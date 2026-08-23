package com.zxcSpringAI.common.spi;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 会话级聊天记忆存储 SPI
 *
 * <p>按 chatId(sessionId)隔离,每次 add 后自动应用双约束(条数 + tokens)淘汰。</p>
 *
 * <p>默认实现:RedisChatMemoryStore(infrastructure.memory)。
 * 可扩展为 JDBC/MongoDB 等存储后端。</p>
 *
 * <p>当前为接口占位,Phase 2 统一抽象后剥离 LangChain4j 依赖。</p>
 */
public interface ChatMemoryStore {

    /**
     * 获取 chatId 下所有消息(按写入顺序正序)
     *
     * @param chatId 会话 ID
     * @return 消息列表
     */
    List<ChatMessage> getMessages(Object chatId);

    /**
     * 全量覆盖写入
     *
     * @param chatId   会话 ID
     * @param messages 消息列表
     */
    void setMessages(Object chatId, List<ChatMessage> messages);

    /**
     * 删除全部(会话永久结束/用户登出)
     *
     * @param chatId 会话 ID
     */
    void deleteMessages(Object chatId);
}
