package com.zzkingcc.stringer.api.spi;

/**
 * 工具执行拦截器 SPI
 *
 * @author zzkingcc
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
