package com.zq.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderLockService 测试用例
 * 测试订单锁服务的基本功能
 */
class OrderLockServiceTest {

    private OrderLockService orderLockService;

    @BeforeEach
    void setUp() {
        orderLockService = new OrderLockService();
    }

    @Test
    void testTryLock_Success() {
        String symbol = "USDCUSDT";
        assertTrue(orderLockService.tryLock(symbol, 1000));
        assertTrue(orderLockService.isHeldByCurrentThread(symbol));
        orderLockService.unlock(symbol);
        assertFalse(orderLockService.isHeldByCurrentThread(symbol));
    }

    @Test
    void testTryLock_Timeout() {
        String symbol = "USDCUSDT";
        orderLockService.tryLock(symbol, 100000);

        // 在另一个线程中尝试获取锁，应该超时
        Thread thread = new Thread(() -> {
            boolean acquired = orderLockService.tryLock(symbol, 100);
            assertFalse(acquired, "Should not acquire lock when held by another thread");
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }

        orderLockService.unlock(symbol);
    }

    @Test
    void testUnlock_NotHeld() {
        String symbol = "USDCUSDT";
        // 解锁一个未被持有的锁，不应该抛出异常
        assertDoesNotThrow(() -> orderLockService.unlock(symbol));
    }

    @Test
    void testExecuteWithLock_Success() {
        String symbol = "USDCUSDT";
        boolean[] executed = new boolean[]{false};

        boolean result = orderLockService.executeWithLock(symbol, 1000, () -> {
            executed[0] = true;
            assertTrue(orderLockService.isHeldByCurrentThread(symbol));
        });

        assertTrue(result);
        assertTrue(executed[0]);
        assertFalse(orderLockService.isHeldByCurrentThread(symbol));
    }

    @Test
    void testExecuteWithLock_Timeout() {
        String symbol = "USDCUSDT";
        // 先获取锁
        orderLockService.tryLock(symbol, 100000);

        // 在另一个线程中尝试执行带锁的操作
        Thread thread = new Thread(() -> {
            boolean result = orderLockService.executeWithLock(symbol, 100, () -> {
                fail("Should not execute when lock is held");
            });
            assertFalse(result, "Should return false when timeout");
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }

        orderLockService.unlock(symbol);
    }

    @Test
    void testMultipleSymbols() {
        String symbol1 = "USDCUSDT";
        String symbol2 = "BTCUSDT";

        assertTrue(orderLockService.tryLock(symbol1, 1000));
        assertTrue(orderLockService.tryLock(symbol2, 1000));

        assertTrue(orderLockService.isHeldByCurrentThread(symbol1));
        assertTrue(orderLockService.isHeldByCurrentThread(symbol2));

        orderLockService.unlock(symbol1);
        orderLockService.unlock(symbol2);
    }

    @Test
    void testReentrantLock() {
        String symbol = "USDCUSDT";

        assertTrue(orderLockService.tryLock(symbol, 1000));
        assertTrue(orderLockService.isHeldByCurrentThread(symbol));

        // 同一线程可以再次获取锁（可重入）
        assertTrue(orderLockService.tryLock(symbol, 1000));

        orderLockService.unlock(symbol);
        // 需要解锁两次
        assertTrue(orderLockService.isHeldByCurrentThread(symbol));

        orderLockService.unlock(symbol);
        assertFalse(orderLockService.isHeldByCurrentThread(symbol));
    }
}
