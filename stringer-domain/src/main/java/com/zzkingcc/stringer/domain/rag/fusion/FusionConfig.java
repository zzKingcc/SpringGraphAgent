package com.zzkingcc.stringer.domain.rag.fusion;

/**
 * 融合排序参数
 *
 * @author zzkingcc
 * @param vectorWeight   向量权重
 * @param keywordWeight  关键词权重
 * @param titleBoost     标题命中加分
 * @param fileNameBoost  文件名命中加分
 * @param topN           重排后返回条数
 */
public record FusionConfig(double vectorWeight,
                           double keywordWeight,
                           double titleBoost,
                           double fileNameBoost,
                           int topN) {

    /** 默认配置:向量 0.6 / 关键词 0.4,标题 +0.15,文件名 +0.10,返回 Top10 */
    public static FusionConfig defaults() {
        return new FusionConfig(0.6, 0.4, 0.15, 0.10, 10);
    }
}
