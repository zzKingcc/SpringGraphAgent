package com.zzkingcc.stringer.api.spi;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 外部工具提供商 SPI
 *
 * @author zzkingcc
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
