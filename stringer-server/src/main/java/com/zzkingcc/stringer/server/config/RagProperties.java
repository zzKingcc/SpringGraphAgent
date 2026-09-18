package com.zzkingcc.stringer.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 向量索引与知识库上传配置（平台自有，统一前缀 {@code stringer.rag}）。
 * @author zzkingcc
 */
@Data
@ConfigurationProperties(prefix = "stringer.rag")
public class RagProperties {

    /** 索引名；必须与 yaml 的 {@code stringer.rag.index-name} 一致，否则 yaml 未显式配置时会落到另一个索引上 */
    private String indexName = "stringer_knowledge";

    /**
     * 启动建索引时是否先删除旧索引。
     */
    private boolean deleteOnStartup = false;

    /**
     * 是否在启动阶段建索引（{@code false} 则完全跳过）。
     */
    private boolean bootstrapEnabled = true;

    /**
     * 启动建索引是否严格（fail-fast）。
     */
    private boolean bootstrapStrict = false;

    /**
     * 单个上传文件的大小上限（默认 10MB）。
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /**
     * 允许上传的扩展名白名单（小写）。
     */
    private List<String> allowedExtensions = List.of("md", "txt", "markdown", "text");

    /**
     * 排队等待导入串行锁的最长时间（秒，默认 60）
     */
    private long ingestLockWaitSeconds = 60;

    /** 知识库导入线程池线程数 */
    private int ingestPoolSize = 2;

    /**
     * 导入任务队列容量。
     */
    private int ingestQueueCapacity = 16;
}
