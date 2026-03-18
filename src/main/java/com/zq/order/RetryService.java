package com.zq.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 重试服务
 * 提供可配置的重试机制，用于处理可能失败的操作
 */
@Slf4j
@Component
public class RetryService {

    /**
     * 执行带重试的操作
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param maxAttempts 最大重试次数
     * @param initialDelayMs 初始重试延迟（毫秒）
     * @param maxDelayMs 最大重试延迟（毫秒）
     * @param backoffMultiplier 退避倍数（每次重试延迟增加的倍数）
     * @return 操作是否成功
     */
    public <T> T executeWithRetry(
            Supplier<T> operation,
            String operationName,
            int maxAttempts,
            long initialDelayMs,
            long maxDelayMs,
            double backoffMultiplier) {

        int attempt = 0;
        long currentDelay = initialDelayMs;
        RuntimeException lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                T result = operation.get();
                if (attempt > 1) {
                    log.info("Operation succeeded after {} attempts: {}", attempt, operationName);
                }
                return result;
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Operation failed (attempt {}/{}): {}, retrying in {}ms...",
                        attempt, maxAttempts, operationName, currentDelay);
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                    currentDelay = (long) Math.min(currentDelay * backoffMultiplier, maxDelayMs);
                } else {
                    log.error("Operation failed after {} attempts: {}", maxAttempts, operationName, e);
                }
            }
        }

        throw lastException;
    }

    /**
     * 执行带重试的操作（无返回值）
     */
    public void executeWithRetry(
            Runnable operation,
            String operationName,
            int maxAttempts,
            long initialDelayMs,
            long maxDelayMs,
            double backoffMultiplier) {

        executeWithRetry(
            () -> {
                operation.run();
                return null;
            },
            operationName,
            maxAttempts,
            initialDelayMs,
            maxDelayMs,
            backoffMultiplier
        );
    }

    /**
     * 批量执行操作，对失败的项目进行重试
     * @param items 要处理的项目列表
     * @param operation 对每个项目执行的操作
     * @param operationName 操作名称
     * @param maxAttempts 最大重试次数
     * @return 成功处理的项目数量
     */
    public <T> int executeBatchWithRetry(
            List<T> items,
            ThrowingConsumer<T> operation,
            String operationName,
            int maxAttempts) {

        if (items.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        List<T> failedItems = new ArrayList<>(items);

        for (int attempt = 1; attempt <= maxAttempts && !failedItems.isEmpty(); attempt++) {
            List<T> currentBatch = new ArrayList<>(failedItems);
            failedItems.clear();

            for (T item : currentBatch) {
                try {
                    operation.accept(item);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Item failed on attempt {}/{}: {} - {}",
                        attempt, maxAttempts, operationName, e.getMessage());
                    failedItems.add(item);
                }
            }

            if (!failedItems.isEmpty() && attempt < maxAttempts) {
                log.info("Retry attempt {}/{} for {} failed items", attempt + 1, maxAttempts, failedItems.size());
                try {
                    Thread.sleep(1000 * attempt); // 递增延迟
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!failedItems.isEmpty()) {
            log.error("After {} attempts, {} items still failed: {}", maxAttempts, failedItems.size(), operationName);
        }

        return successCount;
    }

    /**
     * 可抛出异常的 Consumer 接口
     */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
