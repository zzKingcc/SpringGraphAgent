package com.zzkingcc.stringer.api.tool;

import com.zzkingcc.stringer.api.annotation.StringerTool;

import java.util.List;

/**
 * 工具描述符 —— 平台侧对一个工具的全部认知
 *
 * @author zzkingcc
 * @param name        工具名（全局唯一）
 * @param description 给 LLM 的用途说明
 * @param category    分组
 * @param version     版本
 * @param sideEffect  副作用等级
 * @param idempotent  是否幂等（决定能否自动重试）
 * @param toModel     结果是否回填 LLM
 * @param params      参数列表（结构 + 语义，用于校验与生成 schema）
 * @param profiles    本工具所属的域（留空 = 所有域可见）
 * @param approval    二次确认策略
 * @param source      来源标识，如 {@code com.foo.Bean#method}，用于排障与审计
 */
public record ToolDescriptor(
        String name,
        String description,
        String category,
        String version,
        StringerTool.SideEffect sideEffect,
        boolean idempotent,
        boolean toModel,
        List<Param> params,
        List<String> profiles,
        Approval approval,
        String source) {

    /**
     * 本工具是否对指定域可见。
     */
    public boolean visibleIn(String profile) {
        if (profiles == null || profiles.isEmpty()) {
            return true;
        }
        return profile != null && !profile.isBlank() && profiles.contains(profile);
    }

    /**
     * 参数描述
     *
     * @param name        参数名
     * @param type        类型名：string / integer / number / boolean / enum / array
     * @param description 语义说明
     * @param required    是否必填
     * @param allowValues 枚举白名单（{@code type=enum} 时有效）
     * @param example     示例值
     * @param sensitive   是否敏感（日志 / 事件 / 审批 payload 中脱敏）
     */
    public record Param(
            String name,
            String type,
            String description,
            boolean required,
            List<String> allowValues,
            String example,
            boolean sensitive) {
    }

    /**
     * 二次确认策略（与注解 {@code @ToolPolicy.Approval} 对应）
     *
     * @param mode           NONE / ALWAYS / CONDITIONAL / ONCE_PER_SESSION
     * @param condition      条件表达式（CONDITIONAL 模式）
     * @param reason         展示给审批人的原因
     * @param approverRoles  有批准权的角色标识，由<b>宿主</b>判定——平台不预定义角色，也不做校验
     * @param timeoutSeconds 审批超时（秒）
     */
    public record Approval(
            String mode,
            String condition,
            String reason,
            List<String> approverRoles,
            int timeoutSeconds) {

        /** 不需要确认 */
        public static Approval none() {
            return new Approval("NONE", "", "", List.of(), 0);
        }

        /** 是否需要人工确认 */
        public boolean required() {
            return mode != null && !"NONE".equalsIgnoreCase(mode);
        }
    }

    /** 是否需要人工确认 */
    public boolean requiresApproval() {
        return approval != null && approval.required();
    }
}
