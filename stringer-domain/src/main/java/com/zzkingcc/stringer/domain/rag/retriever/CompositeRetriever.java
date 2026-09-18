package com.zzkingcc.stringer.domain.rag.retriever;

import com.zzkingcc.stringer.domain.rag.fusion.FusionConfig;
import com.zzkingcc.stringer.domain.rag.model.RetrievalScoreKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 混合检索器
 *
 * @author zzkingcc
 */
public class CompositeRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(CompositeRetriever.class);

    private final ContentRetriever vectorRetriever;
    private final ContentRetriever keywordRetriever;
    private final FusionConfig fusion;
    /** 并行召回线程池;null 表示串行执行 */
    private final Executor executor;
    /** 单路召回超时(毫秒);&lt;=0 表示不限时 */
    private final long timeoutMs;

    public CompositeRetriever(ContentRetriever vectorRetriever,
                              ContentRetriever keywordRetriever) {
        this(vectorRetriever, keywordRetriever, FusionConfig.defaults(), null, 0L);
    }

    public CompositeRetriever(ContentRetriever vectorRetriever,
                              ContentRetriever keywordRetriever,
                              FusionConfig fusion,
                              Executor executor,
                              long timeoutMs) {
        this.vectorRetriever = vectorRetriever;
        this.keywordRetriever = keywordRetriever;
        this.fusion = fusion == null ? FusionConfig.defaults() : fusion;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public List<Content> retrieve(Query query) {
        long start = System.currentTimeMillis();

        ChannelResult vector;
        ChannelResult keyword;

        if (executor == null) {
            vector = retrieveSafely(vectorRetriever, query, "向量");
            keyword = retrieveSafely(keywordRetriever, query, "关键词");
        } else {
            CompletableFuture<List<Content>> vectorFuture =
                    CompletableFuture.supplyAsync(() -> vectorRetriever.retrieve(query), executor);
            CompletableFuture<List<Content>> keywordFuture =
                    CompletableFuture.supplyAsync(() -> keywordRetriever.retrieve(query), executor);
            vector = await(vectorFuture, "向量");
            keyword = await(keywordFuture, "关键词");
        }

        if (vector.failed() && keyword.failed()) {
            // 注意！两路都失败不一定没有相关内容：这里若返回空列表，"检索链路坏了"就和"真没命中"
            // 长得一模一样——调用方会换个问法反复试，而不是去查配置。
            throw new IllegalStateException("向量检索与关键词检索均失败，无法判断是否存在相关内容");
        }
        List<Content> vectorResults = vector.contents();
        List<Content> keywordResults = keyword.contents();

        // 合并去重 + 记录分数来源
        Map<String, ScoreEntry> scoreMap = new LinkedHashMap<>();
        String queryText = query.text();

        for (Content c : vectorResults) {
            String hash = hashContent(c);
            double score = extractScore(c);
            scoreMap.computeIfAbsent(hash, k -> new ScoreEntry(c, queryText)).vectorScore = score;
        }

        int keywordAdded = 0;
        for (Content c : keywordResults) {
            String hash = hashContent(c);
            double score = extractScore(c);
            ScoreEntry exist = scoreMap.get(hash);
            if (exist != null) {
                exist.keywordScore = score;
            } else {
                ScoreEntry entry = new ScoreEntry(c, queryText);
                entry.keywordScore = score;
                scoreMap.put(hash, entry);
                keywordAdded++;
            }
        }

        if (scoreMap.isEmpty()) {
            log.info("[组合检索] 无命中结果,耗时 {}ms", System.currentTimeMillis() - start);
            return new ArrayList<>();
        }

        List<ScoreEntry> entries = new ArrayList<>(scoreMap.values());
        normalize(entries);
        computeFusedScores(entries);

        entries.sort(Comparator.comparingDouble(e -> -e.fusedScore));
        int topN = Math.min(Math.max(fusion.topN(), 1), entries.size());

        List<Content> result = IntStream.range(0, topN)
                .mapToObj(i -> withScores(entries.get(i), i + 1))
                .collect(Collectors.toList());

        log.info("[组合检索] 向量命中{}条,关键词命中{}条(新增{}条),去重后{}条,融合重排Top{},耗时{}ms",
                vectorResults.size(), keywordResults.size(), keywordAdded,
                scoreMap.size(), topN, System.currentTimeMillis() - start);

        return result;
    }

    /**
     * 单路召回结果
     */
    private record ChannelResult(List<Content> contents, boolean failed) {

        static ChannelResult ok(List<Content> contents) {
            return new ChannelResult(contents == null ? List.of() : contents, false);
        }

        static ChannelResult failure() {
            return new ChannelResult(List.of(), true);
        }
    }

    /**
     * 串行模式下的安全召回:单路异常不影响另一路
     */
    private ChannelResult retrieveSafely(ContentRetriever retriever, Query query, String channel) {
        try {
            return ChannelResult.ok(retriever.retrieve(query));
        } catch (Exception e) {
            log.warn("[组合检索] {}检索异常,本次降级为仅用另一路结果: {}", channel, e.getMessage());
            return ChannelResult.failure();
        }
    }

    /**
     * 并行模式下等待单路结果:超时或异常都记为"该路失败",并取消该路任务
     */
    private ChannelResult await(CompletableFuture<List<Content>> future, String channel) {
        try {
            // TMD,timeoutMs 小于等于 0 时退化成无限等待 单路 ES 卡死会把整轮检索
            // 现在一起拖住，默认未配置时取 5s 兜底值。
            long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : 5000L;
            List<Content> out = future.get(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
            return ChannelResult.ok(out);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[组合检索] {}检索失败(超时或异常),本次降级为仅用另一路结果: {}", channel, e.getMessage());
            return ChannelResult.failure();
        }
    }

    /**
     * 对向量分数和关键词分数分别做 min-max 归一化
     */
    private void normalize(List<ScoreEntry> entries) {
        normalizeChannel(entries, true);
        normalizeChannel(entries, false);
    }

    private void normalizeChannel(List<ScoreEntry> entries, boolean vector) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ScoreEntry e : entries) {
            Double v = vector ? e.vectorScore : e.keywordScore;
            if (v != null) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        for (ScoreEntry e : entries) {
            Double v = vector ? e.vectorScore : e.keywordScore;
            if (v == null) {
                continue;
            }
            double norm = (max == min) ? 1.0 : (v - min) / (max - min);
            if (vector) {
                e.normVectorScore = norm;
            } else {
                e.normKeywordScore = norm;
            }
        }
    }

    /**
     * 计算融合分数:加权求和 + boost
     */
    private void computeFusedScores(List<ScoreEntry> entries) {
        for (ScoreEntry e : entries) {
            double fused = fusion.vectorWeight() * (e.normVectorScore != null ? e.normVectorScore : 0.0)
                    + fusion.keywordWeight() * (e.normKeywordScore != null ? e.normKeywordScore : 0.0);

            String title = e.content.textSegment().metadata().getString("section_title");
            if (title != null && !title.isBlank() && containsAnyKeyword(title, e.queryText)) {
                fused += fusion.titleBoost();
            }

            String fileName = e.content.textSegment().metadata().getString("file_name");
            if (fileName != null && !fileName.isBlank() && containsAnyKeyword(fileName, e.queryText)) {
                fused += fusion.fileNameBoost();
            }

            e.fusedScore = fused;
        }
    }

    /**
     * 把分项分、融合分、名次、命中通道回写到 metadata
     */
    private Content withScores(ScoreEntry e, int rank) {
        TextSegment source = e.content.textSegment();
        Map<String, Object> meta = new LinkedHashMap<>(source.metadata().toMap());

        if (e.vectorScore != null) {
            meta.put(RetrievalScoreKeys.VECTOR_SCORE, e.vectorScore);
        }
        if (e.keywordScore != null) {
            meta.put(RetrievalScoreKeys.KEYWORD_SCORE, e.keywordScore);
        }
        if (e.normVectorScore != null) {
            meta.put(RetrievalScoreKeys.NORM_VECTOR_SCORE, e.normVectorScore);
        }
        if (e.normKeywordScore != null) {
            meta.put(RetrievalScoreKeys.NORM_KEYWORD_SCORE, e.normKeywordScore);
        }
        meta.put(RetrievalScoreKeys.FUSED_SCORE, e.fusedScore);
        meta.put(RetrievalScoreKeys.FUSION_RANK, rank);
        meta.put(RetrievalScoreKeys.MATCH_CHANNEL, channel(e));

        return Content.from(TextSegment.from(source.text(), Metadata.from(meta)));
    }

    private String channel(ScoreEntry e) {
        if (e.vectorScore != null && e.keywordScore != null) {
            return "both";
        }
        return e.vectorScore != null ? "vector" : "keyword";
    }

    /**
     * 从 Content metadata 中提取 ES 检索分数
     */
    private double extractScore(Content content) {
        try {
            return content.textSegment().metadata().getFloat(RetrievalScoreKeys.RAW_SCORE);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 判断文本中是否包含查询词的任意关键词（中文按单字/词匹配，英文按空格分词）
     */
    private boolean containsAnyKeyword(String text, String query) {
        if (text == null || query == null) return false;
        String lowerText = text.toLowerCase();
        Set<String> keywords = extractKeywords(query);
        for (String kw : keywords) {
            if (kw.length() >= 2 && lowerText.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从查询文本中提取关键词集合
     */
    private Set<String> extractKeywords(String query) {
        return Arrays.stream(query.toLowerCase()
                        .split("[\\s，。！？、；：\"'（）《》\\[\\]【】,.!?;:()]+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    //去重辅助
    private String hashContent(Content content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.textSegment().text().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return content.textSegment().text();
        }
    }

    /**
     * 单条检索结果的分数记录,用于融合计算与回写
     */
    private static class ScoreEntry {
        final Content content;
        final String queryText;
        Double vectorScore;
        Double keywordScore;
        Double normVectorScore;
        Double normKeywordScore;
        double fusedScore;

        ScoreEntry(Content content, String queryText) {
            this.content = content;
            this.queryText = queryText;
        }
    }
}
