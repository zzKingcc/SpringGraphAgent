package com.zxc.stringer.core.spi;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 外部工具提供商 SPI
 *
 * <p>扩展方实现此接口,在 META-INF/services/com.zxc.stringer.core.spi.ToolProvider 注册后,
 * Stringer 启动时通过 ServiceLoader 自动发现并加载。</p>
 *
 * <p>用于注册自定义工具(如 HTTP 外部工具、Python 服务、数据库查询等),
 * 被 ToolRouter 统一路由调用。</p>
 *
 * <p>当前为接口占位,Phase 2 实现具体加载机制。</p>
 */
public interface ToolProvider {

    /**
     * 返回本 Provider 能提供的所有工具定义
     */
    List<ToolSpecification> listDefinitions();

    /**
     * 判断本 Provider 是否能执行指定工具
     */
    default boolean canExecute(String toolName) {
        return listDefinitions().stream().anyMatch(t -> t.name().equals(toolName));
    }

    /**
     * 执行工具
     *
     * @param toolName 工具名称
     * @param argsJson 参数 JSON 字符串
     * @return 工具执行结果
     */
    String execute(String toolName, String argsJson);

    /**
     * 优先级,数值越小优先级越高
     */
    default int priority() { return 100; }
}
