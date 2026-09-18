<h1 align="center">Stringer</h1>

<p align="center">
  <strong>An AI agent runtime middleware for the Java ecosystem.<br>Add one starter: inject AgentService to call AI, annotate a method with @StringerTool to let AI call you. Orchestration, tool governance, knowledge base and the ops console all live in the server.</strong>
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

## What is this

Stringer is an **AI agent runtime middleware for the Java ecosystem**, packaged the way Redis and Nacos are: **a standalone server plus a thin client starter**.

- **Server (`stringer-server`)** — a standalone deployable JAR. Orchestration, tool routing, knowledge base, chat memory, model access and the ops console all live here. Port 9527.
- **Starter (`stringer-spring-boot-starter`)** — the only coordinate you need on the consumer side. Inject `AgentService` and run an agent turn as if it were a local method call; the same dependency also brings the tool instance SDK (hand your own methods to the agent, **off by default**) and the shared exception / input-sanitization support.
- **Tool instance SDK (`stringer-tool-instance`)** — tools do not have to live inside your business process. Any process (including non-Java apps) can register over plain HTTP and become callable by the agent; the Java side only depends on the contract module `stringer-api` (annotations and event model) and on no internal Stringer implementation. It is delivered transitively by the starter; tool-provider-only deployments can depend on it directly.

**A library hands you bricks. A platform asks you to move house. Stringer lets you stay where you are.**

## Why you need it

Shipping agent capabilities into an existing Java system is not really about "calling a model":

| Assembling it yourself | With Stringer |
| --- | --- |
| Pick an orchestration library, define nodes, branches and state | Graph orchestration with state persistence, out of the box — interruptible and resumable |
| Wire Redis for checkpoints, define TTLs, handle deserialization failures | Checkpoints and memory are session-scoped and expire on their own |
| Stitch together vector search, keyword search and score fusion | Hybrid retrieval and score fusion are configuration, not code |
| Design tool registration, multi-instance keep-alive, offline removal | Registry-style heartbeats; instances come and go automatically |
| Decide which tools the model is allowed to see | Declare a profile once; the model's tool view narrows automatically |
| Build "ask a human before refunding" from scratch | Declare an approval policy; the interrupt/resume path is already there |
| Answer ops when they ask what tools and instances exist right now | A built-in 8-page console |
| Every one of the above, when it breaks, is your incident | They are the middleware's problem — you only write your own tools |

None of this is business logic, yet all of it lands on the business team when it fails. Stringer moves it into the middleware.

## How it compares

Orchestration libraries such as LangGraph4j belong to the "library" column — their differences from Stringer are the same as Spring AI / LangChain4j, so they are not listed separately.

| Dimension | Stringer | Dify / FastGPT | Spring AI / LangChain4j |
| --- | --- | --- | --- |
| Form factor | **Standalone server + thin starter** | Standalone platform (container-deployed) | Library, inside your process |
| Stack | Java 21 / Spring Boot | Mostly Python | Java |
| How you write a tool | Your existing Spring bean: annotate a method with `@StringerTool` | Configure it in the platform / plugin marketplace | Write code and wire the routing yourself |
| Where tools run | **Inside your process**, reusing your transactions, permissions and `@Service`s | In the platform process, called over HTTP across systems | Inside your process |
| Business code change | Inject `AgentService` to call the agent — none | Separate process, integrate over REST / iframe | Orchestration and state code lands in your business project |
| Orchestration & state | Graph orchestration + Redis checkpoints, **resumable across instances** | Visual workflows | Build it yourself |
| Tool governance | **Profile visibility + approval interrupts + a multi-instance registry** | Plugins / tool marketplace | None built in; build it yourself |
| Ops console | Built-in, 8 pages, plus a runtime metrics snapshot | Its own visual UI | None |
| Storage | Namespace-isolated — **you can share one ES / Redis with your app** | Separate storage | Depends on your implementation |
| Deployment cost | One extra server to run (ES / Redis can be shared with your app) | The platform plus its own dependencies | Nothing extra |
| Best fit | Existing Java microservices, tools spread across services, human approval and operability required | No-code drag-and-drop app building | Calling a model API a handful of times |

One more note: prompt changes made in the console take effect immediately — no server restart.

## Who it's for / Who it isn't

**For you if**: you already run Java microservices and do not want a second stack for AI; your tools are scattered across services and need one place to register and govern them; you have on-prem, data-residency or compliance constraints; high-risk operations (refunds, order edits, broadcasts) need human approval; ops needs to know which tools and instances are live.

**Not for you if**: you want visual no-code building; you are a pure Python shop; or you need a one-off script that calls a model once.

## Deployment shape: one JAR, every platform

The Stringer server is a **platform-neutral single-file fat JAR**: one build artifact runs directly with `java -jar` on Linux, Windows and macOS (and other Unix-like systems) — no per-target rebuild. This matches its middleware positioning: like Redis or Nacos, you just pick up the binary and run it.

- **OS detection at startup**: `RuntimeEnvironment` reads `System.getProperty("os.name")` before Spring wires up, picks the config and log directories for the detected OS family, and creates them up front — no hard-coded Linux path.
  - Linux / other Unix: `/var/lib/stringer/config`, `/var/log/stringer`
  - Windows: `%ProgramData%\Stringer\config`, `%ProgramData%\Stringer\logs`
  - macOS: `/Library/Application Support/Stringer/config`, `/Library/Logs/Stringer`
- **Override precedence**: CLI `--key` ＞ JVM system property `-Dkey` ＞ env var `STRINGER_SETTINGS_PATH` / `STRINGER_LOG_PATH` ＞ platform default.
- **Containers**: for Docker / K8s just swap the base image (e.g. `eclipse-temurin:21-jre`); the JAR stays the same. The container runtime (kubernetes / docker / podman) is shown on the startup banner for easier troubleshooting.

## Core capabilities

| Capability | What it actually gives you |
| --- | --- |
| **State-graph orchestration** | Every step is explicit (`agent → conditional routing → tools/review → agent`), with transparent state, interrupt and resume |
| **Human-in-the-loop** | Tools that declare an approval policy pause before execution; the interrupt point is persisted in Redis and **survives a server restart** |
| **Profile-based visibility** | Each turn declares its profile; the model only sees that profile's tools and prompt. An unknown profile is an error — **never a silent fallback to every tool** |
| **Remote tool registry** | Tool instances report their full declaration on a heartbeat; the server keeps per-instance replicas. Instances that drop off are removed automatically, and several instances of the same tool can be online at once |
| **Instance availability control** | Mute (fuse) / restore / force-offline a single instance from the console: muting removes every tool replica that instance reports, without touching the process itself; a force-offlined instance gets a 410 and stops heartbeating |
| **Hybrid retrieval** | Vector and keyword search run in parallel and are fused after normalization; chunks are split on Chinese section boundaries and carry source metadata |
| **Knowledge upload and rebuild** | Upload documents straight from the console (default extensions `md` / `txt`, whitelist configurable), inspect ingestion status and rebuild the whole index; a missing ES IK analyzer is detected by the "test connection" on the Storage page (three states: available / confirmed missing / not probed) |
| **Dual-constraint memory** | Caps both message count and estimated tokens, stored per session |
| **Streaming and cancellation** | Events are pushed frame by frame; a running turn can be stopped at any time |
| **Built-in console** | 8 pages: overview, models, storage, domains, instances, prompts, knowledge base, account |
| **Runtime metrics snapshot** | `GET /admin/metrics` returns registered tool count, running sessions, heap usage and core metric snapshots — ready for your existing monitoring collector (admin surface, credential required) |
| **Starts with zero configuration** | ES / Redis / models can all be missing at startup; missing configuration is reported **at call time** with a pointer to the exact console page |

## Quick start

### Prerequisites

- JDK 21+, Maven 3.8+
- Elasticsearch 9.x (verified; 8.x works; older versions are unverified) — with the IK analyzer plugin installed
- Redis 6+
- An OpenAI-compatible model service (a chat model and an embedding model)

> ES / Redis **can be shared with your existing application**: data is separated by private namespaces (Redis keys use the `stringer:` prefix, ES indices the `stringer_` prefix) and each side opens its own connection. Dedicated instances work too.

Any **OpenAI-compatible endpoint** works — a hosted API and a local deployment are treated the same, and a **local Ollama needs no code changes**:

| Field on the "Models" page | What to enter for a local Ollama |
| --- | --- |
| Chat · base URL | `http://localhost:11434/v1` (**the `/v1` is required**) |
| Chat · API key | Any non-empty value, e.g. `ollama` (Ollama ignores it, but this system requires the field) |
| Chat · model name | `qwen2.5:7b`, `llama3.1:8b`, … — with the right base URL the dropdown lists your local models |
| Embedding · model name | `nomic-embed-text`, `bge-m3`, … |
| Embedding · dimensions | **Leave empty**: Ollama's `/v1/embeddings` does not accept OpenAI's `dimensions` parameter, so it would be ignored |

Two things to keep in mind:

- **The chat model must support tool calling (function calling)** (e.g. `qwen2.5`, `llama3.1`). Without it the agent can still chat, but it will never call the tools you registered.
- Switching embedding models changes the vector dimension, and the ES index dimension is fixed at index-creation time — rebuild the index from the "Knowledge base" page. When the server runs in a container, `localhost` means the container itself, so use the host address instead.

### Step 1: Start the server

```bash
mvn -pl stringer-server -am install
mvn -pl stringer-server spring-boot:run     # port 9527 by default
```

**No configuration file is required up front.** The server starts even with nothing configured — it only skips the actions that need a dependency instead of refusing to boot. Then open the console and fill things in:

```
http://localhost:9527/admin.html      # default account stringer / stringer
```

Enter the chat and embedding models under "Models", and the ES / Redis connections under "Storage". Both pages have a "test connection" button so you can verify on the spot.

> Moving this configuration out of yaml and into the console is deliberate: connection details and secrets no longer travel with source code or images, and you can still fix a broken connection because the console does not depend on it. Settings are persisted to `config/*.json`, outside the artifact.

### Step 2: Integrate the client

```xml
<dependency>
    <groupId>com.zzkingcc</groupId>
    <artifactId>stringer-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

> **One dependency is enough.** `stringer-spring-boot-starter` brings three things at once: calling the agent (`AgentService`), handing your own methods to the agent as tools (tool instance SDK, **off by default** — set `stringer.tool-instance.enabled` to turn it on), and the shared exception / input-sanitization support. No web container is included — your existing Spring MVC or WebFlux stack simply stays as it is. Tool-provider-only deployments (tool microservices, non-Java apps) can depend on `stringer-tool-instance` alone. See [instance doc §1.1](docs/INSTANCE.md#11-一个依赖跑起来).

```yaml
stringer:
  server:                       # one shared address and account for both client and tool instance
    host: localhost
    port: 9527
    username: stringer          # keep in sync if the server password changes
    password: stringer
```

> **The server must start first**, as with Redis or Nacos. Before your application is marked ready, the starter exchanges credentials for a signed credential and probes the server's health; an unreachable server or wrong credentials **abort startup** with a diagnostic message. There is deliberately no switch to disable this: letting an application start ahead of its middleware means serving traffic in a state that is guaranteed broken.

> Credentials are not sent on every request. The client logs in once, caches a signed credential with **no expiry**, and reuses it — if the server password changed it re-logs in once automatically, and aborts startup with an explanation if that also fails.

### Step 3: Inject a bean, run a turn

```java
@Service
public class MyService {
    private final AgentService agentService;

    public MyService(AgentService agentService) {   // auto-configured by the starter; no annotation needed
        this.agentService = agentService;
    }

    public Flux<AgentEvent> ask(String sessionId, String question) {
        return agentService.chat(AgentRequest.of(sessionId, question, "customer"));
    }
}
```

The third argument is the **profile**: it determines which tools the model can see and which prompt it receives.

### Step 4: Annotate a method, turn it into a tool

Set `stringer.tool-instance.enabled=true`, then declare on any Spring bean method:

```java
// Read-only: visible in the customer profile; the parameter schema is derived from the signature
@StringerTool(name = "queryOrder", description = "Look up an order by number. Call when the user asks about shipping",
        profiles = {"customer"})
public String queryOrder(@ToolParam(description = "Order number, e.g. FR2024001") String orderNo) { ... }

// Write: side effect declared + interrupts for human approval before every call
@StringerTool(name = "refundOrder", description = "Refund an order. Call only when the user explicitly asks for a refund",
        profiles = {"admin"}, sideEffect = StringerTool.SideEffect.WRITE)
@ToolPolicy(approval = @ToolPolicy.Approval(mode = Mode.ALWAYS, reason = "Refunds need human sign-off"))
public String refundOrder(@ToolParam(description = "Order number") String orderNo,
                          @ToolParam(description = "Amount in CNY") BigDecimal amount) { ... }
```

The signature is the parameter schema, the annotation is the governance policy, the body is the implementation — all in one place. When the tool list has to be assembled dynamically at startup, register programmatically via `ToolInstanceContributor` instead (programmatic wins on name conflicts).

Profiles are created by tools declaring them — there is nothing to register in advance.

> Profiles are a **caller-declared, platform-trusted** governance mechanism — they keep the model from misusing tools and keep prompts aligned with the visible tool set. They are **not a security boundary**: the client picks the profile and the platform cannot verify it. End-user identity and authorization remain the host's own IAM.

### Demo

The repository includes `stringer-example` (a client demo on port 8080, shipping six demo tools — all declared with `@StringerTool` — registered as a tool instance):

```bash
mvn -pl stringer-example spring-boot:run
```

Open `http://localhost:8080/test.html` for the full path, including an approval interrupt and resume.

## Architecture

```
Business system (adds the starter, injects AgentService)
   │  HTTP + SSE
   ▼
stringer-server
   ├─ graph orchestration · tool registry
   ├─ knowledge base · chat memory
   ├─ ES · Redis · model services
   └─ console at http://localhost:9527/admin.html
   ▲
   │  register + heartbeat
Tool provider (tool-instance SDK, or the HTTP protocol)
```

## Modules

| Module | Description |
| --- | --- |
| `stringer-api` | Contracts: `AgentService` / annotations / events / tool descriptors / error codes |
| `stringer-common` | Common support: exceptions / input security |
| `stringer-domain` | Domain capabilities: knowledge retrieval / hybrid search with score fusion / memory policy |
| `stringer-infrastructure` | Infrastructure: ES retrieval and index management / document ingestion and splitting / Redis / embedding |
| `stringer-runtime` | Agent runtime core: graph orchestration / tool registry and routing / instance registry / streaming / prompts |
| `stringer-server` | **Server**: standalone deployable, hosts all heavy logic and the console |
| `stringer-spring-boot-starter` | **Consumer-side single coordinate**: remote calls + tool instance SDK + shared exceptions and input security |
| `stringer-tool-instance` | **Tool instance SDK**: registration and heartbeat keep-alive plus the invocation endpoint; depends only on the contract module `stringer-api`, no internal implementation (delivered transitively by the starter) |
| `stringer-example` | Integration demo |

## API

Business systems call through `AgentService` and never hand-write HTTP; when you do need raw HTTP, these are the ones that matter:

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/agent/login` | Exchange credentials for a signed credential (auth-exempt) |
| GET | `/api/agent/health` | Health probe |
| POST | `/api/agent/chat` | Start a turn; returns an SSE event stream |
| POST | `/api/agent/resume` | Resume a session interrupted by an approval |
| POST | `/api/agent/stop/{sessionId}` | Stop a running task |
| POST | `/api/agent/tools/register` | Tool instance registration and heartbeat |

The admin surface used by the console (`/admin/*`: settings, models, profiles, instance mute and offline, knowledge upload and rebuild, metrics snapshot, account) is not listed above. Full endpoints, the SSE event contract, the error code table and SDK usage are in the [API documentation](docs/API.md).

## Documentation

- [Design](docs/DESIGN.md) — form factor and modules, profiles and tool visibility, the tool system, storage and model configuration, concurrency, configuration reference
- [API](docs/API.md) — all HTTP endpoints, SSE event contract, error code table, starter and tool instance SDK
- [Instance](docs/INSTANCE.md) — configuration and integration walkthrough: server config, client starter integration, tool instance SDK, local Bean tools, the profile mechanism, end-to-end run

## License

[Apache-2.0](LICENSE)
