package com.zzkingcc.stringer.example.advice;

import com.zzkingcc.stringer.api.code.ErrorCode;
import com.zzkingcc.stringer.common.exception.BaseException;
import com.zzkingcc.stringer.common.exception.ChatMemoryException;
import com.zzkingcc.stringer.common.exception.KnowledgeBaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * 示例层全局异常处理器
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有异常统一以 <b>ERROR</b> 级别写入日志并输出到控制台（完整堆栈）；</li>
 *   <li>响应体统一携带 {@code code}（业务状态码）+ {@code codeName}（枚举名）+ {@code error} + {@code detail} + {@code timestamp}；</li>
 *   <li>业务异常直接复用 {@link ErrorCode}，不散落 magic number；</li>
 *   <li>{@link CancellationException} 为用户主动中断，属正常流程，仅 WARN 且不计入服务错误。</li>
 * </ul>
 * </p>
 * @author zzkingcc
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===================== 业务异常（携带 ErrorCode） =====================

    @ExceptionHandler(KnowledgeBaseException.class)
    public ResponseEntity<Map<String, Object>> handleKnowledgeBaseException(KnowledgeBaseException e) {
        log.error("[全局异常][KNOWLEDGE] code={}({}), detail={}",
                e.getCode(), e.getCodeName(), e.getMessage(), e);
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(ChatMemoryException.class)
    public ResponseEntity<Map<String, Object>> handleChatMemoryException(ChatMemoryException e) {
        log.error("[全局异常][CHAT_MEMORY] code={}({}), detail={}",
                e.getCode(), e.getCodeName(), e.getMessage(), e);
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleBaseException(BaseException e) {
        log.error("[全局异常][BUSINESS] code={}({}), detail={}",
                e.getCode(), e.getCodeName(), e.getMessage(), e);
        return build(e.getErrorCode(), e.getMessage());
    }

    // ===================== 参数校验异常 =====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorCode code = e.getMessage() != null && e.getMessage().contains("不安全")
                ? ErrorCode.INPUT_REJECTED : ErrorCode.INVALID_PARAMETER;
        log.error("[全局异常][PARAM] code={}({}), detail={}", code.getCode(), code.name(), e.getMessage(), e);
        return build(code, e.getMessage());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPathVariable(MissingPathVariableException e) {
        log.error("[全局异常][PARAM] 路径参数缺失: {}", e.getVariableName(), e);
        return build(ErrorCode.MISSING_REQUIRED_PARAMETER, e.getVariableName() + " 不能为空");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expect = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知";
        log.error("[全局异常][PARAM] 参数类型不匹配: name={}, expect={}", e.getName(), expect, e);
        return build(ErrorCode.TYPE_MISMATCH, "参数 " + e.getName() + " 期望类型：" + expect);
    }

    // ===================== 运行时 / 状态异常 =====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        ErrorCode code = e.getMessage() != null && e.getMessage().contains("工具名冲突")
                ? ErrorCode.TOOL_DUPLICATE : ErrorCode.SYSTEM_ERROR;
        log.error("[全局异常][STATE] code={}({}), detail={}", code.getCode(), code.name(), e.getMessage(), e);
        return build(code, e.getMessage());
    }

    // ===================== LLM / 外部依赖超时 =====================

    @ExceptionHandler(SocketTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleSocketTimeoutException(SocketTimeoutException e) {
        log.error("[全局异常][LLM] 大模型接口读取超时(Socket): {}", e.getMessage(), e);
        return build(ErrorCode.LLM_TIMEOUT, "大模型接口响应超时，请稍后重试");
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(TimeoutException e) {
        log.error("[全局异常][LLM] 大模型接口超时(Timeout): {}", e.getMessage(), e);
        return build(ErrorCode.LLM_TIMEOUT, "大模型接口响应超时，请稍后重试");
    }

    // ===================== 静态资源 =====================

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        String resourcePath = e.getResourcePath();
        // 浏览器自动请求的 favicon.ico 等资源缺失属正常现象，静默返回 404，避免污染日志
        if ("favicon.ico".equals(resourcePath)) {
            return build(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在：" + resourcePath);
        }
        log.error("[全局异常][RESOURCE] 资源未找到: {}", resourcePath, e);
        return build(ErrorCode.RESOURCE_NOT_FOUND, "请求的资源不存在：" + resourcePath);
    }

    // ===================== 用户主动中断（正常流程，不计入错误） =====================

    @ExceptionHandler(CancellationException.class)
    public ResponseEntity<Map<String, Object>> handleCancellationException(CancellationException e) {
        log.warn("[全局异常][CANCEL] 用户主动中断请求");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 499);
        body.put("codeName", "CLIENT_CANCELLED");
        body.put("error", "用户已中断请求");
        body.put("detail", e.getMessage());
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(499).body(body);
    }

    // ===================== 兜底 =====================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("[全局异常][UNEXPECTED] 未预期运行时异常：{}", e.getMessage(), e);
        return build(ErrorCode.UNEXPECTED_ERROR, "服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("[全局异常][UNEXPECTED] 未预期异常：{}", e.getMessage(), e);
        return build(ErrorCode.UNEXPECTED_ERROR, "服务暂时不可用，请稍后重试");
    }

    /**
     * 统一构建 JSON 响应体，HTTP 状态码取自 {@link ErrorCode#getHttpStatus()}
     */
    private ResponseEntity<Map<String, Object>> build(ErrorCode errorCode, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", errorCode.getCode());
        body.put("codeName", errorCode.name());
        body.put("error", errorCode.getMessage());
        body.put("detail", detail);
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}
