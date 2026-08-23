# Stringer SPI 扩展点设计报告

> 版本：v0.1 草稿 · 更新日期：2026-08-23

本文档定义 Stringer 平台的**全部扩展点接口和注解规范**。遵循 **开闭原则**：对扩展开放，对修改关闭。所有扩展点均通过 **Java SPI（Service Provider Interface）** 机制加载，不需要改 Stringer 源码即可插拔。

---

## 一、扩展设计原则

在实现任何一个扩展点之前，必须遵守以下 4 条原则：

1. **约定大于配置** — 默认实现覆盖 80% 场景，用户只需要在 `META-INF/services/com.xxx.Interface` 文件里加一行就能替换。不需要复杂的 YAML 配置。
2. **零侵入** — 扩展接口只依赖 `stringer-core`（纯 Java，不含 Spring / LangChain4j 等第三方库）。扩展实现方不会被迫引入不需要的传递依赖。
3. **失败隔离** — 一个扩展实现抛异常，不影响其他扩展和主流程。所有扩展调用都包 try/catch 并打印 WARN 日志。
4. **可观测** — 每个扩展点执行前后都打 Trace 日志（可通过 `stringer.trace.extensions=true` 开启），便于排查"这个功能走到哪了"。

---

## 二、Starter 层扩展点：注解规范（stringer-core）

这 5 个注解放在 `com.zxc.stringer.core.annotation` 包下，**零 Spring 依赖**，纯 `java.lang.annotation.*`。客户代码用这些注解标记业务方法，Starter 扫描后上报中心平台。

### 2.1 @StringerStep — 标记一个工作流步骤

```java
package com.zxc.stringer.core.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 Stringer 工作流步骤。
 * Spring 启动时由 StringerAnnotationScanner 扫描，自动上报到中心平台。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StringerStep {

    /**
     * 步骤显示名称（前端展示用）。不填则默认为方法名。
     */
    String name() default "";

    /**
     * 步骤功能描述（前端 tooltip 展示，建议不超过 50 字）。
     */
    String description() default "";

    /**
     * 是否需要人工审批才能执行。
     * 等价于在方法上额外加 @RequireApproval。
     */
    boolean requireApproval() default false;

    /**
     * 审批超时时间（秒）。超时未审批视为拒绝。0 = 使用平台全局默认值。
     */
    int approvalTimeoutSec() default 0;

    /**
     * 失败时的重试次数（不含首次执行）。-1 = 用平台默认，0 = 不重试。
     */
    int retryCount() default -1;

    /**
     * 执行超时时间（毫秒）。超时会触发中断。0 = 不限制（不推荐）。
     */
    long timeoutMs() default 300_000L; // 默认 5 分钟

    /**
     * 步骤标签，用于筛选/分组。例如 {"支付", "财务"}
     */
    String[] tags() default {};
}
```

**使用示例**：

```java
@Service
public class PaymentService {

    @StringerStep(
        name = "创建支付订单",
        description = "根据订单号生成支付单并调用第三方支付",
        requireApproval = true,      // 超过 5 万的支付需要财务审批
        approvalTimeoutSec = 3600,   // 1 小时不批就自动拒绝
        retryCount = 2,              // 失败重试 2 次（共执行 3 次）
        timeoutMs = 10_000L,         // 10 秒必须返回
        tags = {"支付", "财务"}
    )
    public PaymentResult createPayment(@StringerInput("业务订单号") String orderNo,
                                       @StringerInput("金额") BigDecimal amount) {
        // 客户原有的业务逻辑一行都不用改
        return thirdPartyPayClient.create(orderNo, amount);
    }
}
```

### 2.2 @StringerInput — 标记步骤输入参数

```java
package com.zxc.stringer.core.annotation;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StringerInput {

    /**
     * 输入参数显示名称（前端展示，必填）。
     */
    String value();

    /**
     * 参数描述（tooltip）。
     */
    String description() default "";

    /**
     * 是否必填。默认 true。如果 false，中心平台允许用户不填走默认值。
     */
    boolean required() default true;

    /**
     * 示例值（前端 placeholder 显示）。
     */
    String example() default "";
}
```

**设计说明**：为什么不用参数名反射？Java 8 以后虽然能通过 `-parameters` 编译参数拿到参数名，但很多老项目没开这个编译参数，扫出来就是 `arg0`、`arg1`。强制用户写 `@StringerInput("中文名")`，前端展示永远清晰。

### 2.3 @StringerOutput — 标记方法返回值的字段别名（可选）

```java
package com.zxc.stringer.core.annotation;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StringerOutput {

    /**
     * 返回值字段显示名。方法级注解用于描述整个返回值含义；
     * 字段级注解（标注在返回类型的字段上）用于描述单个字段。
     */
    String value();
}
```

### 2.4 @StringerOnCompletion — 标记步骤完成回调（可选）

```java
package com.zxc.stringer.core.annotation;

/**
 * 标注在方法上，当所在工作流全部执行成功后自动回调。
 * 常用于"工作流结束后发通知"这类横切逻辑。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StringerOnCompletion {

    /**
     * 绑定到哪个工作流编码。默认 = 所在类的类名。
     */
    String workflowCode() default "";
}
```

### 2.5 @RequireApproval — 已有注解，合并进 stringer-core

你现在的 `com.zxcSpringAI.common.annotation.RequireApproval` 搬到 `com.zxc.stringer.core.annotation.RequireApproval`，和 `@StringerStep(requireApproval = true)` 等价，两种写法都支持。

---

## 三、核心运行时扩展点（stringer-core SPI 接口）

下面所有 SPI 接口都放在 `com.zxc.stringer.core.spi` 包下。**扩展加载机制 = Java 标准 SPI**：

1. 扩展方写一个类实现接口
2. 在自己的 jar 里建文件：`META-INF/services/com.zxc.stringer.core.spi.ToolProvider`
3. 文件里写一行：`com.yourcompany.yourextension.YourToolProvider`
4. Stringer 启动时通过 `ServiceLoader.load(ToolProvider.class)` 自动发现并加载

---

### 扩展点 1：ToolProvider SPI — 注册自定义工具

**扩展目标**：让客户可以用 Python/HTTP/数据库 等任何语言写工具，然后注册到 Stringer 里被 Agent 调用。

**接口定义**（stringer-core 里写）：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.ToolDefinition;
import com.zxc.stringer.core.model.ToolExecutionRequest;
import com.zxc.stringer.core.model.ToolExecutionResult;
import java.util.List;

/**
 * 外部工具提供商 SPI。
 * 每次 Agent 需要调工具时，Stringer 会轮询所有注册的 ToolProvider，
 * 找到第一个能处理该 toolName 的 Provider 并执行。
 */
public interface ToolProvider {

    /**
     * 返回本 Provider 能提供的所有工具定义（工具名、参数签名、描述、授权策略等）。
     * 在 Stringer 启动时调用一次，结果缓存。
     */
    List<ToolDefinition> listDefinitions();

    /**
     * 判断本 Provider 是否能执行指定工具。
     * 默认实现：检查 toolName 是否在 listDefinitions() 返回的定义中。
     */
    default boolean canExecute(String toolName) {
        return listDefinitions().stream().anyMatch(t -> t.getName().equals(toolName));
    }

    /**
     * 执行工具。
     *
     * @param request   工具执行请求（含 toolName、参数 map、上下文 runId）
     * @param context   执行上下文（取消标志位、超时、MDC），扩展方应定期检查 context.isCancelled()
     * @return          工具执行结果（字符串返回 + 元数据）
     * @throws Exception 任何异常都会被 Stringer 捕获，按重试策略处理
     */
    ToolExecutionResult execute(ToolExecutionRequest request, ExecutionContext context) throws Exception;

    /**
     * 优先级。数值越小优先级越高（默认 100）。
     * 同一个工具被多个 Provider 都声明能处理时，选优先级最高的。
     */
    default int priority() { return 100; }
}
```

**实现示例：HTTP 调用外部 Python 工具**

```java
package com.example.extension;

public class HttpToolProvider implements ToolProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<ToolDefinition> listDefinitions() {
        return List.of(
            ToolDefinition.builder()
                .name("python_nlp_analyze")      // 工具名
                .description("调用外部 Python 服务做 NLP 情感分析")
                .parameter("text", "string", true, "要分析的文本")
                .build()
        );
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, ExecutionContext ctx) {
        // 检查取消标志位 —— 用户点了停止就立刻返回，别让 Python 服务白跑
        if (ctx.isCancelled()) {
            return ToolExecutionResult.cancelled();
        }

        String text = (String) request.getArguments().get("text");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "http://python-nlp.internal/analyze",
            Map.of("text", text),
            String.class
        );
        return ToolExecutionResult.success(resp.getBody());
    }
}
```

然后在扩展 jar 的 `META-INF/services/com.zxc.stringer.core.spi.ToolProvider` 里写：
```
com.example.extension.HttpToolProvider
```

---

### 扩展点 2：CheckpointSaver SPI — 工作流断点持久化

**扩展目标**：替换现在的 Redis 实现，支持 JDBC/MySQL/MongoDB/ZooKeeper 等各种持久化介质。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.Checkpoint;
import java.util.List;
import java.util.Optional;

/**
 * 检查点存储 SPI。
 * 工作流中断（人工审批 / 暂停）时，把当前图状态 + 消息栈 + Tool 执行中间结果存起来，
 * 恢复时原样读回继续跑。
 */
public interface CheckpointSaver {

    /**
     * 存储（覆盖写）。同一个 threadId 多次写入以最后一次为准。
     *
     * @param threadId   会话/工作流实例唯一 ID（等于 wf_run.id）
     * @param checkpoint 序列化后的检查点对象
     */
    void save(String threadId, Checkpoint checkpoint);

    /**
     * 追加写入（不覆盖，保留历史版本用于回滚到 N 步前）。
     * 默认实现 = 复用 save 覆盖；需要版本回滚能力的扩展方重写。
     */
    default void append(String threadId, Checkpoint checkpoint) {
        save(threadId, checkpoint);
    }

    /**
     * 读取最新一次检查点。
     */
    Optional<Checkpoint> loadLatest(String threadId);

    /**
     * 读取全部历史检查点（按写入顺序正序），用于步骤回滚面板。
     */
    default List<Checkpoint> loadAll(String threadId) {
        return loadLatest(threadId).map(List::of).orElse(List.of());
    }

    /**
     * 删除 threadId 下全部检查点（工作流正常完成 / 用户停止时调用）。
     */
    void release(String threadId);
}
```

**默认实现对比**：

| 实现类 | 介质 | 优点 | 缺点 | 适用场景 |
|---|---|---|---|---|
| RedisCheckpointSaver（现有） | Redis String | 读写快，天然 TTL 过期 | 内存存储不能无限存历史版本 | 生产环境首选（99% 场景） |
| JdbcCheckpointSaver（可扩展） | MySQL/PG BLOB 列 | 持久化，可存历史回滚版本 | 读写比 Redis 慢 5-10 倍 | 监管要求审计留痕的场景 |
| MongoCheckpointSaver（可扩展） | MongoDB Document | 天然支持 JSON，不用序列化 | 引入 MongoDB 依赖 | 已经在用 Mongo 的团队 |

---

### 扩展点 3：ChatMemoryStore SPI — 会话记忆存储

**扩展目标**：替换对话记忆的存储后端。

**接口定义**（基本照搬 LangChain4j 的 `ChatMemoryStore`，但剥离 LangChain4j 依赖，纯 Stringer POJO）：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.ChatMessage;
import java.util.List;

/**
 * 会话级聊天记忆存储。
 * 按 chatId（sessionId）隔离，每次 add 后自动应用双约束（条数 + tokens）淘汰。
 */
public interface ChatMemoryStore {

    /**
     * 获取 chatId 下所有消息（按写入顺序正序）。
     */
    List<ChatMessage> getMessages(Object chatId);

    /**
     * 全量覆盖写入。一般用于恢复到某个快照。
     */
    void setMessages(Object chatId, List<ChatMessage> messages);

    /**
     * 追加一条消息到末尾。
     */
    default void addMessage(Object chatId, ChatMessage message) {
        List<ChatMessage> existing = new java.util.ArrayList<>(getMessages(chatId));
        existing.add(message);
        setMessages(chatId, existing);
    }

    /**
     * 移除最后一条（用户停止任务时回滚，避免"用户提问"留在上下文里）。
     */
    default void removeLastMessage(Object chatId) {
        List<ChatMessage> existing = new java.util.ArrayList<>(getMessages(chatId));
        if (!existing.isEmpty()) {
            existing.remove(existing.size() - 1);
            setMessages(chatId, existing);
        }
    }

    /**
     * 删除全部（会话永久结束 / 用户登出）。
     */
    void deleteMessages(Object chatId);
}
```

---

### 扩展点 4：ContentRetriever SPI — 自定义 RAG 检索策略

**扩展目标**：替换现在的 ES 混合检索，支持 Milvus/pgvector/本地 FAISS 等向量库，或接入 BM25-only / 纯向量 / 重排模型等策略。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.RetrievedDocument;
import java.util.List;

/**
 * 内容检索 SPI — 给定用户问题，返回最相关的 Top N 文档片段。
 * 由 RagService（capability 层）在需要知识库检索时调用。
 */
public interface ContentRetriever {

    /**
     * 本 Retriever 的唯一名称（用于检索融合时区分来源，例如 "es_vector" / "milvus" / "bm25_keyword"）。
     */
    String name();

    /**
     * 执行检索。
     *
     * @param query  用户问题（已经过 InputSanitizer 清洗）
     * @param topN   期望返回的最大结果数
     * @return       检索到的文档列表（不需要刚好 topN 条，可以更少）
     */
    List<RetrievedDocument> retrieve(String query, int topN);

    /**
     * 本 Retriever 在混合检索中的权重（默认 1.0）。
     * 多个 Retriever 组合时，CompositeContentRetriever 按权重加权融合分数。
     */
    default double weight() { return 1.0; }
}
```

**你现在已经有的两个实现**（将来直接包装成 SPI 即可）：
- `NativeScriptScoreContentRetriever` → name = "es_vector"，weight = 0.6
- `KeywordMatchContentRetriever` → name = "es_keyword"，weight = 0.4
- `CompositeContentRetriever` 本身不是 SPI，是内置组合器：调用所有已注册的 ContentRetriever，SHA-256 去重后按权重分数融合

---

### 扩展点 5：ToolExecutionInterceptor SPI — 工具调用拦截器（AOP）

**扩展目标**：给所有工具调用加统一横切逻辑，不用改每个工具实现。比如审计日志、参数脱敏、速率限流、故障注入等。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.ToolExecutionRequest;
import com.zxc.stringer.core.model.ToolExecutionResult;

/**
 * 工具执行拦截器 SPI — 类似 Spring MethodInterceptor，但作用范围是所有 @StringerStep / @Tool 方法。
 * 多个拦截器按 priority() 顺序组成责任链。
 */
public interface ToolExecutionInterceptor {

    /**
     * 工具执行前调用。可以修改 request 参数、做限流、抛异常终止执行等。
     *
     * @return 返回修改后的 request；一般返回入参本身，不要 null。
     */
    default ToolExecutionRequest beforeExecute(ToolExecutionRequest request) {
        return request;
    }

    /**
     * 工具正常执行成功后调用。可以修改 result、做脱敏、打成功审计日志。
     */
    default ToolExecutionResult afterSuccess(ToolExecutionRequest request, ToolExecutionResult result) {
        return result;
    }

    /**
     * 工具抛出异常后调用。可以吞掉异常返回降级结果、打失败告警日志。
     *
     * @return  如果能恢复，返回降级后的 Result；如果不能恢复，重新抛出原异常或包装异常。
     */
    default ToolExecutionResult afterFailure(ToolExecutionRequest request, Exception ex) throws Exception {
        throw ex;
    }

    /**
     * 优先级。数值越小越先执行（beforeExecute 顺序），afterSuccess/afterFailure 是逆序。
     */
    default int priority() { return 100; }
}
```

**典型应用场景**：

| 拦截器类 | 作用 | 优先级 |
|---|---|---|
| `RateLimitInterceptor` | 按工具名 + appName 限流（令牌桶），超限抛 `TooManyRequestsException` | 10（最前面，先限流） |
| `AuditLogInterceptor` | 所有工具调用前/后写 DB 审计日志（异步） | 50 |
| `SensitiveFieldInterceptor` | 返回结果里的手机号/身份证号脱敏打 * 号 | 200（成功返回前最后一步） |
| `ChaosMonkeyInterceptor` | 测试环境按 10% 概率注入超时/异常，验证故障恢复链路 | 999（只在测试环境启用） |

---

### 扩展点 6：Scheduler SPI — 多实例调度策略（Phase 5）

**扩展目标**：多实例部署时，决定哪个实例执行哪个任务。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.WorkflowRun;
import java.util.List;

/**
 * 多实例调度策略 SPI。
 * 每次中心平台从队列里取到待执行任务时，调用 pickWorker() 决定由哪个实例执行。
 */
public interface Scheduler {

    /**
     * 策略名（用于配置，例如 "round_robin" / "consistent_hash" / "priority_preempt"）。
     */
    String name();

    /**
     * 选择一个 worker（实例 ID）来执行 run。
     *
     * @param run         待执行的工作流
     * @param aliveWorkerIds 当前存活的实例 ID 列表（心跳上报的，一般已排序）
     * @return            选中的实例 ID（必须在 aliveWorkerIds 中），返回 null 表示先不执行回队列
     */
    String pickWorker(WorkflowRun run, List<String> aliveWorkerIds);
}
```

**默认策略 = 轮询 + 简单随机**（已覆盖大多数场景）。复杂场景可实现：
- 一致性哈希：同一个 `appName` 的任务固定到同一个实例，利于本地缓存
- 优先级抢占：高标签任务分配到配置更高的实例
- 位置感知：同一个机房的任务优先分配给同机房实例

---

### 扩展点 7：Approver SPI — 人工审批对接企业 OA（Phase 3+）

**扩展目标**：中心平台的审批消息不只展示在前端，而是自动推送到企业现有审批系统（飞书审批、钉钉 OA、BPM 等）。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.ApprovalRequest;

/**
 * 审批通知 SPI — 中心平台产生审批待办时调用。
 * 用户审批后通过回调 URL 通知中心平台。
 */
public interface Approver {

    /**
     * 推送审批待办。
     *
     * @param request 审批请求（含运行号、步骤名、待审批人、审批链接、输入 JSON）
     * @return        外部审批系统的单据号，用于后续关联查询
     */
    String submitApproval(ApprovalRequest request) throws Exception;

    /**
     * 撤销审批待办（用户停止任务、超时自动拒绝等场景调用）。
     * 外部审批系统里的单据应自动关闭，避免待办还挂着让人误点。
     */
    default void cancelApproval(String externalApprovalId) throws Exception {
        // 默认实现 = 什么都不做（大多数外部审批系统不支持撤销，但仍建议重写）
    }

    /**
     * 审批人别名解析器（可选）。
     * Stringer 里存的审批人是员工工号，外部系统可能要映射成 open_id / 邮箱，重写此方法转换。
     */
    default String resolveApproverId(String internalApproverId) {
        return internalApproverId;
    }
}
```

---

### 扩展点 8：Notifier SPI — 任务状态通知（Phase 5）

**扩展目标**：工作流失败 / 成功 / 进入死信等关键事件，推送到钉钉/飞书/邮件等。

**接口定义**：

```java
package com.zxc.stringer.core.spi;

import com.zxc.stringer.core.model.NotificationEvent;

/**
 * 事件通知 SPI。中心平台关键事件广播后，所有 Notifier 都会被调用一次（异步不阻塞主流程）。
 * 一个 Notifier 一般对应一个通道：飞书群机器人 / 钉钉 webhook / 邮件 / Prometheus metrics 等。
 */
public interface Notifier {

    /**
     * 通道名。日志里区分是哪个通知通道打出来的。
     */
    String channel();

    /**
     * 是否关注该事件。默认所有事件都关注，实现方可以按需过滤（例如只关心 FAILED 事件）。
     */
    default boolean accepts(NotificationEvent.EventType type) { return true; }

    /**
     * 发送通知。所有异常会被 Stringer 吞掉并打 WARN 日志（通知失败不能影响工作流）。
     */
    void notify(NotificationEvent event) throws Exception;
}
```

---

### 扩展点 9：ModelProvider SPI — 多模型切换（可选）

**扩展目标**：切换 LLM / Embedding 供应商，不依赖 Spring AI / LangChain4j 的更新节奏。属于可选项，因为 LangChain4j 已经支持绝大多数模型。当某模型厂商新出 SDK，LangChain4j 还没适配时，让客户自己写一个 ModelProvider 就能先接上。

接口定义略，Phase 4/5 再细化。核心是两个方法：`ChatResponse chat(ChatRequest req)` 和 `EmbeddingResponse embed(EmbeddingRequest req)`。

---

### 扩展点 10：ApprovalStrategy SPI — 授权策略（细粒度）

现在 `@RequireApproval` 是"方法级要不要审批"的粗粒度开关。实际业务中经常要"金额 > 5 万才审批，否则直接过"。这个扩展点允许写自定义判定逻辑：

```java
public interface ApprovalStrategy {
    boolean requiresApproval(ToolExecutionRequest request);
}
```

优先级高于 `@StringerStep(requireApproval = true)`。实现方可以根据参数金额、申请人部门、当前星期几等任意条件判断。

---

## 四、扩展点总览表

| 序号 | SPI 接口名 | 所属模块 | 加载时机 | 已有默认实现 | 替换优先级 |
|---|---|---|---|---|---|
| 1 | ToolProvider | stringer-core | 启动时扫描注册 | LocalToolWrappers（本地方法） | ★★★ 高 |
| 2 | CheckpointSaver | stringer-core | 启动时注入 | RedisCheckpointSaver | ★★ 中 |
| 3 | ChatMemoryStore | stringer-core | 启动时注入 | RedisChatMemoryStore | ★★ 中 |
| 4 | ContentRetriever | stringer-core | 启动时注册 + 检索时聚合 | ES向量 + ES关键词 + 融合器 | ★★★ 高 |
| 5 | ToolExecutionInterceptor | stringer-core | 工具调用责任链 | 空（限流/审计等都留给用户实现） | ★★ 中 |
| 6 | Scheduler | stringer-server | Phase 5 多实例部署 | 轮询 | ★ 低（Phase 5 才需要） |
| 7 | Approver | stringer-server | Phase 3 审批流 | 仅前端待办页 | ★★ 中 |
| 8 | Notifier | stringer-server | Phase 5 可靠性增强 | 仅控制台日志 | ★★ 中 |
| 9 | ModelProvider | stringer-core | 可选 | LangChain4j 内置适配 | ★ 低（LangChain4j 够用） |
| 10 | ApprovalStrategy | stringer-core | 每个工具执行前判断 | 注解级开关 | ★★ 中 |

---

## 五、Starter 配置项（StringerProperties 全字段）

客户在 `application.yaml` 中能配置的全部参数，写在这里作为最终规格：

```yaml
stringer:
  # ======= 基础连接 =======
  server-url: http://stringer-center.internal:8080   # 中心平台地址，必填
  api-key: sk-xxxxxxxx                                # 认证密钥，生产环境用环境变量注入
  app-name: ${spring.application.name}                # 项目应用名（区分不同客户），默认取 spring.application.name

  # ======= 上报策略 =======
  report:
    enabled: true                                     # 是否启用自动上报（开发阶段可设 false 只跑本地）
    retry-max: 3                                      # 上报失败重试次数
    retry-backoff-ms: 1000                            # 重试间隔（指数退避基数）
    buffer-size: 1000                                 # 本地缓冲队列大小（网络不通时暂存）
    batch-size: 10                                    # 批量上报每批条数

  # ======= 执行超时 =======
  execution:
    default-step-timeout-ms: 300000                   # 单步骤默认超时 5 分钟
    default-global-timeout-ms: 3600000                # 整个工作流默认超时 1 小时
    default-retry-count: 2                            # 步骤失败默认重试 2 次（共 3 次）

  # ======= 审批策略 =======
  approval:
    default-timeout-sec: 1800                         # 默认审批超时 30 分钟，超时视为拒绝
    auto-reject-on-timeout: true                      # 超时是否自动拒绝

  # ======= 调试与观测 =======
  trace:
    extensions: false                                 # 是否打印扩展点调用前后 Trace（排查问题时开）
    tool-params: false                                # 是否打印工具调用的完整参数（生产勿开，可能泄露敏感数据）
  actuator:
    expose-diagnostics: true                          # 是否暴露 /actuator/stringer 诊断端点
```

---

## 六、版本兼容性约定

1. **SPI 接口永不做破坏性变更** — 新增方法必须加 `default` 实现，老版本扩展实现方不需要改代码就能在新版本 Stringer 上跑。
2. **注解新增参数必须有默认值** — 比如以后在 `@StringerStep` 加 `boolean async() default false`，老代码继续按默认值工作。
3. **配置项向后兼容** — 新配置项一律给默认值，旧 application.yaml 不用改也能启动新版本 Starter。
4. **Stringer Server 版本 X 必须兼容 Starter 版本 X-1** — 中心平台升级，客户项目不用立即跟着升级 Starter。
