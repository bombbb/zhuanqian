package com.zq.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 订单锁服务
 * 提供内存级别的分布式锁机制，防止并发下单
 * 在单机环境下使用 ReentrantLock 实现
 * 如果将来需要多实例部署，可以升级为 Redis 分布式锁
 */
@Slf4j
@Service
public class OrderLockService {

    // 锁的持有记录 (symbol -> lock)
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    // 锁的超时时间（分钟），防止死锁
    private static final long LOCK_TIMEOUT_MINUTES = 5;

    /**
     * 尝试获取交易锁
     * @param symbol 交易对
     * @param timeout 超时时间（毫秒）
     * @return 如果获取到锁返回 true，否则返回 false
     */
    public boolean tryLock(String symbol, long timeout) {
        ReentrantLock lock = locks.computeIfAbsent(symbol, k -> new ReentrantLock());

        try {
            boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired order lock for symbol: {}", symbol);
            } else {
                log.warn("Failed to acquire order lock for symbol: {} (timeout: {}ms)", symbol, timeout);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock: symbol={}", symbol);
            return false;
        }
    }

    /**
     * 释放交易锁
     * @param symbol 交易对
     */
    public void unlock(String symbol) {
        ReentrantLock lock = locks.get(symbol);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Released order lock for symbol: {}", symbol);
        }
    }

    /**
     * 检查锁是否被持有
     * @param symbol 交易对
     * @return 如果锁被当前线程持有返回 true
     */
    public boolean isHeldByCurrentThread(String symbol) {
        ReentrantLock lock = locks.get(symbol);
        return lock != null && lock.isHeldByCurrentThread();
    }

    /**
     * 执行带锁的操作
     * @param symbol 交易对
     * @param timeout 超时时间（毫秒）
     * @param action 要执行的操作
     * @return 操作是否成功执行
     */
    public boolean executeWithLock(String symbol, long timeout, Runnable action) {
        if (!tryLock(symbol, timeout)) {
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            unlock(symbol);
        }
    }
}
