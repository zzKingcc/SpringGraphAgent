package com.zxcSpringAI.common.spi;

/**
 * 内容检索 SPI — 给定用户问题,返回最相关的文档片段
 *
 * <p>由 RagService(capability 层)在需要知识库检索时调用。
 * CompositeContentRetriever 聚合多个实现,按权重分数融合后去重返回 TopN。</p>
 *
 * <p>默认实现:
 * <ul>
 *   <li>NativeScriptScoreContentRetriever(infrastructure.retriever) — ES 向量检索</li>
 *   <li>KeywordMatchContentRetriever(infrastructure.retriever) — ES BM25 关键词检索</li>
 * </ul>
 *
 * <p>可扩展为 Milvus/pgvector/FAISS 等向量库,或接入重排模型等策略。</p>
 *
 * <p>当前为接口占位,Phase 2 统一抽象后替换 LangChain4j 的 ContentRetriever。</p>
 */
public interface ContentRetriever {

    /**
     * 本 Retriever 的唯一名称(用于检索融合时区分来源)
     *
     * @return 名称,如 "es_vector" / "milvus" / "bm25_keyword"
     */
    String name();

    /**
     * 执行检索
     *
     * @param query 用户问题
     * @param topN  期望返回的最大结果数
     * @return 检索到的文档列表(JSON 字符串,每条含 title/content/score)
     */
    String retrieve(String query, int topN);

    /**
     * 本 Retriever 在混合检索中的权重
     *
     * @return 权重值,默认 1.0
     */
    default double weight() { return 1.0; }
}
