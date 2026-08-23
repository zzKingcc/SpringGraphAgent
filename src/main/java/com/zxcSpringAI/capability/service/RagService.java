package com.zxcSpringAI.capability.service;

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
 * <p>纯业务逻辑,依赖 LangChain4j RAG 框架的 ContentRetriever 接口。
 * 向量检索 Top15 + 关键词检索 Top5 混合检索 + 分数融合重排序,最终返回 Top5。</p>
 *
 * <p>由 agent 层的 LocalToolWrappers 用 @Tool 注解包装后暴露给 LLM。</p>
 */
@Component
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ContentRetriever contentRetriever;

    public RagService(@Qualifier("myContentRetriever") ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
    }

    /**
     * 从知识库检索相关内容
     *
     * <p>综合召回取 Top5,避免过长上下文挤占 memory 窗口。
     * 返回纯文本格式,附带来源元数据。</p>
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
                sb.append("【片段").append(i + 1).append("】\n");
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
            log.error("[知识库检索] 检索异常：{}", e.getMessage(), e);
            return "知识库检索服务暂时不可用，请稍后重试";
        }
    }
}
