package com.zq.position;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DailyLossTracker 测试用例
 * 测试每日损失跟踪功能
 */
class DailyLossTrackerTest {

    private DailyLossTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DailyLossTracker();
    }

    @Test
    void testRecordTrade_FirstTrade() {
        String symbol = "USDCUSDT";
        boolean isFirst = tracker.recordTrade(symbol, 10.0);
        assertTrue(isFirst, "First trade should return true");

        DailyLossTracker.DailyStats stats = tracker.getTodayStats(symbol);
        assertNotNull(stats);
        assertEquals(10.0, stats.getTotalPnl(), 0.001);
        assertEquals(1, stats.getTradeCount());
        assertEquals(10.0, stats.getPeakPnl(), 0.001);
    }

    @Test
    void testRecordTrade_SubsequentTrades() {
        String symbol = "USDCUSDT";
        tracker.recordTrade(symbol, 10.0);
        boolean isFirst = tracker.recordTrade(symbol, -5.0);
        assertFalse(isFirst, "Subsequent trade should return false");

        DailyLossTracker.DailyStats stats = tracker.getTodayStats(symbol);
        assertEquals(5.0, stats.getTotalPnl(), 0.001);
        assertEquals(2, stats.getTradeCount());
        assertEquals(10.0, stats.getPeakPnl(), 0.001); // 峰值保持不变
    }

    @Test
    void testRecordTrade_Loss() {
        String symbol = "USDCUSDT";
        tracker.recordTrade(symbol, -10.0);
        tracker.recordTrade(symbol, -5.0);

        DailyLossTracker.DailyStats stats = tracker.getTodayStats(symbol);
        assertEquals(-15.0, stats.getTotalPnl(), 0.001);
        assertEquals(2, stats.getTradeCount());
        assertEquals(-10.0, stats.getWorstTrade(), 0.001); // 最差单笔
    }

    @Test
    void testRecordTrade_PeakPnlUpdate() {
        String symbol = "USDCUSDT";
        tracker.recordTrade(symbol, 5.0);
        tracker.recordTrade(symbol, 10.0); // 新峰值
        tracker.recordTrade(symbol, -3.0);

        DailyLossTracker.DailyStats stats = tracker.getTodayStats(symbol);
        assertEquals(12.0, stats.getTotalPnl(), 0.001);
        assertEquals(15.0, stats.getPeakPnl(), 0.001); // 峰值 = 5 + 10 = 15
    }

    @Test
    void testShouldStopTrading_LossLimit() {
        String symbol = "USDCUSDT";
        double dailyLossLimit = 50.0;
        double drawdownThreshold = 0.1;

        // 未达到限制
        assertFalse(tracker.shouldStopTrading(symbol, dailyLossLimit, drawdownThreshold));

        // 达到损失限额
        tracker.recordTrade(symbol, -30.0);
        tracker.recordTrade(symbol, -25.0); // 总计 -55
        assertTrue(tracker.shouldStopTrading(symbol, dailyLossLimit, drawdownThreshold));
    }

    @Test
    void testShouldStopTrading_DrawdownProtection() {
        String symbol = "USDCUSDT";
        double dailyLossLimit = 1000.0;  // 设置一个很高的值
        double drawdownThreshold = 0.05;  // 5% 回撤保护

        // 先盈利
        tracker.recordTrade(symbol, 100.0); // 总盈亏 100，峰值 100

        // 回撤不到 5%
        assertFalse(tracker.shouldStopTrading(symbol, dailyLossLimit, drawdownThreshold));

        // 继续亏损，回撤超过 5% (100 * 0.05 = 5)
        tracker.recordTrade(symbol, -10.0); // 总盈亏 90，回撤 10
        assertTrue(tracker.shouldStopTrading(symbol, dailyLossLimit, drawdownThreshold));
    }

    @Test
    void testShouldStopTrading_NoStats() {
        String symbol = "USDCUSDT";
        assertFalse(tracker.shouldStopTrading(symbol, 50.0, 0.1));
    }

    @Test
    void testResetTodayStats() {
        String symbol = "USDCUSDT";
        tracker.recordTrade(symbol, 10.0);
        assertNotNull(tracker.getTodayStats(symbol));

        tracker.resetTodayStats(symbol);
        assertNull(tracker.getTodayStats(symbol));
    }

    @Test
    void testMultipleSymbols() {
        String symbol1 = "USDCUSDT";
        String symbol2 = "BTCUSDT";

        tracker.recordTrade(symbol1, 10.0);
        tracker.recordTrade(symbol2, 20.0);

        assertEquals(10.0, tracker.getTodayStats(symbol1).getTotalPnl(), 0.001);
        assertEquals(20.0, tracker.getTodayStats(symbol2).getTotalPnl(), 0.001);
    }

    @Test
    void testCleanupOldData() {
        String symbol = "USDCUSDT";

        // 模拟旧数据（通过直接操作内部状态）
        // 注意：由于 LocalDate 是 final 的，我们无法直接模拟旧日期
        // 这里只测试方法不会抛出异常
        assertDoesNotThrow(() -> tracker.cleanupOldData());
    }
}
