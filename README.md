<h1 align="center">Stringer</h1>

<p align="center">
  <strong>在现有 Java 应用里跑一个可运维的 AI Agent 运行时。<br>引入一个 starter，编排、工具治理、知识库、管控台都不用自己写。</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0-blue?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/license-Apache--2.0-yellow?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white" alt="java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="spring-boot">
  <img src="https://img.shields.io/badge/LangChain4j-1.18.1-7B68EE?style=flat-square" alt="langchain4j">
  <img src="https://img.shields.io/badge/LangGraph4j-1.8.17-008080?style=flat-square" alt="langgraph4j">
  <img src="https://img.shields.io/badge/Elasticsearch-9.x-005571?style=flat-square&logo=elasticsearch&logoColor=white" alt="elasticsearch">
  <img src="https://img.shields.io/badge/Redis-6%2B-DC382D?style=flat-square&logo=redis&logoColor=white" alt="redis">
</p>

---

## 这是什么

Stringer 是 **Java 生态的 Agent 运行时中间件**，交付形态与 Redis、Nacos 一致：**独立服务端 + 薄客户端 starter**。

- **服务端（`stringer-server`）** —— 一个可独立部署的 jar。编排、工具路由、知识库、会话记忆、模型接入、管控台全在这里，端口 9527。
- **starter（`stringer-spring-boot-starter`）** —— 消费侧唯一需要的坐标，引一个就够。注入 `AgentService`，像调用本地方法一样发起一轮 Agent 对话；同时带出工具实例 SDK（把本进程的方法交给 Agent 调用，默认关闭）与公共异常 / 输入安全。
- **工具实例 SDK（`stringer-tool-instance`）** —— 工具不必写在业务进程里。任何进程（含非 Java 应用）按 HTTP 协议注册到服务端，即可被 Agent 调用，零 Stringer 依赖。已随 starter 传递，纯工具方可单独引入。

**一句话：库给你积木，平台让你搬家，Stringer 让你留在原地。**

## 为什么需要它

把 Agent 能力做进一个已有 Java 系统，真正的工作量不在"调模型"：

| 自己拼 | 用 Stringer |
| --- | --- |
| 选一个编排库，自己定义节点、分支、状态 | 图编排 + 状态持久化已就绪，支持中断与恢复 |
| 自己接 Redis 存检查点、定义过期策略、处理反序列化 | 检查点与记忆按会话隔离，自动过期 |
| 自己拼向量检索 + 关键词检索 + 分数融合 | 混合检索与重排是配置项，不是代码 |
| 自己设计工具注册、多实例保活、下线摘除 | 注册中心式心跳，实例上下线自动生效 |
| 自己判断"这个工具该不该给模型看见" | 域（profile）一处声明，模型视野自动收敛 |
| 自己实现"退款这类操作要先问人" | 声明审批策略即得中断-恢复链路 |
| 运维问"现在有哪些工具、哪些实例、谁在跑" | 内置 8 页管控台 |
| 上面每一条出问题，都算在你头上 | 它们是中间件的事，你只写自己的工具 |

这些代码与业务无关，但每一条出问题都算在业务方头上。Stringer 把它们收进中间件。

## 与主流方案相比

| 维度 | Stringer | Dify / FastGPT | Spring AI / LangChain4j | LangGraph4j |
| --- | --- | --- | --- | --- |
| 形态 | **独立服务端 + 薄 starter** | 独立平台（容器编排部署） | 库，随业务进程 | 库 |
| 技术栈 | Java 21 / Spring Boot | Python 为主 | Java | Java |
| 嵌入现有 Java 应用 | 引 starter、注入 Bean；业务代码零改动 | 另起一套进程，走 REST / iframe | 业务代码里手写编排 | 业务代码里手写状态与持久化 |
| 编排与状态 | 图编排 + Redis 检查点，**中断后可跨实例恢复** | 可视化工作流 | 需自行实现 | 提供图，持久化接入自行实现 |
| 工具治理 | **域可见性 + 审批中断 + 多实例注册中心** | 插件 / 工具市场 | 无 | 无 |
| 运维界面 | 内置 8 页管控台 | 内置可视化界面 | 无 | 无 |
| 与业务共用 ES / Redis | 命名空间隔离，**可同一套实例** | 独立存储 | 取决于业务侧实现 | 取决于业务侧实现 |
| 定位 | Agent 运行时（可运维） | AI 应用搭建平台 | 开发库 | 编排库 |

选择建议：

- 要**无代码拖拽搭应用** → 去 Dify / FastGPT。
- 只是**调几次模型 API** → 直接用厂商 SDK，不必上中间件。
- 要**把 Agent 嵌进已有 Java 微服务**、工具分散在多个服务里、还要能被人审计和干预 → 这是 Stringer 的位置。

## 适合谁 / 不适合谁

**适合**：已有 Java 微服务且不想为 AI 另起一套技术栈；工具体系分散在多个服务、需要统一注册与治理；有 on-prem、数据不出域或信创要求；需要人工审批介入高风险操作（退款、改单、群发）；需要知道"现在有哪些工具、哪些实例在线"。

**不适合**：以可视化拖拽为主的低代码搭建；纯 Python 技术栈；单机脚本级的一次性调用。

## 部署形态：一个 jar，跑遍各端

Stringer 服务端是**平台无关的单文件 fat jar**：一份构建产物，在 Linux、Windows、 macOS（含其他 Unix 类）上直接 `java -jar` 即可运行，无需为目标系统重新构建。这与"中间件"的定位一致——像 Redis、Nacos 一样，拿到二进制就能跑。

- **启动期自动探测运行系统**：`RuntimeEnvironment` 在 Spring 装配前用 `System.getProperty("os.name")` 判定 OS 族，自动选定配置 / 日志目录并提前建好，不再写死 Linux 路径。
  - Linux / 其他 Unix：`/var/lib/stringer/config`、`/var/log/stringer`
  - Windows：`%ProgramData%\Stringer\config`、`%ProgramData%\Stringer\logs`
  - macOS：`/Library/Application Support/Stringer/config`、`/Library/Logs/Stringer`
- **覆盖优先级**：命令行 `--key` ＞ JVM 系统属性 `-Dkey` ＞ 环境变量 `STRINGER_SETTINGS_PATH` / `STRINGER_LOG_PATH` ＞ 平台默认。
- **容器部署**：Docker / K8s 只需换对应基底镜像（如 `eclipse-temurin:21-jre`），jar 不变；容器运行时（kubernetes / docker / podman）会在启动横幅中显示，便于排障。

### 相比 Python 部署的优势

| 维度 | Stringer（Java fat jar） | 典型 Python 部署 |
| --- | --- | --- |
| 分发 | 单文件 jar，构建一次到处跑 | 常需 lock 文件 + 虚拟环境 + 解释器版本对齐 |
| 依赖 | 全部打进 jar，无运行时依赖地狱 | 系统 / 用户 site-packages 易冲突，需 venv 隔离 |
| 跨平台 | 字节码一次编译，各 OS 行为一致 | C 扩展（numpy / torch 等）需按平台预编译 wheel |
| 启动 | JVM 预热后即稳定，无需运行时联网拉包 | 冷启动常伴随 `pip install` 或镜像层下载 |
| 类型与可维护性 | 强类型、编译期检查，重构安全 | 动态类型，大型项目易在运行时才暴露类型错 |
| 并发 | 工业级多线程，无 GIL 瓶颈 | CPython 受 GIL 限制，CPU 密集需多进程 |
| 内存可控 | 堆内存可显式约束（`-XX:MaxRAMPercentage`） | 引用计数 + GC，长驻内存不易预估 |

## 核心能力

| 能力 | 具体到能做什么 |
| --- | --- |
| **图编排状态机** | 每一步显式可控（`agent → 条件路由 → tools/review → agent`），状态透明、可中断、可恢复 |
| **人工审批（HITL）** | 工具声明审批策略后，调用前自动中断等待确认；中断点落 Redis，**服务端重启后仍可恢复** |
| **域（profile）可见性** | 一次对话必须声明所处的域，模型只能看到该域的工具、只能拿到该域的提示词；域不存在直接报错，**绝不静默降级成全量工具** |
| **远程工具注册中心** | 工具实例周期整包上报声明，服务端按实例维护副本；实例掉线自动摘除、重连自动恢复，同名工具多实例可同时在线 |
| **混合检索** | 向量检索与关键词检索并行执行，归一化后加权融合；切片按中文章节边界切分并携带来源元数据 |
| **双约束会话记忆** | 同时约束消息条数与 Token 估算，按会话隔离存储 |
| **流式输出与中断** | 事件流逐帧下发；任务可随时停止 |
| **内置管控台** | 8 页：概览、模型设置、存储配置、域空间、在线实例、提示词设定、知识库、账号 |
| **零配置可启动** | 未填 ES / Redis / 模型也能启动，缺配置只在**调用时**报明确错误并指向该去哪一页填 |

## 快速开始

### 环境要求

- JDK 21+、Maven 3.8+
- Elasticsearch 9.x（已验证；8.x 可用；更低版本需自行验证）—— 需安装 IK 分词器插件
- Redis 6+
- 一个 OpenAI 兼容的模型服务（对话模型 + 向量模型）

> ES / Redis **可与业务系统共用同一套实例**：数据层通过私有命名空间隔离（Redis key 统一 `stringer:` 前缀、ES 索引统一 `stringer_` 前缀），双方各自建立独立连接，互不影响。

### 第一步：启动服务端

```bash
mvn -pl stringer-server -am install
mvn -pl stringer-server spring-boot:run     # 默认端口 9527
```

**不需要预先准备配置文件。** 服务端在未配置状态下即可启动（只跳过需要依赖的动作，不阻断启动），启动后打开管控台填写：

```
http://localhost:9527/admin.html      # 默认账号 stringer / stringer
```

依次在「模型设置」填对话模型与向量模型、「存储配置」填 ES 与 Redis（每页都有"测试连接"，填完可当场验证）。

> 配置从 yaml 搬到管控台是刻意的：连接信息与密钥不随源码、镜像分发，且在依赖不可用时仍能进调控台改回来。落盘位置 `config/*.json`，与源码分离。

### 第二步：业务系统接入

```xml
<dependency>
    <groupId>com.zzkingcc</groupId>
    <artifactId>stringer-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

> **一个依赖就够。** `stringer-spring-boot-starter` 同时带来三件事：调 AI（`AgentService`）、把本进程的方法作为工具交给 Agent（工具实例 SDK，**默认关闭**，需要时打开 `stringer.tool-instance.enabled`）、公共异常与输入安全。Web 容器不在其中——宿主原有的 Spring MVC / WebFlux 栈保持不变即可。只想当工具方（工具微服务、非 Java 应用）可只引 `stringer-tool-instance`。详见[实例文档 §1.1](docs/INSTANCE.md#11-一个依赖跑起来)。

```yaml
stringer:
  server:                       # 客户端与工具实例共用这一份地址与账号
    host: localhost
    port: 9527
    username: stringer          # 服务端改过密码后需同步
    password: stringer
```

> **服务端必须先启动**（与 Redis / Nacos 的接入习惯一致）。引入 starter 的应用在启动完成前会换取签名凭证并探测服务端健康状态，连不上或账号密码错误会**直接中断启动**并给出排查提示——不提供关闭开关：允许应用先于中间件启动，等于让它在必然不可用的状态下对外服务。
>
> 凭证不设有效期，正常路径下登录只发生一次；服务端改过密码后客户端会自动重登一次，仍失败则中断启动并提示原因。

### 第三步：发起一轮对话

```java
@Service
public class MyService {
    private final AgentService agentService;

    public MyService(AgentService agentService) {
        this.agentService = agentService;
    }

    public Flux<AgentEvent> ask(String sessionId, String question) {
        return agentService.chat(AgentRequest.of(sessionId, question, "customer"));
    }
}
```

第三个参数是**域**：平台据此决定模型能看到哪些工具、用哪份提示词。域由工具声明即创建，不需要预先注册：

```java
@StringerTool(name = "queryOrder", description = "查询订单详情", profiles = {"customer"})
public String queryOrder(String orderNo) { ... }
```

审批也只有一行：

```java
@ToolPolicy(approval = @Approval(mode = Approval.Mode.ALWAYS, reason = "退款需人工确认"))
public String refundOrder(String orderNo, BigDecimal amount) { ... }
```

> 域是**调用方自行声明、平台信任**的治理机制（防止模型误用工具、防止提示词与工具集错位），**不是安全边界**——用哪个域由客户端决定，平台无法验证真伪。终端用户的身份与授权属于宿主自己的 IAM。

### 联调示例

仓库内置 `stringer-example`（客户端接入示例，端口 8080，自带 4 个演示工具并以"工具实例"身份注册给服务端）：

```bash
mvn -pl stringer-example spring-boot:run
```

访问 `http://localhost:8080/test.html` 体验完整链路（含审批中断 → 恢复）。

## 架构

```
业务系统（引入 starter，注入 AgentService）
   │  HTTP + SSE
   ▼
stringer-server
   ├─ 图编排 · 工具注册中心
   ├─ 知识库 · 会话记忆
   ├─ ES · Redis · 模型服务
   └─ 管控台 http://localhost:9527/admin.html
   ▲
   │  注册 + 心跳
工具提供方（tool-instance SDK，或按 HTTP 协议自实现）
```

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `stringer-api` | 对外契约：`AgentService` / 注解 / 事件 / 工具描述符 / 错误码 |
| `stringer-common` | 公共支撑：异常体系 / 输入安全 |
| `stringer-domain` | 领域能力：知识检索 / 混合重排 / 记忆策略 |
| `stringer-infrastructure` | 基础设施：ES 检索与索引管理 / 文档摄取切片 / Redis / 向量化 |
| `stringer-runtime` | Agent 运行时内核：图编排 / 工具注册表与路由 / 实例注册表 / 流式 / 提示词 |
| `stringer-server` | **服务端**：可独立部署，承载全部重逻辑与管控台 |
| `stringer-spring-boot-starter` | **消费侧唯一坐标**：远程调用 + 工具实例 SDK + 公共异常与输入安全 |
| `stringer-tool-instance` | **工具实例 SDK**：注册与心跳保活 + 工具调用端点，零 Stringer 依赖（随 starter 传递） |
| `stringer-example` | 接入示例与联调 |

## 接口

业务系统通过 `AgentService` 调用，无需拼 HTTP；需要裸 HTTP 时看这几个：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/login` | 账号密码换签名凭证（免鉴权） |
| GET | `/api/agent/health` | 健康探测 |
| POST | `/api/agent/chat` | 发起对话，SSE 返回事件流 |
| POST | `/api/agent/resume` | 恢复因审批而中断的会话 |
| POST | `/api/agent/stop/{sessionId}` | 停止执行中的任务 |
| POST | `/api/agent/tools/register` | 工具实例注册与心跳 |

完整端点、SSE 事件契约、错误码总表、SDK 用法见 [API 文档](docs/API.md)。

## 文档

- [设计文档](docs/DESIGN.md) —— 形态与模块、域与工具可见性、工具体系、存储与模型配置、并发模型、配置项总表
- [API 文档](docs/API.md) —— 全部 HTTP 端点、SSE 事件契约、错误码总表、starter 与工具实例 SDK
- [实例文档](docs/INSTANCE.md) —— 配置与接入实操：服务端配置、客户端 starter 接入、工具实例 SDK、本地 Bean 工具、域机制、端到端跑通

## License

[Apache-2.0](LICENSE)
