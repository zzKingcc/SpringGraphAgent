package com.zzkingcc.stringer.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具治理策略注解
 *
 * @author zzkingcc
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ToolPolicy {

    /** 二次确认策略；默认表示可直接调用 */
    Approval approval() default @Approval;

    /** 策略注解 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @Documented
    @interface Approval {

        /** 确认模式；默认直接执行 */
        Mode mode() default Mode.NONE;

        /** 条件表达式，对工具参数求值做范围判断 */
        String condition() default "";

        /** 中断时展示给审批人的原因，说明为什么需要审批 */
        String reason() default "";

        /** 谁有权批准（权限码或角色），由接入方在中断事件中据此选人 */
        String[] approverRoles() default {"tenant:admin"};

        /** 审批超时时间（秒） */
        int timeoutSeconds() default 300;

        /** 超时后的处理方式 */
        OnTimeout onTimeout() default OnTimeout.REJECT;

        /** 中断事件里展示哪些参数 */
        String[] payloadFields() default {};

        /**
         * 确认模式
         */
        enum Mode {
            /** 不需要人工确认（默认） */
            NONE,
            /** 每次调用都要确认 —— 适合退款、发消息、写库等有副作用的工具 */
            ALWAYS,
            /** 仅当 {@link #condition()} 为真时确认 —— 适合"金额超过阈值才审批"这类业务规则 */
            CONDITIONAL,
            /** 同一会话同一工具确认一次后免确认 */
            ONCE_PER_SESSION
        }

        /**
         * 超时处理方式
         */
        enum OnTimeout {
            /** 视为拒绝 */
            REJECT,
            /** 终止本轮对话 */
            ABORT
        }
    }
}
