package com.zxcSpringAI.agent.router;

import com.zxcSpringAI.agent.tool.LocalToolWrappers;
import com.zxcSpringAI.common.annotation.RequireApproval;
import com.zxcSpringAI.common.util.TokenUsageTracker;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具路由器
 *
 * <p>Agent 调用能力的唯一入口。统一管理工具注册、规格生成、授权标记和执行路由。</p>
 *
 * <p>职责:
 * <ol>
 *   <li>反射扫描 LocalToolWrappers 的 @Tool 方法,生成 ToolSpecification 列表(喂给 LLM 的"菜单")</li>
 *   <li>为每个工具创建 DefaultToolExecutor(实际调用 wrapper 方法)</li>
 *   <li>扫描 @RequireApproval 注解,标记需授权工具</li>
 *   <li>提供 execute(request) 统一执行入口,内含 Token 用量统计</li>
 * </ol>
 *
 * <p>未来扩展 MCP 时,此处增加 MCP 工具的注册和路由逻辑,
 * Agent 层无需感知本地工具与远程 MCP 工具的差异。</p>
 */
@Component
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Set<String> toolsRequiringApproval;

    public ToolRouter(LocalToolWrappers wrappers) {
        this.toolSpecifications = new ArrayList<>();
        this.toolExecutors = new HashMap<>();
        this.toolsRequiringApproval = new HashSet<>();

        // 反射扫描 @Tool 方法,生成规格和执行器
        for (Method m : wrappers.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(Tool.class)) {
                ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(m);
                toolSpecifications.add(spec);
                toolExecutors.put(spec.name(), new DefaultToolExecutor(wrappers, m));
                if (m.isAnnotationPresent(RequireApproval.class)) {
                    toolsRequiringApproval.add(spec.name());
                }
            }
        }

        log.info("[工具路由] 注册 {} 个工具,其中 {} 个需授权: {}",
                toolSpecifications.size(),
                toolsRequiringApproval.size(),
                toolsRequiringApproval);
    }

    /**
     * 获取所有工具规格(供 Agent 注入到 ChatRequestParameters)
     */
    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    /**
     * 获取需授权工具名集合
     */
    public Set<String> getToolsRequiringApproval() {
        return toolsRequiringApproval;
    }

    /**
     * 执行工具调用(统一入口)
     *
     * <p>Agent 的 toolsNode 只能调此方法,不能越级直接调用 LocalToolWrappers 或 capability Service。
     * 内部统一记录 Token 用量。</p>
     *
     * @param request LLM 生成的工具调用请求
     * @return 工具执行结果
     * @throws IllegalStateException 未找到工具执行器
     */
    public String execute(ToolExecutionRequest request) {
        ToolExecutor executor = toolExecutors.get(request.name());
        if (executor == null) {
            throw new IllegalStateException("未找到工具执行器: " + request.name());
        }
        log.info("[工具路由] 执行工具: {}", request.name());
        String result = executor.execute(request, null);
        // 统一记录 Token 用量
        TokenUsageTracker.recordToolCall(
                request.name(),
                request.arguments() == null ? "" : request.arguments(),
                result);
        return result;
    }
}
