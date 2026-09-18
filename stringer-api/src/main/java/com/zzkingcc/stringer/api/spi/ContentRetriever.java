package com.zzkingcc.stringer.api.spi;

/**
 * 内容检索 SPI — 给定用户问题,返回最相关的文档片段
 *
 * @author zzkingcc
 */
public interface ContentRetriever {

    /**
     * 本 Retriever 的唯一名称
     *
     * @return 名称
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
