package com.zzkingcc.stringer.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述工具参数的语义注解
 *
 * @author zzkingcc
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Documented
public @interface ToolParam {

    /** 参数名；留空则取形参名 */
    String name() default "";

    /** 参数说明：写清业务含义、单位、格式、边界，例如"退款金额，单位：元，必须 ≤ 订单实付金额" */
    String description();

    /** 是否必填；{@code Optional} 或包装类型可自行判定为可选 */
    boolean required() default true;

    /** 示例值，帮助模型理解格式 */
    String example() default "";

    /** 枚举白名单：能用它表达的约束不要写成自然语言 */
    String[] allowValues() default {};

    /** 标记敏感参数：日志、事件与审批 payload 中需脱敏。*/
    boolean sensitive() default false;
}
