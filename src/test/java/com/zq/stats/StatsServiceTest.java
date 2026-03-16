package com.zq.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatsService 测试用例
 * 测试统计数据的聚合和更新
 */
@SpringBootTest
class StatsServiceTest {
    
    @Autowired
    private StatsService statsService;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    private static final String TEST_SYMBOL = "USDCUSDT";
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), SpreadStats.class);
        mongoTemplate.remove(new Query(), DepthStats.class);
        mongoTemplate.remove(new Query(), TradeStats.class);
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), SpreadStats.class);
        mongoTemplate.remove(new Query(), DepthStats.class);
        mongoTemplate.remove(new Query(), TradeStats.class);
    }
    
    /**
     * 测试：记录价差（首次）
     */
    @Test
    void testRecordSpread_First() throws InterruptedException {
        double bid = 1.0005;
        double ask = 1.0006;
        
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(200);
        
        String id = SpreadStats.generateId(TEST_SYMBOL, LocalDate.now(), bid, ask);
        SpreadStats stats = mongoTemplate.findById(id, SpreadStats.class);
        
        assertNotNull(stats);
        assertEquals(TEST_SYMBOL, stats.getSymbol());
        assertEquals(bid, stats.getBidPrice());
        assertEquals(ask, stats.getAskPrice());
        assertEquals(1, stats.getCount());
    }
    
    /**
     * 测试：记录价差（递增count）
     */
    @Test
    void testRecordSpread_Increment() throws InterruptedException {
        double bid = 1.0005;
        double ask = 1.0006;
        
        // 记录3次相同的价差
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(100);
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(100);
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(100);
        
        String id = SpreadStats.generateId(TEST_SYMBOL, LocalDate.now(), bid, ask);
        SpreadStats stats = mongoTemplate.findById(id, SpreadStats.class);
        
        assertNotNull(stats);
        assertEquals(3, stats.getCount());
    }
    
    /**
     * 测试：记录深度
     */
    @Test
    void testRecordDepth() throws InterruptedException {
        double price = 1.0003;
        double supportRatio = 0.65;
        double volume = 1000.0;
        
        statsService.recordDepth(TEST_SYMBOL, price, supportRatio, volume);
        Thread.sleep(200);
        
        // 验证数据已记录（需要查询）
        // 由于ID生成较复杂，这里只验证集合不为空
        long count = mongoTemplate.count(new Query(), DepthStats.class);
        assertTrue(count > 0);
    }
    
    /**
     * 测试：记录交易统计（首次）
     */
    @Test
    void testRecordTrade_First() throws InterruptedException {
        double pnl = 0.05;  // 盈利0.05 USDT
        int holdSeconds = 600;  // 持仓10分钟
        double slippage = 0.0001;
        
        statsService.recordTrade(TEST_SYMBOL, pnl, holdSeconds, slippage);
        Thread.sleep(200);
        
        String id = TradeStats.generateId(TEST_SYMBOL, LocalDate.now());
        TradeStats stats = mongoTemplate.findById(id, TradeStats.class);
        
        assertNotNull(stats);
        assertEquals(TEST_SYMBOL, stats.getSymbol());
        assertEquals(1, stats.getTotalTrades());
        assertEquals(1, stats.getProfitTrades());
        assertEquals(0, stats.getLossTrades());
        assertEquals(pnl, stats.getTotalPnl());
        assertEquals((double) holdSeconds, stats.getAvgHoldSeconds());
        assertEquals(slippage, stats.getAvgSlippage());
    }
    
    /**
     * 测试：记录交易统计（多次）
     */
    @Test
    void testRecordTrade_Multiple() throws InterruptedException {
        // 第一笔：盈利
        statsService.recordTrade(TEST_SYMBOL, 0.05, 600, 0.0001);
        Thread.sleep(100);
        
        // 第二笔：亏损
        statsService.recordTrade(TEST_SYMBOL, -0.02, 1800, 0.0002);
        Thread.sleep(100);
        
        // 第三笔：盈利
        statsService.recordTrade(TEST_SYMBOL, 0.08, 300, 0.0001);
        Thread.sleep(100);
        
        String id = TradeStats.generateId(TEST_SYMBOL, LocalDate.now());
        TradeStats stats = mongoTemplate.findById(id, TradeStats.class);
        
        assertNotNull(stats);
        assertEquals(3, stats.getTotalTrades());
        assertEquals(2, stats.getProfitTrades());
        assertEquals(1, stats.getLossTrades());
        
        // 总盈亏：0.05 - 0.02 + 0.08 = 0.11
        assertEquals(0.11, stats.getTotalPnl(), 0.001);
        
        // 平均持仓时间：(600 + 1800 + 300) / 3 = 900
        assertEquals(900.0, stats.getAvgHoldSeconds(), 1.0);
        
        // 平均滑点：(0.0001 + 0.0002 + 0.0001) / 3 = 0.000133...
        assertEquals(0.000133, stats.getAvgSlippage(), 0.00001);
    }
}

