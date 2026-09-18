package com.zzkingcc.stringer.server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zzkingcc.stringer.domain.rag.fusion.FusionConfig;
import com.zzkingcc.stringer.domain.rag.retriever.CompositeRetriever;
import com.zzkingcc.stringer.infrastructure.elasticsearch.retriever.KeywordMatchContentRetriever;
import com.zzkingcc.stringer.infrastructure.elasticsearch.retriever.NativeScriptScoreContentRetriever;
import com.zzkingcc.stringer.infrastructure.ingestion.DocumentIngestor;
import com.zzkingcc.stringer.infrastructure.elasticsearch.EsIndexManager;
import com.zzkingcc.stringer.server.settings.InfraSettingsHolder;
import com.zzkingcc.stringer.server.settings.LlmModelHolder;
import com.zzkingcc.stringer.server.settings.LlmSettings;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationScript;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 配置类
 * @author zzkingcc
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({RagProperties.class, RetrievalProperties.class})
public class RetrievalConfiguration {

    /**
     * 混合检索专用线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService retrievalExecutor(RetrievalProperties retrievalProps) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicLong seq = new AtomicLong();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "stringer-retrieval-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                retrievalProps.getCorePoolSize(),
                retrievalProps.getMaxPoolSize(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(retrievalProps.getQueueCapacity()),
                factory);

        log.info("[检索配置] 检索线程池 core={}, max={}, queue={}, parallel={}, timeout={}ms",
                retrievalProps.getCorePoolSize(), retrievalProps.getMaxPoolSize(),
                retrievalProps.getQueueCapacity(), retrievalProps.isParallel(),
                retrievalProps.getTimeoutMs());
        return executor;
    }

    /**
     * 组合检索器（向量检索 Top15 + 关键词检索 Top5 → 融合重排 → 分数回写 metadata）
     */
    @Bean
    @Lazy
    public ContentRetriever myContentRetriever(
            @Qualifier("stringerElasticsearchClient") ElasticsearchClient esClient,
            RagProperties ragEsProps,
            RetrievalProperties retrievalProps,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            @Qualifier("retrievalExecutor") ExecutorService retrievalExecutor) {

        // 向量检索：余弦相似度，Top15，minScore=0.2
        NativeScriptScoreContentRetriever vectorRetriever = new NativeScriptScoreContentRetriever(
                esClient,
                ragEsProps.getIndexName(),
                embeddingModel,
                15,
                0.2
        );

        // 关键词检索：BM25 match，Top5
        KeywordMatchContentRetriever keywordRetriever = new KeywordMatchContentRetriever(
                esClient,
                ragEsProps.getIndexName(),
                5
        );

        FusionConfig fusion = new FusionConfig(
                retrievalProps.getVectorWeight(),
                retrievalProps.getKeywordWeight(),
                retrievalProps.getTitleBoost(),
                retrievalProps.getFileNameBoost(),
                retrievalProps.getTopN());

        // parallel=false 时传 null,检索器退化为串行召回
        Executor executor = retrievalProps.isParallel() ? retrievalExecutor : null;

        return new CompositeRetriever(vectorRetriever, keywordRetriever, fusion,
                executor, retrievalProps.getTimeoutMs());
    }

    /**
     * 向量存储（懒加载，仅构建；灌库由启动钩子 {@link #knowledgeIndexBootstrap} 触发）。
     */
    @Bean
    @Lazy
    public EmbeddingStore myEmbeddingStore(
            @Qualifier("stringerElasticsearchClient") ElasticsearchClient esClient,
            RagProperties ragEsProps) {
        log.info("[检索配置] 懒构建向量存储 indexName={}", ragEsProps.getIndexName());
        return ElasticsearchEmbeddingStore.builder()
                .client(esClient)
                .indexName(ragEsProps.getIndexName())
                .configuration(ElasticsearchConfigurationScript.builder().build())
                .build();
    }

    /**
     * 显式重建知识库索引
     * @return 本次成功写入的文档数；&lt;=0 已按失败抛出
     */
    public void rebuildKnowledgeIndex(ElasticsearchClient esClient,
                                      EmbeddingModel embeddingModel,
                                      EmbeddingStore embeddingStore,
                                      RagProperties ragEsProps,
                                      int dimensions,
                                      boolean forceDelete) {
        String indexName = ragEsProps.getIndexName();
        // 0) 连通性预检：ES 与 embedding 任一不可达都直接失败，不进入后续流程
        assertElasticsearchReachable(esClient);
        assertEmbeddingReachable(embeddingModel);
        // 1) 诊断
        EsIndexManager.diagnoseElasticsearch(esClient, indexName);
        // 2) 按需删旧索引：手动重建强制删，启动期尊重 yaml 开关
        EsIndexManager.deleteIndexIfNeeded(esClient, indexName,
                forceDelete || ragEsProps.isDeleteOnStartup());
        // 3) 建带 IK 分词器的 mapping（维度来自配置声明或实测，与 embedding 模型一致）
        EsIndexManager.createIndexWithIkMapping(esClient, indexName, dimensions);
        // 4) 详细诊断输出
        EsIndexManager.writeAfterVerify(esClient, indexName);
        // 只建索引，不灌库：知识内容由部署方通过上传接口导入，平台不内置任何文档
    }

    /**
     * ES 连通性预检：ping 不通或抛异常都视为不可达。
     */
    private void assertElasticsearchReachable(ElasticsearchClient esClient) {
        try {
            Boolean ok = esClient.ping().value();
            if (ok == null || !ok) {
                throw new IllegalStateException("ES ping 返回 false");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Elasticsearch 不可达，启动中止："
                    + e.getMessage() + "（请检查 stringer.elasticsearch.host/port 与账号密码）", e);
        }
    }

    /**
     * embedding 连通性预检：真正调用一次向量接口，避免"配置有值但服务不可达"被后续流程吞掉。
     */
    private void assertEmbeddingReachable(EmbeddingModel embeddingModel) {
        try {
            embeddingModel.embedAll(List.of(TextSegment.from("连通性检查")));
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 模型不可达，启动中止："
                    + e.getMessage() + "（请检查管控台「模型设置」页的向量模型地址 / API Key / 模型名）", e);
        }
    }

    /**
     * 灌库后校验：索引文档数为 0 直接判定失败。
     */
    private void assertIndexNotEmpty(ElasticsearchClient esClient, String indexName) {
        try {
            long count = esClient.count(c -> c.index(indexName)).count();
            log.info("[检索配置] 灌库后索引文档数 = {}", count);
            if (count <= 0) {
                throw new IllegalStateException("索引[" + indexName + "]写入后文档数为 0，知识库未生效");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("灌库后校验失败，启动中止：" + e.getMessage(), e);
        }
    }

    /**
     * 启动完成前同步灌库钩子
     */
    @Bean
    public SmartInitializingSingleton knowledgeIndexBootstrap(
            @Qualifier("stringerElasticsearchClient") ElasticsearchClient esClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            @Qualifier("myEmbeddingStore") EmbeddingStore embeddingStore,
            RagProperties ragEsProps,
            LlmModelHolder modelHolder,
            InfraSettingsHolder infraHolder) {
        return () -> {
            if (!ragEsProps.isBootstrapEnabled()) {
                log.warn("[检索配置] stringer.rag.bootstrap-enabled=false，已跳过启动期建索引");
                return;
            }
            // 未配置是合法初始态（配置可在管控台运行时补齐），因此用 INFO 陈述事实、不告警：
            // 按项目日志原则，"还没配"够不上 WARN。真正的失败推迟到调用点，由未配置守卫报。
            if (!infraHolder.isEsConfigured()) {
                log.info("[检索配置] Elasticsearch 尚未配置，跳过启动期建索引"
                        + "（到管控台「存储配置」页填写并保存，再到「知识库」页触发一次重建）");
                return;
            }
            // 模型还没配（首次部署、尚未在管控台填写服务商）时同样跳过建索引，而不是抛异常阻断启动。
            // 理由同上：配置模型本身就需要先能进管控台。配好后再手动触发重建即可。
            if (!modelHolder.isEmbeddingConfigured()) {
                log.info("[检索配置] 向量模型尚未配置，跳过启动期建索引"
                        + "（到管控台「模型设置」页填写并保存，再到「知识库」页触发一次重建）");
                return;
            }
            log.info("[检索配置] 配置已就位，启动完成前同步执行知识库建索引（index={}）",
                    ragEsProps.getIndexName());
            try {
                // 维度取自"三处同源"的公共取值点：管控台声明优先，未声明则实测模型默认维度。
                // 不再写死 1536——换模型后写死值会静默建出维度错误的索引。
                int dimensions = modelHolder.effectiveEmbeddingDimension();
                LlmSettings effective = modelHolder.currentSettings();
                log.info("[检索配置] 索引向量维度 = {}（{}）", dimensions,
                        effective != null && effective.getEmbeddingDimensions() != null
                                ? "管控台声明值" : "用户未指定，实测所得模型默认维度");
                // 启动期只补不删：forceDelete=false，删不删由 stringer.rag.delete-on-startup 决定，
                // 否则每次重启都会退化成一次全量重灌
                rebuildKnowledgeIndex(esClient, embeddingModel, embeddingStore, ragEsProps, dimensions, false);
            } catch (Exception e) {
                if (ragEsProps.isBootstrapStrict()) {
                    // fail-fast：配置不到位 / 中间件不可达时直接阻断启动，避免"启动成功但检索全空"
                    throw new IllegalStateException("Stringer 启动失败：知识库初始化未通过 —— " + e.getMessage()
                            + "；如需先拉起服务排查，可设置 stringer.rag.bootstrap-strict=false", e);
                }
                log.error("[检索配置] 知识库初始化失败，但 bootstrap-strict=false，服务继续启动：{}",
                        e.getMessage(), e);
            }
        };
    }
}
