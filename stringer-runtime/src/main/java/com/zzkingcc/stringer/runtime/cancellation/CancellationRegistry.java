package com.zzkingcc.stringer.runtime.cancellation;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务取消信号中心
 *
 * @author zzkingcc
 */
@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> flags = new ConcurrentHashMap<>();

    /**
     * 请求停止指定会话的任务
     *
     * @param sessionId 会话 ID
     * @return true=本次设置成功(之前未停止); false=该会话已处于停止状态(幂等)
     */
    public boolean requestStop(String sessionId) {
        return flags.computeIfAbsent(sessionId, k -> new AtomicBoolean(false))
                .compareAndSet(false, true);
    }

    /**
     * 检查会话是否已被请求停止
     *
     * @param sessionId 会话 ID
     * @return true=已被请求停止
     */
    public boolean isCancelled(String sessionId) {
        AtomicBoolean flag = flags.get(sessionId);
        return flag != null && flag.get();
    }

    /**
     * 清除会话的停止标志
     *
     * @param sessionId 会话 ID
     */
    public void clear(String sessionId) {
        flags.remove(sessionId);
    }

    /**
     * 执行中标记集合（同会话串行的载体）。
     */
    private final java.util.Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * 尝试把会话标记为"执行中"。
     *
     * @return true=获取成功，本次可以执行；false=已有在跑的一轮，应拒绝本次请求
     */
    public boolean tryMarkRunning(String sessionId) {
        return running.add(sessionId);
    }

    /** 释放"执行中"标记；只在成功获取标记的那一轮的 finally 中调用 */
    public void unmarkRunning(String sessionId) {
        running.remove(sessionId);
    }

    /** 当前正在执行的会话数（供管控台指标读取；进程内数值，不跨实例聚合） */
    public int runningCount() {
        return running.size();
    }
}
