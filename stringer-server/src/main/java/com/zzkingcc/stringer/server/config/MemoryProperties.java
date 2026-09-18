package com.zzkingcc.stringer.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 会话记忆配置
 *
 * <p>前缀 {@code stringer.memory}。阈值统一外置在 yaml，便于调整窗口而无需重新编译。</p>
 * @author zzkingcc
 */
@Data
@ConfigurationProperties(prefix = "stringer.memory")
public class MemoryProperties {

    /** 单会话保留的消息条数上限(约等于 maxMessages/2 轮问答) */
    private int maxMessages = 100;

    /** 单会话消息累计 Token 估算上限 */
    private int maxTokens = 30000;

    /** 会话记忆过期时间;默认与检查点同量级(更长一档),避免"断点已过期而提问仍留在记忆里" */
    private Duration ttl = Duration.ofHours(72);

    /** 图检查点保留时长;中断后未 resume 的会话靠此兜底清理 */
    private Duration checkpointTtl = Duration.ofHours(24);
}
