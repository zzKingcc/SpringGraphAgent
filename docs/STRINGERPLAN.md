# Stringer 实施路线图

> 版本：v0.1 草稿 · 更新日期：2026-08-23

本文档是 Stringer 三方件平台的**分阶段实施计划**。采用 **Phase-Gate（阶段-门禁）** 模式：每个 Phase 必须完成全部验收标准才能进入下一阶段，避免半成品堆到后面一起爆炸。

---

## 总体里程碑

```
现在 ──────────────────────────────────────────────────────────── 生产就绪
 Phase 1       Phase 2         Phase 3         Phase 4       Phase 5
代码基线     Starter包       中心平台后端    中心平台前端   可靠性增强
 2周          3周             4周             3周            3周
───────────────────────────────────────────────────────────────────────
                   总工期预估：约 15 周（不含返工）
```

---

## Phase 1：代码基线收敛（✅ 已完成）

**目标**：把现在的单模块 Spring Boot 项目清理干净，为拆多模块打好基础。不做新功能，只做"去重 + 统一 + 验证"。

> 注：下表"新位置"为 Phase 1 四层包（`agent/capability/common/infrastructure`）；Phase 2 已在四层之上进一步拆为 9 个 Maven 模块，包名统一为 `com.zxc.stringer.*`（见下方 Phase 2 目录结构）。

| 项目 | 内容 |
|---|---|
| 工期 | ~ 2 周 |
| 依赖 | 无（当前代码即可开始） |
| 核心动作 | 包分层统一 → 重复类清理 → 编译通过 → 静态检查 |

### 交付物清单

1. **重复包清理**
   - 删除（或标记 `@Deprecated`）以下旧包（功能已在新包实现）：
     - `com.zxcSpringAI.aiService.*` → 新位置 `agent.service.*`
     - `com.zxcSpringAI.config.*` → 新位置 `infrastructure.config.*`
     - `com.zxcSpringAI.exception.*` → 新位置 `common.exception.*`
     - `com.zxcSpringAI.memory.*` → 新位置 `infrastructure.memory.*`
     - `com.zxcSpringAI.processor.*` → 新位置 `infrastructure.processor.*`
     - `com.zxcSpringAI.retriever.*` → 新位置 `infrastructure.retriever.*`
     - `com.zxcSpringAI.splitter.*` → 新位置 `infrastructure.splitter.*`
     - `com.zxcSpringAI.tools.*` → 已拆分到 `capability.service.*` + `agent.tool.*`
     - `com.zxcSpringAI.util.*` → 新位置 `common.util.*` + `infrastructure.util.*`
     - `com.zxcSpringAI.model.*` → 新位置 `infrastructure.config.*`（`@ConfigurationProperties` 类）
   - 所有引用同步更新，确保删除后 IDE 没有红线

2. **包分层校验文档**
   - 明确四层依赖方向：`agent → capability → common`；`agent → infrastructure`；`capability → common`；**禁止反向依赖**（common 不能 import agent）
   - 写一个简单的架构测试（ArchUnit 或手写）：校验 `common` 包下的类没有 import 其他三层的类

3. **编译验证报告**
   - `mvn clean compile` 零警告通过（`-Xlint:all`）
   - 测试页面 `test.html` 全流程跑通：对话 → 工具授权确认 → 任务停止

### 风险

| 风险 | 影响 | 规避方案 |
|---|---|---|
| 删除旧包时漏改引用导致编译失败 | 中 | 不用硬删，先把旧类全部加 `@Deprecated(since = "phase1", forRemoval = true)` 注释并指向新类，Phase 2 拆模块时再物理删除 |
| 现在 Maven 不可用（环境问题） | 中 | 静态代码审查 + Grep 查 import 路径，确保引用全部指向新包；等 IDE 里能编译时再最终验证 |

### 验收标准（全部满足才能进 Phase 2）

- [ ] 所有旧包内的类均已 `@Deprecated` 并标注替代类
- [ ] `AgentOrchestrationService` 只 import `agent.*`、`common.*`、`capability.*`、`infrastructure.*` 四层（不 import 旧包）
- [ ] Grep 全项目：没有任何 `import com.zxcSpringAI.aiService`、没有 `import com.zxcSpringAI.config` 等旧包引用
- [ ] IDE（IDEA）里 "Rebuild Project" 零错误通过

---

## Phase 2：Maven 多模块拆分 + Starter 第一版

**目标**：从单模块拆成 9 个子模块（已拆分完成），发布客户能用的 `stringer-spring-boot-starter` 第一版。

| 项目 | 内容 |
|---|---|
| 工期 | ~ 3 周 |
| 依赖 | Phase 1 验收通过（✅ 已完成） |
| 核心动作 | 根 pom 改造 → 9 个子模块 pom → 代码搬迁 → **Starter 注解 + 自动装配（进行中）** |

### 交付物清单

1. **多模块目录结构**（✅ 已落地为 9 模块，包名统一 `com.zxc.stringer.*`）

```
springAI-rag\
├── pom.xml                         ← 根 pom（packaging=pom，继承 spring-boot-starter-parent + dependencyManagement）
├── stringer-core\                  ← 纯 Java 契约（仅依赖 langchain4j-core，零 Spring）
│   └── src\main\java\com\zxc\stringer\core\
│       ├── annotation\             @RequireApproval（未来 @StringerStep 系列）
│       └── spi\                    ToolProvider / CheckpointSaver / ChatMemoryStore / ContentRetriever / ToolExecutionInterceptor
├── stringer-common\                ← 公共支撑：exception + util（InputSanitizer/TokenUsageTracker）
├── stringer-retrieval\             ← 混合检索（ES 向量 script_score + BM25 + 分数融合）
├── stringer-infrastructure\        ← memory / checkpoint / ingestion（记忆、断点、文档导入）
├── stringer-capability\            ← 业务能力（RagService / WeatherService）
├── stringer-agent\                 ← web / orchestration / tools / cancellation（Agent 运行时闭环）
├── stringer-server\                ← 中心平台/可运行服务：config 装配 + 启动类 + resources
├── stringer-spring-boot-starter\   ← ★ 客户引入的 Starter（骨架，待填自动装配）
└── stringer-example\               ← 示例项目（骨架）
```

2. **根 pom（核心要点）**（✅ 已实现）
   - `<packaging>pom</packaging>`，继承 `spring-boot-starter-parent`（版本/插件由父 POM 统一），`<modules>` 声明 9 个子模块
   - `dependencyManagement` 统一管理：`langchain4j-bom`、`langgraph4j-bom`、ES 客户端版本锁定（9.4.4，与服务器一致）、9 个内部模块版本
   - 构建顺序：core → common → retrieval → infrastructure → capability → agent → server → starter → example（前序 install 成功才能 build 后序）

3. **Starter 最小可用功能**（🔄 进行中：模块骨架已建，`StringerAutoConfiguration`/`@StringerStep` 待填）
   - `StringerProperties` 能读取 `application.yaml` 里的 `stringer.server-url` 和 `stringer.api-key`
   - `StringerAnnotationScanner`：Spring 启动时扫描所有 Bean 的方法，找到 `@StringerStep`，打印出所有步骤名 + 参数签名（先用 console 打日志，中心平台 API 在 Phase 3 再做）
   - `stringer-example` 项目启动后，控制台能看到扫描到的步骤列表

### 风险

| 风险 | 影响 | 规避方案 |
|---|---|---|
| 多模块拆分后循环依赖（stringer-core 不小心 import stringer-server） | 高 | stringer-core 的 pom 里只写 `junit` 等测试依赖，绝不允许加任何 Spring 或中间件依赖；写 ArchUnit 测试强制校验 |
| Spring Boot 3 自动装配文件路径写错，Starter 不生效 | 中 | 必须放在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（不是旧版本的 `spring.factories`），写完写一个集成测试验证自动装配触发 |
| 客户项目里 Starter 的类路径和客户自己的类冲突 | 低 | 所有 Starter 内部类包名用 `com.zxc.stringer.starter.internal.*`，外部只暴露 `StringerProperties` + 注解，避免类名冲突 |

### 验收标准

- [x] 根目录 `mvn clean install` 一次通过，9 个子模块按顺序构建（✅ 已验证）
- [x] `stringer-core` 的 jar 大小 < 500KB（证明没有 Spring 依赖混进去）（✅ 仅依赖 langchain4j-core）
- [x] 启动 `stringer-server`，`/test/agent` 接口全流程回归通过（✅ 对话/知识库检索/checkpoint 正常）
- [ ] 进入 `stringer-example` 目录，`mvn spring-boot:run` 启动后控制台能打印出 "Stringer: scanned N steps: ..." 日志
- [ ] 解包 `stringer-spring-boot-starter-1.0-SNAPSHOT.jar`，确认包含 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件

---

## Phase 3：Stringer 中心平台后端

**目标**：`stringer-server` 从现在的"单项目 Agent Demo"升级成"多项目接入的中心平台"。

| 项目 | 内容 |
|---|---|
| 工期 | ~ 4 周 |
| 依赖 | Phase 2 验收通过 |
| 核心动作 | 接入 API → 调度引擎 → 审批流 → 检查点存储 → 任务历史 |

### 交付物清单

1. **中心平台 API（对外开放的 HTTP 接口）**

| 接口 | 方法 | 功能 | 调用方 |
|---|---|---|---|
| `/api/v1/step/report` | POST | Starter 上报 `@StringerStep` 定义 | stringer-spring-boot-starter |
| `/api/v1/run/start` | POST | 启动一次工作流执行 | 客户项目 Starter / 前端 |
| `/api/v1/run/{runId}/stream` | GET | SSE 流式接收执行进度 | 前端 |
| `/api/v1/run/{runId}/approve` | POST | 人工审批通过 | 前端 |
| `/api/v1/run/{runId}/reject` | POST | 人工审批拒绝 + 拒绝原因 | 前端 |
| `/api/v1/run/{runId}/stop` | POST | 停止执行 | 前端 |
| `/api/v1/run/{runId}` | GET | 查询单次运行详情 + 历史步骤 | 前端 |
| `/api/v1/workflow/list` | GET | 查询所有已注册的工作流定义 | 前端 |
| `/api/v1/approval/pending` | GET | 查询我待处理的审批列表 | 前端 |

2. **核心数据库表设计**（建议 PostgreSQL，先跑起来再说分库分表）

```sql
-- 工作流定义表（Starter 上报后写入）
CREATE TABLE wf_definition (
    id BIGSERIAL PRIMARY KEY,
    app_name VARCHAR(64) NOT NULL,       -- 来自 starter 的 stringer.app-name
    workflow_code VARCHAR(128) NOT NULL, -- 工作流编码（类名或自定义）
    step_json JSONB NOT NULL,            -- 所有步骤定义（@StringerStep），含参数签名
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (app_name, workflow_code, version)
);

-- 工作流运行实例
CREATE TABLE wf_run (
    id BIGSERIAL PRIMARY KEY,
    definition_id BIGINT NOT NULL REFERENCES wf_definition(id),
    run_no VARCHAR(32) NOT NULL UNIQUE,  -- 业务友好的运行号 SR20260823-000123
    status VARCHAR(16) NOT NULL,         -- RUNNING / WAIT_APPROVAL / SUCCESS / FAILED / STOPPED
    triggered_by VARCHAR(64) NOT NULL,   -- 触发人/自动
    checkpoint_ref VARCHAR(256),         -- 检查点存储引用（Redis key / DB id）
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP,
    error_message TEXT
);

-- 运行步骤明细
CREATE TABLE wf_run_step (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES wf_run(id) ON DELETE CASCADE,
    step_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,         -- PENDING / RUNNING / WAIT_APPROVAL / SUCCESS / FAILED / SKIPPED
    input_json JSONB,
    output_json JSONB,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(64),
    approved_at TIMESTAMP,
    approve_reason TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms INT
);

-- 审批事件日志（审计用，不可修改）
CREATE TABLE wf_audit_log (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES wf_run(id),
    action VARCHAR(32) NOT NULL,         -- START / STEP_BEGIN / APPROVE / REJECT / STOP / END
    operator VARCHAR(64) NOT NULL,
    detail JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

3. **调度引擎 MVP**
   - 单实例版即可：接收到 `/run/start` 请求后，按步骤定义顺序执行，遇到 `@RequireApproval` 就把 `wf_run.status` 改成 `WAIT_APPROVAL`，写入审批待办
   - 执行中每个步骤开始/结束都写 `wf_run_step` + 审计日志
   - 暂停时写 Checkpoint（复用现在的 RedisCheckpointSaver）

4. **Starter 对接中心平台**
   - 原来 `StringerAnnotationScanner` 只是打日志，现在改成真正调 HTTP 上报到 `/api/v1/step/report`
   - 新增 `StringerTemplate` 类（类似 `RestTemplate`、`RedisTemplate`），客户代码里可以 `@Autowired` 它，手动触发工作流

### 风险

| 风险 | 影响 | 规避方案 |
|---|---|---|
| 客户项目和中心平台之间网络不通，上报失败 | 高 | Starter 上报加本地缓冲：失败时写入客户项目的 `stringer-report.log`，后台线程每 30s 重试；加 "诊断模式" 端点 `/actuator/stringer` 输出上报状态 |
| 长步骤执行中中心平台重启，状态丢失 | 中 | 每个步骤开始时先写 DB 状态 `RUNNING`，结束后原子更新为 `SUCCESS/FAILED`；启动时后台线程扫描所有 `status=RUNNING` 超过 10 分钟的任务，标记为 `FAILED` 并触发告警 |

### 验收标准

- [ ] 上面 8 个 API 全部可通过 Postman 调用成功，DB 里对应表有数据
- [ ] `stringer-example` 启动后，Starter 能成功把 3-5 个 `@StringerStep` 上报到中心平台，`wf_definition` 表有记录
- [ ] 触发一个含 `@RequireApproval` 的工作流，验证：状态自动切 WAIT_APPROVAL → 审批通过后继续执行 → 全步骤写审计日志
- [ ] `/actuator/stringer` 端点输出上报成功率、待重试数量

---

## Phase 4：中心平台前端（可选优先级）

**目标**：给运维和业务人员一个可视化界面，不用查 DB 看状态。

| 项目 | 内容 |
|---|---|
| 工期 | ~ 3 周 |
| 依赖 | Phase 3 验收通过 |
| 核心动作 | 观测面板 → 流程图展示 → 审批页 → 运行历史 |

### 交付物清单

1. **技术栈建议**（和 Dify/Flowise 对齐）
   - React 19 + TypeScript + Vite
   - 流程图：`@xyflow/react`（原 React Flow，Langflow 也用它）
   - UI 组件：shadcn/ui + Tailwind CSS
   - 状态管理：Zustand（轻量，不折腾 Redux）

2. **4 个核心页面**

| 页面 | 功能 | 关键组件 |
|---|---|---|
| 运行观测面板 | 运行中任务数、今日成功率、平均耗时、审批待办数 Top N | 卡片 + ECharts 柱/折线图 |
| 工作流定义列表 | 所有已注册的工作流（按 app_name 筛选），点击看步骤拓扑图 | 表格 + `@xyflow/react` 拓扑图（只读展示，不允许画布编辑——这是 Stringer 和 Dify 的区别） |
| 运行详情页 | 单次执行的全步骤执行轨迹、每步输入/输出、审批记录、日志 | 时间线 + 折叠面板 + JSON 查看器 |
| 审批待办页 | 我待处理的审批列表，通过/拒绝 + 原因输入 | 表格 + 模态框 |

> **注意**：Stringer 前端**不提供可视化编辑工作流**（这是 Dify 的赛道）。用户只能在代码里改 `@StringerStep`、重启项目自动上报更新。前端只做"看 + 审批 + 停止"。

### 验收标准

- [ ] 4 个页面全部能从后端拉到真实数据并渲染
- [ ] 审批待办页操作通过后，后端 DB 状态同步更新，且能在运行详情页看到审批日志
- [ ] 流程图拓扑页：根据 `wf_definition.step_json` 自动渲染节点和连线（根据步骤参数依赖推断顺序，或按声明顺序）

---

## Phase 5：可靠性增强（生产就绪最后一公里）

**目标**：解决多实例部署 + 高并发 + 异常场景下的可靠性问题。

| 项目 | 内容 |
|---|---|
| 工期 | ~ 3 周 |
| 依赖 | Phase 3 验收通过（前端并行开发不影响） |
| 核心动作 | 分布式调度 → 重试 + 死信 → 任务确认 → 告警 |

### 交付物清单

1. **多实例分布式调度**
   - 现在 Phase 3 的调度是单实例本地执行，改成：所有执行请求写入队列（RabbitMQ/Redis Stream），多个 stringer-server 实例抢占消费
   - 避免重复消费：用 `wf_run.id` 做分布式锁（Redis `SETNX` 带过期），抢不到就跳过
   - 调度策略 SPI（写进 EXTENSIONPOINTS.md）：默认轮询，后续可扩展一致性哈希/优先级/权重

2. **重试 + 死信队列**
   - 步骤执行失败后自动重试（默认 3 次，指数退避 1s/2s/4s）
   - 超过最大重试次数进入死信队列（DLQ），DB 状态 `DEAD_LETTER`，不自动删除
   - 提供手动"重试死信"API 和前端操作入口

3. **任务确认机制（至少一次语义）**
   - Starter 收到步骤执行结果后，必须向中心平台发送 `ACK` 确认
   - 中心平台 30s 内没收到 ACK，自动触发重试
   - 防重复：`wf_run_step` 上唯一键 `(run_id, step_name, attempt_no)`，重复写入直接忽略

4. **告警通知扩展点**
   - `Notifier` SPI：`void onRunFailed(RunId id, String error)`
   - 默认实现：控制台日志；用户可实现飞书/钉钉/企微 webhook 通知
   - 内置告警规则：连续 3 次失败、死信队列深度 > 100、任务运行超过 10 分钟

### 验收标准

- [ ] 启动 3 个 stringer-server 实例，批量提交 100 个工作流，每个 run_id 只被 1 个实例执行（通过日志验证）
- [ ] 故意让某个步骤抛异常，验证 3 次重试 → 进死信 → 手动重试成功全链路
- [ ] 关掉 Starter 和中心平台之间的网络 1 分钟，再恢复，验证 30s 未 ACK 自动重试生效
- [ ] 注入故障：连续失败 > 3 次，收到飞书/钉钉告警（需先配置 Notifier 实现）

---

## 版本发布节奏

| 阶段完成 | 版本号 | 说明 |
|---|---|---|
| Phase 1 | 0.8.0-SNAPSHOT | 内部基线版本，仅仓库内可用 |
| Phase 2 | 0.9.0-M1 | 里程碑 1：Starter 可本地 install |
| Phase 3 | 0.9.0-M2 | 里程碑 2：中心平台后端可用 |
| Phase 4 | 0.9.0-RC1 | 候选版本：前后端打通 |
| Phase 5 | 1.0.0-GA | 正式发布：生产就绪 |

发布顺序：`stringer-core` → `stringer-spring-boot-starter` → `stringer-server` → `stringer-example`（严格顺序，前一个 install 成功才能 build 下一个）。
