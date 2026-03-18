package com.zq.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryService 测试用例
 * 测试重试服务的各种场景
 */
class RetryServiceTest {

    private RetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new RetryService();
    }

    @Test
    void testExecuteWithRetry_SuccessOnFirstAttempt() {
        AtomicInteger counter = new AtomicInteger(0);
        String result = retryService.executeWithRetry(
            () -> {
                counter.incrementAndGet();
                return "success";
            },
            "testOperation",
            3,
            100,
            1000,
            2.0
        );

        assertEquals("success", result);
        assertEquals(1, counter.get());
    }

    @Test
    void testExecuteWithRetry_SuccessOnSecondAttempt() {
        AtomicInteger counter = new AtomicInteger(0);
        String result = retryService.executeWithRetry(
            () -> {
                int attempt = counter.incrementAndGet();
                if (attempt < 2) {
                    throw new RuntimeException("Temporary failure");
                }
                return "success";
            },
            "testOperation",
            3,
            10,
            100,
            2.0
        );

        assertEquals("success", result);
        assertEquals(2, counter.get());
    }

    @Test
    void testExecuteWithRetry_AllAttemptsFailed() {
        AtomicInteger counter = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () -> {
            retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("Persistent failure");
                },
                "testOperation",
                3,
                10,
                100,
                2.0
            );
        });

        assertEquals(3, counter.get());
    }

    @Test
    void testExecuteWithRetry_NoReturnValue() {
        AtomicInteger counter = new AtomicInteger(0);

        assertDoesNotThrow(() -> {
            retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                },
                "testOperation",
                3,
                10,
                100,
                2.0
            );
        });

        assertEquals(1, counter.get());
    }

    @Test
    void testExecuteWithRetry_MaxDelayRespected() {
        AtomicInteger counter = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        assertThrows(RuntimeException.class, () -> {
            retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("Failure");
                },
                "testOperation",
                5,
                10,    // 初始延迟 10ms
                50,    // 最大延迟 50ms
                3.0    // 退避倍数 3
            );
        });

        long elapsedTime = System.currentTimeMillis() - startTime;
        // 5 次尝试，总共 4 次重试延迟
        // 延迟序列: 10, 30, 50, 50 (不超过最大值)
        // 总延迟: 10 + 30 + 50 + 50 = 140ms
        // 加上执行时间，应该在 150-300ms 之间完成
        assertTrue(elapsedTime >= 100, "Should have taken at least 100ms for retries");
        assertTrue(elapsedTime < 500, "Should not take too long");
        assertEquals(5, counter.get());
    }

    @Test
    void testExecuteBatchWithRetry_AllSuccess() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        List<Integer> processedItems = new ArrayList<>();
        AtomicInteger attemptCount = new AtomicInteger(0);

        int successCount = retryService.executeBatchWithRetry(
            items,
            (RetryService.ThrowingConsumer<Integer>) item -> {
                processedItems.add(item);
                // 只在第一次处理 item 3 时失败
                if (item == 3 && attemptCount.incrementAndGet() == 1) {
                    throw new Exception("Simulated failure for item 3 on first attempt");
                }
            },
            "testBatchOperation",
            2
        );

        // 所有项目最终都应该成功（item 3 在第二次尝试时成功）
        assertEquals(5, successCount);
        assertTrue(processedItems.contains(3));
        // item 3 应该被处理了 2 次
        assertEquals(2, processedItems.stream().filter(i -> i == 3).count());
    }

    @Test
    void testExecuteBatchWithRetry_SomeFail() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        List<Integer> processedItems = new ArrayList<>();

        int successCount = retryService.executeBatchWithRetry(
            items,
            (RetryService.ThrowingConsumer<Integer>) item -> {
                processedItems.add(item);
                if (item == 3) {
                    throw new Exception("Persistent failure for item 3");
                }
            },
            "testBatchOperation",
            1  // 只尝试 1 次
        );

        // 项目 3 会失败
        assertEquals(4, successCount);
    }

    @Test
    void testExecuteBatchWithRetry_EmptyList() {
        List<Integer> items = List.of();

        int successCount = retryService.executeBatchWithRetry(
            items,
            (RetryService.ThrowingConsumer<Integer>) item -> {
                throw new Exception("Should not be called");
            },
            "testBatchOperation",
            3
        );

        assertEquals(0, successCount);
    }

    @Test
    void testExecuteBatchWithRetry_RetryUntilSuccess() {
        List<Integer> items = List.of(1, 2, 3);
        AtomicInteger attemptCount = new AtomicInteger(0);

        int successCount = retryService.executeBatchWithRetry(
            items,
            (RetryService.ThrowingConsumer<Integer>) item -> {
                attemptCount.incrementAndGet();
                if (attemptCount.get() <= 2) {
                    throw new Exception("First two attempts fail");
                }
            },
            "testBatchOperation",
            5
        );

        // 所有项目最终都应该成功
        assertEquals(3, successCount);
    }

    @Test
    void testExecuteBatchWithRetry_RetryWithDelay() throws Exception {
        List<Integer> items = List.of(1, 2);
        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        int successCount = retryService.executeBatchWithRetry(
            items,
            (RetryService.ThrowingConsumer<Integer>) item -> {
                // item 2 在第一次尝试时失败
                if (item == 2 && attemptCount.get() == 0) {
                    attemptCount.incrementAndGet();
                    throw new Exception("Fail first time for item 2");
                }
                // item 1 总是成功
            },
            "testBatchOperation",
            2
        );

        long elapsedTime = System.currentTimeMillis() - startTime;
        // 第一次尝试后，第二次尝试应该有延迟（至少 1000ms = 1000 * 1）
        assertTrue(elapsedTime >= 900, "Should have delay between retry attempts, elapsed=" + elapsedTime + "ms");
        assertEquals(2, successCount);
    }
}
