package com.zxc.stringer.core.spi;

/**
 * 检查点存储 SPI
 *
 * <p>工作流中断(人工审批/暂停)时,把当前图状态 + 消息栈 + Tool 执行中间结果存起来,
 * 恢复时原样读回继续跑。</p>
 *
 * <p>默认实现:RedisCheckpointSaver(infrastructure.memory)。
 * 可扩展为 JDBC/MongoDB/ZooKeeper 等持久化介质。</p>
 *
 * <p>当前为接口占位,Phase 2 统一抽象后替换 LangGraph4j 的 BaseCheckpointSaver。</p>
 */
public interface CheckpointSaver {

    /**
     * 存储检查点(覆盖写)
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
     * 删除 threadId 下全部检查点(工作流完成/用户停止时调用)
     *
     * @param threadId 会话 ID
     */
    void release(String threadId);
}
