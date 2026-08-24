<h1 align="center">Stringer</h1>

<p align="center">
  <em>Java-based AI Workflow Orchestration Starter</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.8.0--SNAPSHOT-blue?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/license-MIT-yellow?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white" alt="java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="spring-boot">
  <img src="https://img.shields.io/badge/LangChain4j-1.18.1-7B68EE?style=flat-square" alt="langchain4j">
  <img src="https://img.shields.io/badge/LangGraph4j-1.8.17-008080?style=flat-square" alt="langgraph4j">
  <img src="https://img.shields.io/badge/Elasticsearch-9.4.4-005571?style=flat-square&logo=elasticsearch&logoColor=white" alt="elasticsearch">
  <img src="https://img.shields.io/badge/Redis-7+-DC382D?style=flat-square&logo=redis&logoColor=white" alt="redis">
</p>

<p align="center">
  <code>pom.xml</code> 加一行依赖 + 代码里打 <code>@StringerStep</code> 注解，业务步骤自动注册到中心平台 —— 不用打开浏览器画流程图。
</p>

---

基于 **Spring Boot 3.5 + LangChain4j 1.18 + LangGraph4j** 构建。仓库已按 **Maven 多模块** 拆分为 9 个模块：`stringer-core`（纯 Java 契约）、`stringer-common`（公共支撑）、`stringer-retrieval`（混合检索）、`stringer-infrastructure`（基础设施）、`stringer-capability`（业务能力）、`stringer-agent`（Agent 运行时）、`stringer-server`（可运行服务）、`stringer-spring-boot-starter`（客户依赖入口，骨架）、`stringer-example`（示例，骨架）。

---

## 为什么不是 Dify / Flowise / Langflow？

三家竞品全是 **GUI 优先**（先拖节点再导出 API），适合原型和非技术用户。但在 **Java 企业级客户场景** 里存在天然盲区：

| 维度 | Dify (Python) | Flowise (Node.js) | Langflow (Python) | Stringer (Java / 本项目) |
|---|---|---|---|---|
| 后端语言 | Python 3.12 + Flask | TypeScript + Node.js | Python 3.12 + FastAPI | Java 21 + Spring Boot 3 |
| 接入方式 | HTTP API + 前端画布 | HTTP API + 前端画布 | HTTP API + 前端画布 | **Spring Boot Starter + 注解** |
| 客户改造量 | 起新服务 + 写适配层 | 起新服务 + 写适配层 | 起新服务 + 写适配层 | **pom 加依赖 + 打注解** |
| Java 企业契合 | ★★ 需要额外对接 | ★ 需要额外对接 | ★★ 需要额外对接 | **★★★★★ 原生生态** |
| 取消信号/并发 | gevent 猴子补丁 | Node 事件循环 | ASGI 协程 | **JVM 虚拟线程 + Flux onCancel** |
| 包分层策略 | 大单体 Flask 应用 | PNPM Monorepo | UV Monorepo | **多模块 + 执行链路分包** |

**Stringer 的核心差异化**：中心平台 + 客户项目里的 Starter，IDE 里写代码就完成接入，不强制改变企业研发流程。

---

## 📚 相关文档

| 文档 | 位置 | 状态 |
|---|---|---|
| SDK 使用文档（注解 + Starter 配置） | `docs/SDK-GUIDE.md` | 📝 后期补充 |
| API 接口文档（中心平台 REST API） | `docs/API-REFERENCE.md` | 📝 后期补充 |
| 实施路线图（Phase-Gate） | `docs/STRINGERPLAN.md` | ✅ 已有 |
| SPI 扩展点设计 | `docs/EXTENSIONPOINTS.md` | ✅ 已有 |
| 技术设计文档 | `stringer-server/src/main/resources/technical-design.md` | ✅ 已有 |
| 快速开始 | 👇 见下文 | ✅ 已有 |

---

## 核心特性（已实现）

**LangGraph4j 图编排状态机** —— 不是简单的 AiServices 隐式 ReAct 循环，而是用显式状态图控制每一步：`agent → 条件路由 → tools/review → agent` 循环，节点间状态透明可查，中断可恢复。

**人工审批（HITL）** —— 给敏感工具标注 `@RequireApproval`，Agent 调用时自动中断，等待用户确认后才执行。中断状态落 Redis，支持跨实例恢复。

**混合检索 + 分数融合** —— 向量检索（Top15）与 BM25 关键词检索（Top5）并行执行，min-max 归一化后加权融合，兼顾语义理解和精确匹配。

**双约束会话记忆** —— 同时卡住消息条数（100 条）和 Token 估算（30K），哪个先触顶就裁剪哪个，记忆落 Redis 按会话隔离。

**流式输出 + 任务停止** —— `Flux<String>` 逐块推送，首字延迟百毫秒级；用户可随时中断正在执行的 Agent 任务，停止时回滚记忆。

**输入安全防护** —— 五维正则检测提示词注入攻击（指令覆盖、角色混淆、分隔符注入、提示词窃取、编码绕过），命中即拦截。

## 图编排链路

Agent 的核心是一条 LangGraph4j 状态图，三个节点 + 三路条件边：

```
START → agent 节点 → 条件路由（三路分流）
                     ├─ 无工具调用 → END
                     ├─ 全自主工具 → tools 节点 → agent（循环）
                     └─ 含授权工具 → review 节点（interruptBefore 暂停）
                                         └─ 用户 approve → tools 节点 → agent
```

**路由逻辑**：LLM 生成的 `AiMessage` 如果没有工具调用 → 直接结束；如果全是自主工具 → 走 tools 直连不中断；如果包含 `@RequireApproval` 标注的工具 → 走 review 中断，等用户确认。

**中断与恢复**：review 节点前通过 `interruptBefore` 暂停图执行，检查点（含完整消息状态）落 Redis。前端收到中断事件弹出确认框，用户批准后调用 resume 接口继续执行；拒绝则注入拒绝反馈让 LLM 重新决策。

## 多模块结构（Maven 9 模块）

按 **标准三方件流程** 拆分：模块 = 发布/复用边界，包 = 执行链路边界。依赖方向严格单向、无环：

```
core ← common ← {retrieval, infrastructure} ← agent ← server
                              capability ← agent
```

```
springAI-rag/                                   根 pom（packaging=pom + dependencyManagement）
├── stringer-core/            com.zxc.stringer.core
│   ├── annotation/           @RequireApproval（未来 @StringerStep 系列）
│   └── spi/                  5 个扩展点接口（ToolProvider/CheckpointSaver/ChatMemoryStore/ContentRetriever/ToolExecutionInterceptor）
├── stringer-common/          com.zxc.stringer.common
│   ├── exception/            BaseException / ChatMemoryException / KnowledgeBaseException
│   └── util/                 InputSanitizer / TokenUsageTracker
├── stringer-retrieval/       com.zxc.stringer.retrieval       混合检索（ES 向量 script_score + BM25 + 融合）
├── stringer-infrastructure/  com.zxc.stringer.infrastructure
│   ├── memory/               双约束会话记忆（DualConstraintChatMemory / RedisChatMemoryStore）
│   ├── checkpoint/           RedisCheckpointSaver（interrupt/resume 断点）
│   └── ingestion/            文档导入（DocumentIngestor + 策略分片 + 中文 splitter）
├── stringer-capability/      com.zxc.stringer.capability     业务能力（RagService / WeatherService）
├── stringer-agent/           com.zxc.stringer.agent
│   ├── web/                  TestController / GlobalExceptionHandler
│   ├── orchestration/        AgentOrchestrationService（图编排调度中枢）
│   ├── tools/                ToolRouter / LocalToolWrappers
│   └── cancellation/         CancellationTracker（任务停止）
├── stringer-server/          com.zxc.stringer.server
│   ├── config/               全部 @Configuration / @ConfigurationProperties（装配层）
│   ├── SpringRagApplication  启动类
│   └── resources/            application.yaml / ragDatabase / test.html / 设计文档
├── stringer-spring-boot-starter/   骨架（依赖 core，Phase 2 后段填自动装配）
└── stringer-example/                骨架（依赖 starter，示例项目）
```

**执行链路 → 包 映射**（在 stringer-agent / infrastructure / retrieval 内）：

| 链路环节 | 模块 | 包 |
|---|---|---|
| 接口层 | stringer-agent | `web` |
| 图编排 | stringer-agent | `orchestration` |
| 工具体系 | stringer-agent | `tools` |
| 任务停止 | stringer-agent | `cancellation` |
| 业务能力 | stringer-capability | `service` |
| 混合检索 | stringer-retrieval | `retrieval` |
| 会话记忆 | stringer-infrastructure | `memory` |
| 检查点 | stringer-infrastructure | `checkpoint` |
| 文档导入 | stringer-infrastructure | `ingestion` |
| 配置装配 | stringer-server | `config` |
| 公共支撑 | stringer-common | `exception` / `util` |
| 核心契约 | stringer-core | `annotation` / `spi` |

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.7 |
| AI 框架 | LangChain4j | 1.18.1 |
| 图编排 | LangGraph4j | 1.8.17 |
| LLM | 通义千问 qwen3.7-plus（阿里百炼） | - |
| Embedding | text-embedding-v2（1536 维） | - |
| 向量存储 | Elasticsearch（IK 分词器） | 9.4.4 |
| 会话记忆 / 检查点 | Redis | - |
| JDK | OpenJDK | 21 |

## 快速开始（当前 9 模块）

### 环境要求

- JDK 21+
- Maven 3.8+
- Elasticsearch 9.4.4（需安装 IK 分词器插件）
- Redis 6+
- 阿里百炼 API Key（开通 qwen3.7-plus 和 text-embedding-v2 模型权限）

### 配置启动

1. 克隆仓库

```bash
git clone https://gitee.com/your-username/springAI-Langchain-Agent.git
cd springAI-Langchain-Agent
```

2. 配置 `stringer-server/src/main/resources/application.yaml`，填入中间件地址和 API Key：

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: "https://dashscope.aliyuncs.com/compatible-mode/v1"
      api-key: 你的百炼API-Key
      model-name: "qwen3.7-plus"
    streaming-chat-model:
      base-url: "https://dashscope.aliyuncs.com/compatible-mode/v1"
      api-key: 你的百炼API-Key
      model-name: "qwen3.7-plus"
    embedding-model:
      base-url: "https://dashscope.aliyuncs.com/compatible-mode/v1"
      api-key: 你的百炼API-Key
      model-name: "text-embedding-v2"

spring:
  elasticsearch:
    host: 你的ES地址
    port: 9200
  data:
    redis:
      host: 你的Redis地址
      port: 6379
      password: 你的Redis密码
```

3. 把知识库文档放进 `stringer-server/src/main/resources/ragDatabase/`（支持 .txt / .md / .markdown / .pdf）

4. 首次启动时把 `rag.elasticsearch.delete-on-startup` 设为 `true` 重建索引，后续改回 `false`

5. 编译启动（根目录聚合构建，模块按依赖顺序自动编译）

```bash
mvn clean install          # 根目录：编译并安装全部 9 个模块
mvn -pl stringer-server spring-boot:run    # 启动可运行服务
```

### 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test/agent/{sessionId}/{message}` | Agent 对话入口，流式返回 |
| GET | `/test/agent/resume/{sessionId}?approved=true` | 恢复中断会话（人工授权） |
| GET | `/test/agent/resume/{sessionId}?approved=false` | 恢复中断会话（人工拒绝） |
| POST | `/test/agent/stop/{sessionId}` | 停止正在执行的任务 |

项目自带测试页面 `stringer-server/src/main/resources/test.html`，浏览器直接打开即可体验对话、工具授权确认、任务停止等完整流程。

系统提示词在 `stringer-server/src/main/resources/application.yaml` 的 `ai.prompt.system-message` 中配置，改完重启生效。

## 核心逻辑（代码说明）

### Agent 编排服务

`AgentOrchestrationService`（`stringer-agent` 的 `orchestration` 包）是整个框架的调度中枢，持有编译好的 `CompiledGraph` 并管理三个核心流程：

- **orchestrate**（新对话）：加载会话记忆 → 拼装 `[SystemMessage] + 历史消息` → 启动图执行 → 流式推送 LLM 输出 → 检测 review 中断推送事件 → 正常完成时提取最终 AiMessage 存入 memory
- **resume**（恢复中断）：从 Redis checkpoint 恢复图状态 → 拒绝时注入 `ToolExecutionResultMessage` 反馈 → 继续执行 → 存最终回复到 memory → 清 checkpoint
- **handleStop**（用户停止）：回滚 memory 中的 UserMessage → 清 checkpoint → 推送停止事件

agent 节点通过 `StreamingChatModel.chat()` 流式生成，每个 chunk 实时推送到 `FluxSink`；每个工具调用前检查停止标志位。

### 工具体系

工具就是普通的 `@Tool` 方法，Spring `@Component` 自动扫描注册。`ToolRouter`（`stringer-agent.tools`）在构造时反射扫描 `LocalToolWrappers` 注册所有工具，并检查 `@RequireApproval`（`stringer-core.annotation`）标注，将需授权工具名收集到集合中供条件路由判断。

### 混合检索

`CompositeContentRetriever`（`stringer-retrieval`）组合两路检索器并行执行：

- **向量检索**（`NativeScriptScoreContentRetriever`）：ES `script_score` 余弦相似度，Top15，minScore=0.2
- **关键词检索**（`KeywordMatchContentRetriever`）：ES BM25 match，Top5

两路结果按 min-max 归一化后以 0.6/0.4 加权融合，再对标题命中（+0.15）、文件名命中（+0.1）加 boost 重排序，最终取 Top5 返回。

### 会话记忆

`DualConstraintChatMemory`（`stringer-infrastructure.memory`）实现 LangChain4j 的 `ChatMemory` 接口，同时约束消息条数（100 条）和 Token 估算（30K）。每次 `add` 时从最旧消息开始淘汰，直到两个约束都满足。

底层 `RedisChatMemoryStore` 用 `StringRedisTemplate` 存储，key 为 `chat:memory:{sessionId}`。`removeLastMessage()` 方法用于任务停止时回滚 UserMessage。

### 检查点持久化

`RedisCheckpointSaver`（`stringer-infrastructure.checkpoint`）实现 LangGraph4j 的 `BaseCheckpointSaver` 接口，通过共享的 `graphStateSerializer`（`stringer-server.config.MemoryConfig`）序列化 checkpoint 后 Base64 落 Redis。注意：`ChatMessage`/`ToolExecutionRequest` 未实现 `Serializable`，必须经由注册了自定义序列化器的 `StateSerializer` 处理，不能用 Java 原生序列化。

### 输入安全

`InputSanitizer`（`stringer-common.util`）在请求进入模型之前用 30+ 正则扫描用户输入，覆盖五类提示词注入攻击。命中即抛异常拦截，同时清洗零宽字符、压缩换行、截断超长输入。

## 未来路线图

这个项目的终极目标是做一个 **Java 生态的 AI 工作流三方件**：

```
客户 Spring Boot 项目
     │ pom.xml 引入 stringer-spring-boot-starter
     │ 代码里加 @StringerStep / @StringerInput 注解
     ▼
项目启动时自动上报步骤定义 → Stringer 中心平台
                                      │
                                      ▼
                              可视化流程观测
                              人工审批 @RequireApproval
                              断点续跑 Checkpoint
                              多实例分布式调度
                              版本回滚 / 任务历史回放
```

- **Phase 1**：代码基线收敛（✅ 已完成：旧包清理、四层依赖单向化）
- **Phase 2**：Maven 多模块拆分 + Starter 第一版（🔄 进行中：9 模块已拆分，starter/example 骨架待填充自动装配）
- **Phase 3**：Stringer 中心平台后端（API + 调度引擎 + 审批流 + 检查点）
- **Phase 4**：中心平台前端（运行观测 + 人工审批页 + 流程图展示）
- **Phase 5**：可靠性增强（分布式调度抢占 + 重试 + 死信队列 + 任务确认）

## License

MIT License
