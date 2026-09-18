package com.zzkingcc.stringer.example.controller;

import com.zzkingcc.stringer.api.agent.AgentRequest;
import com.zzkingcc.stringer.api.agent.AgentService;
import com.zzkingcc.stringer.api.agent.CallerContext;
import com.zzkingcc.stringer.api.event.AgentEvent;
import com.zzkingcc.stringer.api.event.AgentEventType;
import com.zzkingcc.stringer.common.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 联调用的 Agent HTTP 入口（示例层，仅一层 Controller）
 * @author zzkingcc
 */
@CrossOrigin
@RestController
@RequestMapping("/api/agent")
public class TestAgentController {

    private static final Logger log = LoggerFactory.getLogger(TestAgentController.class);

    /** 遗留文本协议:中断事件前缀,与 test.html 约定 */
    private static final String LEGACY_INTERRUPT_PREFIX = "__INTERRUPT__:";
    /** 遗留文本协议:停止事件,与 test.html 约定 */
    private static final String LEGACY_STOPPED = "__STOPPED__";

    /** 场景 → 域。与服务端工具声明的 {@code profiles} 对应。 */
    private static final String SCENARIO_CUSTOMER = "customer";
    private static final String SCENARIO_ADMIN = "admin";

    private final AgentService agentService;

    public TestAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 把前端指定的场景翻译成域。
     *
     * <p>真实接入时这一步应当换成"从当前登录用户的 SecurityContext / 角色表推导出场景"，
     * 而不是信任前端传来的字符串——示例为了便于对比演示才这么做。
     * 角色 → 域 的映射可以多对一（客服与客服主管可以都映射到 {@code customer}）。</p>
     */
    private static CallerContext callerOf(String scenario) {
        String profile = SCENARIO_ADMIN.equalsIgnoreCase(scenario) ? SCENARIO_ADMIN : SCENARIO_CUSTOMER;
        return CallerContext.of(profile, null, scenario);
    }

    /**
     * 发起一轮对话(事件流)
     *
     * @param request 请求体,sessionId 与 message 必填
     * @return 事件流(SSE),订阅后开始执行
     */
    @PostMapping(value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<AgentEvent> chat(@RequestBody AgentRequest request) {
        AgentRequest safeRequest = AgentRequest.builder()
                .sessionId(request.getSessionId())
                .message(InputSanitizer.validate(request.getMessage()))
                // 域必须原样透传：漏传会被服务端判为"未携带 profile"而直接拒绝
                .profile(request.getProfile())
                .tenantId(request.getTenantId())
                .userId(request.getUserId())
                .attributes(request.getAttributes())
                .build();
        log.info("[Agent入口] 会话[{}] chat 请求, profile={}, tenantId={}, userId={}",
                safeRequest.getSessionId(), safeRequest.getProfile(),
                safeRequest.getTenantId(), safeRequest.getUserId());
        return agentService.chat(safeRequest);
    }

    /**
     * 恢复被 INTERRUPT 事件挂起的会话
     *
     * @param sessionId 会话 ID,需与 chat 时一致
     * @param approved  true=批准执行工具; false=拒绝
     * @param caller    调用方身份（可选），权限需与 chat 时一致
     * @return 事件流(SSE)
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<AgentEvent> resume(@RequestParam String sessionId,
                                   @RequestParam boolean approved,
                                   @RequestBody CallerContext caller) {
        log.info("[Agent入口] 会话[{}] resume 请求, approved={}, profile={}",
                sessionId, approved, caller == null ? null : caller.profile());
        return agentService.resume(sessionId, approved, caller);
    }

    /**
     * 停止正在执行的任务(不可恢复)
     *
     * @param sessionId 会话 ID
     * @return {"sessionId":"...","stopRequested":true|false}
     */
    @PostMapping("/stop/{sessionId}")
    public Map<String, Object> stop(@PathVariable String sessionId) {
        boolean triggered = agentService.stop(sessionId);
        log.info("[Agent入口] 会话[{}] stop 请求, stopRequested={}", sessionId, triggered);
        return Map.of("sessionId", sessionId, "stopRequested", triggered);
    }

    /**
     * 遗留文本端点,供 test.html 使用
     *
     * <p>带 {@code scenario} 参数以区分公开用户 / 管理员两种权限场景。</p>
     *
     * <p><b>消息走 query 参数而不是路径变量</b>：用户输入的文本可能含 {@code /}、换行等字符，
     * 放在路径里会被 Tomcat 以 400 拒绝（{@code %2F} 默认不放行），且长文本与特殊字符也不适合放路径。
     * 路径写作 {@code /legacy/chat} 而非 {@code /{sessionId}/{message}}，避免与精确路径产生匹配歧义。</p>
     *
     * @deprecated 改用 {@link #chat(AgentRequest)}
     */
    @Deprecated
    @GetMapping(value = "/legacy/chat", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> chatLegacy(@RequestParam String sessionId,
                                   @RequestParam String message,
                                   @RequestParam(required = false, defaultValue = SCENARIO_CUSTOMER) String scenario) {
        String safeMessage = InputSanitizer.validate(message);
        CallerContext caller = callerOf(scenario);
        log.info("[Agent入口-遗留] 会话[{}] 场景={} 域={} 收到问题:{}",
                sessionId, scenario, caller.profile(), safeMessage);
        AgentRequest request = AgentRequest.builder()
                .sessionId(sessionId)
                .message(safeMessage)
                .profile(caller.profile())
                .build();
        return agentService.chat(request).map(this::toLegacyText);
    }

    /**
     * 遗留文本端点,供 test.html 使用
     *
     * @deprecated 改用 {@link #resume(String, boolean, CallerContext)}
     */
    @Deprecated
    @GetMapping(value = "/resume/{sessionId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> resumeLegacy(@PathVariable String sessionId,
                                     @RequestParam boolean approved,
                                     @RequestParam(required = false, defaultValue = SCENARIO_CUSTOMER) String scenario) {
        CallerContext caller = callerOf(scenario);
        log.info("[Agent入口-遗留] 会话[{}] resume 请求, approved={}, 场景={}, 域={}",
                sessionId, approved, scenario, caller.profile());
        return agentService.resume(sessionId, approved, caller).map(this::toLegacyText);
    }

    /** 遗留文本协议:工具调用痕迹前缀,与 test.html 约定。格式 {@code __TOOL__:工具名:参数JSON} 后跟换行 */
    private static final String LEGACY_TOOL_PREFIX = "__TOOL__:";

    /** 事件 → 遗留文本协议,仅供 test.html 解析 */
    private String toLegacyText(AgentEvent event) {
        if (event.getType() == AgentEventType.INTERRUPT) {
            return LEGACY_INTERRUPT_PREFIX + event.getPayload();
        }
        if (event.getType() == AgentEventType.STOPPED) {
            return LEGACY_STOPPED;
        }
        if (event.getType() == AgentEventType.ERROR) {
            // 状态码必须一起带出去：这条链路是仓库内唯一能端到端跑起来的客户端，
            // 丢掉 code 就把"去管控台补配置(90005)""域不存在(10004)""会话状态异常(30002)"
            // 全抹平成一句文本，调用方只能靠读中文猜下一步该做什么。
            String code = event.getCode() == null
                    ? "" : "[" + event.getCodeName() + "(" + event.getCode() + ")]";
            String trace = event.getTraceId() == null ? "" : " traceId=" + event.getTraceId();
            return "[ERROR]" + code + event.getContent() + trace;
        }
        if (event.getType() == AgentEventType.TOOL_CALL) {
            // 把工具调用渲染成单独一行，前端据此展示"调用了哪个工具"——
            // 这是两个权限面板差异最直观的证据
            String args = event.getPayload() == null ? "" : event.getPayload().replace("\n", " ");
            return LEGACY_TOOL_PREFIX + event.getContent() + ":" + args + "\n";
        }
        if (event.getType() == AgentEventType.TOOL_RESULT) {
            // 结果不直接展示（内容可能很长），避免挤占对话
            return "";
        }
        return event.getContent() == null ? "" : event.getContent();
    }
}
