package com.zq.position;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日损失跟踪器
 * 跟踪每日的盈亏情况，实现每日止损和回撤保护
 */
@Slf4j
@Component
public class DailyLossTracker {

    // 按交易对和日期跟踪每日盈亏
    private final ConcurrentHashMap<String, DailyStats> dailyStatsMap = new ConcurrentHashMap<>();

    /**
     * 记录交易盈亏
     * @param symbol 交易对
     * @param pnl 盈亏（正数为盈利，负数为亏损）
     * @return 如果是首次记录返回true
     */
    public boolean recordTrade(String symbol, double pnl) {
        String key = symbol + ":" + LocalDate.now();
        DailyStats stats = dailyStatsMap.computeIfAbsent(key, k -> new DailyStats(symbol, LocalDate.now()));

        boolean isFirstTrade = stats.tradeCount == 0;
        stats.totalPnl += pnl;
        stats.tradeCount++;

        // 更新峰值
        if (stats.totalPnl > stats.peakPnl) {
            stats.peakPnl = stats.totalPnl;
        }

        // 更新最大单笔亏损
        if (pnl < stats.worstTrade) {
            stats.worstTrade = pnl;
        }

        log.debug("Recorded trade: symbol={}, pnl={}, totalPnl={}, tradeCount={}",
            symbol, pnl, stats.totalPnl, stats.tradeCount);

        return isFirstTrade;
    }

    /**
     * 获取今日统计数据
     */
    public DailyStats getTodayStats(String symbol) {
        String key = symbol + ":" + LocalDate.now();
        return dailyStatsMap.get(key);
    }

    /**
     * 检查是否应该停止交易（触发止损）
     * @param symbol 交易对
     * @param dailyLossLimit 每日损失限额
     * @param drawdownThreshold 回撤保护阈值
     * @return 如果应该停止交易返回true
     */
    public boolean shouldStopTrading(String symbol, double dailyLossLimit, double drawdownThreshold) {
        DailyStats stats = getTodayStats(symbol);
        if (stats == null) {
            return false;
        }

        // 检查1: 每日损失限额
        if (stats.totalPnl < -dailyLossLimit) {
            log.warn("Daily loss limit reached: symbol={}, totalPnl={}, limit={}",
                symbol, stats.totalPnl, dailyLossLimit);
            return true;
        }

        // 检查2: 回撤保护
        if (stats.peakPnl > 0) {
            double drawdownFromPeak = stats.peakPnl - stats.totalPnl;
            double drawdownRatio = drawdownFromPeak / stats.peakPnl;
            if (drawdownRatio > drawdownThreshold) {
                log.warn("Daily drawdown limit reached: symbol={}, peakPnl={}, currentPnl={}, drawdown={}%, threshold={}%",
                    symbol, stats.peakPnl, stats.totalPnl,
                    String.format("%.2f", drawdownRatio * 100),
                    String.format("%.2f", drawdownThreshold * 100));
                return true;
            }
        }

        return false;
    }

    /**
     * 重置今日统计数据（用于测试或手动重置）
     */
    public void resetTodayStats(String symbol) {
        String key = symbol + ":" + LocalDate.now();
        dailyStatsMap.remove(key);
        log.info("Reset daily stats for symbol: {}", symbol);
    }

    /**
     * 清理过期数据（保留最近7天）
     */
    public void cleanupOldData() {
        LocalDate cutoff = LocalDate.now().minusDays(7);
        dailyStatsMap.entrySet().removeIf(entry -> {
            LocalDate date = entry.getValue().date;
            boolean isOld = date.isBefore(cutoff);
            if (isOld) {
                log.debug("Removed old daily stats: {}", entry.getKey());
            }
            return isOld;
        });
    }

    /**
     * 每日统计数据
     */
    @Data
    public static class DailyStats {
        private final String symbol;
        private final LocalDate date;
        private double totalPnl = 0.0;       // 总盈亏
        private double peakPnl = 0.0;        // 峰值盈亏
        private double worstTrade = 0.0;     // 最差单笔
        private int tradeCount = 0;          // 交易次数

        // 构造函数，只接受必填字段
        public DailyStats(String symbol, LocalDate date) {
            this.symbol = symbol;
            this.date = date;
        }
    }
}
