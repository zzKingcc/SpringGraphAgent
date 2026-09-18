package com.zzkingcc.stringer.domain.capability.knowledge;

import com.zzkingcc.stringer.domain.rag.model.RetrievalScoreKeys;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索能力服务
 *
 * @author zzkingcc
 */
@Component
public class KnowledgeSearchService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchService.class);

    private final ContentRetriever contentRetriever;

    public KnowledgeSearchService(@Qualifier("myContentRetriever") ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
    }

    /**
     * 从知识库检索相关内容
     *
     * @param keyword 检索关键词或问题
     * @return 检索到的知识库内容片段(拼接为纯文本),无结果时返回提示
     */
    public String searchKnowledgeBase(String keyword) {
        log.info("[知识库检索] 关键词：{}", keyword);
        try {
            List<Content> contents = contentRetriever.retrieve(new Query(keyword));
            if (contents == null || contents.isEmpty()) {
                log.info("[知识库检索] 未检索到相关内容");
                return "未检索到相关内容";
            }
            int limit = Math.min(contents.size(), 5);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                Content c = contents.get(i);
                sb.append("【片段").append(i + 1);
                Double fused = readScore(c, RetrievalScoreKeys.FUSED_SCORE);
                if (fused != null) {
                    sb.append("｜相关度 ").append(String.format("%.2f", fused));
                }
                sb.append("】\n");
                String fileName = c.textSegment().metadata().getString("file_name");
                String sectionTitle = c.textSegment().metadata().getString("section_title");
                if (fileName != null) {
                    sb.append("来源：").append(fileName);
                    if (sectionTitle != null && !sectionTitle.isBlank()) {
                        sb.append(" > ").append(sectionTitle);
                    }
                    sb.append("\n");
                }
                sb.append(c.textSegment().text()).append("\n\n");
            }
            String result = sb.toString();
            log.info("[知识库检索] 检索到 {} 条相关内容，返回前 {} 条", contents.size(), limit);
            return result;
        } catch (Exception e) {
            // 走到这里说明检索链路本身有问题（上面返回空列表那条分支才是"没命中"）。
            // 可能是检索引擎未配置（该去管控台补），也可能是暂时故障——域层拿不到区分二者的错误码
            log.error("[知识库检索] 检索异常：{}", e.getMessage(), e);
            return "知识库检索服务当前不可用：可能是检索服务尚未配置，也可能是暂时故障。"
                    + "请确认知识库配置后再试。";
        }
    }

    /** 读取融合阶段回写的分数 */
    private Double readScore(Content content, String key) {
        try {
            return content.textSegment().metadata().getDouble(key);
        } catch (Exception e) {
            return null;
        }
    }
}
