package com.zxcSpringAI.common.spi;

/**
 * 工具执行拦截器 SPI — 类似 Spring MethodInterceptor,作用范围是所有 @Tool 方法
 *
 * <p>多个拦截器按 priority() 顺序组成责任链。
 * 用于审计日志、参数脱敏、速率限流、故障注入等横切逻辑。</p>
 *
 * <p>当前为接口占位,Phase 2 实现责任链加载机制。</p>
 */
public interface ToolExecutionInterceptor {

    /**
     * 工具执行前调用
     *
     * @param toolName  工具名称
     * @param argsJson  参数 JSON
     * @return 修改后的参数 JSON,一般返回入参本身
     */
    default String beforeExecute(String toolName, String argsJson) {
        return argsJson;
    }

    /**
     * 工具正常执行成功后调用
     *
     * @param toolName  工具名称
     * @param argsJson  原始参数
     * @param result    执行结果
     * @return 修改后的结果
     */
    default String afterSuccess(String toolName, String argsJson, String result) {
        return result;
    }

    /**
     * 工具抛出异常后调用
     *
     * @param toolName 工具名称
     * @param argsJson 原始参数
     * @param ex       异常
     * @return 降级后的结果 JSON;如果不能恢复,重新抛出异常
     */
    default String afterFailure(String toolName, String argsJson, Exception ex) throws Exception {
        throw ex;
    }

    /**
     * 优先级,数值越小越先执行
     */
    default int priority() { return 100; }
}
