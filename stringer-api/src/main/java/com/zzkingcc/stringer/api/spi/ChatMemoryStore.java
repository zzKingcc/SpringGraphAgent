package com.zzkingcc.stringer.api.spi;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 会话级聊天记忆存储 SPI
 *
 * @author zzkingcc
 */
public interface ChatMemoryStore {

    /**
     * 按序获取 chatId 下所有消息
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
     * 删除全部
     *
     * @param chatId 会话 ID
     */
    void deleteMessages(Object chatId);
}
