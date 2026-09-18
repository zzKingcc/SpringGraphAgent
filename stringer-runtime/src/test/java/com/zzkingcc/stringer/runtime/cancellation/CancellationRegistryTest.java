package com.zzkingcc.stringer.runtime.cancellation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同会话串行的载体：{@code Set.add} 的原子性即"独占获取"。
 *
 * <p>这条语义错了会同时坏两件事——并发请求没被挡住（记忆与断点是单份资源），
 * 或者被挡住之后标记没释放（会话永久锁死，只能重启）。</p>
 * @author zzkingcc
 */
class CancellationRegistryTest {

    @Test
    @DisplayName("同一会话只允许一轮在执行，释放后可再次进入")
    void onlyOneRunnerPerSession() {
        CancellationRegistry registry = new CancellationRegistry();

        assertTrue(registry.tryMarkRunning("s-1"));
        assertFalse(registry.tryMarkRunning("s-1"), "同一会话不应允许并发进入");
        registry.unmarkRunning("s-1");
        assertTrue(registry.tryMarkRunning("s-1"), "释放后应可再次进入");
    }

    @Test
    @DisplayName("不同会话互不影响")
    void sessionsAreIndependent() {
        CancellationRegistry registry = new CancellationRegistry();

        assertTrue(registry.tryMarkRunning("s-1"));
        assertTrue(registry.tryMarkRunning("s-2"));
        registry.unmarkRunning("s-1");

        assertEquals(1, registry.runningCount());
    }

    @Test
    @DisplayName("并发抢占时只有一个赢家")
    void concurrentAcquireHasSingleWinner() throws InterruptedException {
        CancellationRegistry registry = new CancellationRegistry();
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (registry.tryMarkRunning("s-1")) {
                    winners.incrementAndGet();
                }
                finished.countDown();
            }).start();
        }
        start.countDown();
        finished.await();

        assertEquals(1, winners.get(), "并发抢占只应有一个赢家");
        assertEquals(1, registry.runningCount());
    }

    @Test
    @DisplayName("停止标志与执行中标记互相独立")
    void stopFlagIsIndependentOfRunningMark() {
        CancellationRegistry registry = new CancellationRegistry();

        registry.requestStop("s-1");
        assertTrue(registry.isCancelled("s-1"));
        assertTrue(registry.tryMarkRunning("s-1"), "停止标志不应影响执行中标记");

        registry.clear("s-1");
        assertFalse(registry.isCancelled("s-1"));
        assertEquals(1, registry.runningCount(), "clear 只清停止标志，不动执行中标记");
    }
}
