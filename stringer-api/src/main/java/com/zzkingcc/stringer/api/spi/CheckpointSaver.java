package com.zzkingcc.stringer.api.spi;

/**
 * 检查点存储 SPI
 *
 * @author zzkingcc
 */
public interface CheckpointSaver {

    /**
     * 存储检查点
     *
     * @param threadId   会话/工作流实例唯一 ID
     * @param checkpoint 序列化后的检查点数据
     */
    void save(String threadId, String checkpoint);

    /**
     * 读取最新一次检查点
     *
     * @param threadId 会话 ID
     * @return 检查点数据,不存在则返回 null
     */
    String loadLatest(String threadId);

    /**
     * 删除 threadId 下全部检查点
     *
     * @param threadId 会话 ID
     */
    void release(String threadId);
}
