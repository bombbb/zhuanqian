package com.zq.stats;

import com.zq.config.ConfigChangeLog;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计分析服务测试
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class StatsAnalysisServiceTest {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private StatsAnalysisService statsAnalysisService;
    
    @Autowired
    private StrategyService strategyService;
    
    @Autowired
    private StatsService statsService;
    
    private static final String TEST_SYMBOL = "USDCUSDT";
    private static final String TEST_CONFIG_ID = "USDCUSDT_TESTNET";
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        mongoTemplate.dropCollection("spread_stats");
        mongoTemplate.dropCollection("depth_stats");
        mongoTemplate.dropCollection("trade_stats");
        mongoTemplate.dropCollection("config_change_logs");
    }
    
    /**
     * 测试价差统计分析
     */
    @Test
    void testAnalyzeOptimalMinProfitTick() throws Exception {
        // 准备测试数据 - 模拟最近7天的价差统计
        LocalDate today = LocalDate.now();
        
        // 价差 0.0001 出现 500 次（最常见）
        for (int i = 0; i < 5; i++) {
            SpreadStats stats = new SpreadStats();
            stats.setId(SpreadStats.generateId(TEST_SYMBOL, today.minusDays(i), 1.0000, 1.0001));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setBidPrice(1.0000);
            stats.setAskPrice(1.0001);
            stats.setCount(100);  // 每天100次
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 价差 0.0002 出现 200 次
        for (int i = 0; i < 5; i++) {
            SpreadStats stats = new SpreadStats();
            stats.setId(SpreadStats.generateId(TEST_SYMBOL, today.minusDays(i), 1.0000, 1.0002));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setBidPrice(1.0000);
            stats.setAskPrice(1.0002);
            stats.setCount(40);  // 每天40次
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 价差 0.0005 出现 100 次（较少）
        for (int i = 0; i < 5; i++) {
            SpreadStats stats = new SpreadStats();
            stats.setId(SpreadStats.generateId(TEST_SYMBOL, today.minusDays(i), 1.0000, 1.0005));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setBidPrice(1.0000);
            stats.setAskPrice(1.0005);
            stats.setCount(20);  // 每天20次
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 执行分析
        Double suggested = statsAnalysisService.analyzeOptimalMinProfitTick(TEST_SYMBOL, 7);
        
        // 验证结果
        assertNotNull(suggested, "Should return suggested minProfitTick");
        assertTrue(suggested > 0, "Suggested value should be positive");
        assertTrue(suggested >= (0.0001 * 1.2) - 1e-9, "Should be at least 20% above minimum spread");
        
        System.out.println("✓ Suggested minProfitTick: " + suggested);
    }
    
    /**
     * 测试深度统计分析
     */
    @Test
    void testAnalyzeOptimalMinSupportRatio() throws Exception {
        // 准备测试数据 - 模拟最近7天的深度统计
        LocalDate today = LocalDate.now();
        
        // 支撑比率 0.6-0.7 出现最多
        for (int i = 0; i < 7; i++) {
            DepthStats stats = new DepthStats();
            stats.setId(DepthStats.generateId(TEST_SYMBOL, today.minusDays(i), "1.0000-1.0005", "0.6-0.7"));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setPriceRangeBucket("1.0000-1.0005");
            stats.setSupportRatioBucket("0.6-0.7");
            stats.setCount(100);  // 每天100次
            stats.setAvgVolume(500000.0);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 支撑比率 0.7-0.8 出现较少
        for (int i = 0; i < 7; i++) {
            DepthStats stats = new DepthStats();
            stats.setId(DepthStats.generateId(TEST_SYMBOL, today.minusDays(i), "1.0000-1.0005", "0.7-0.8"));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setPriceRangeBucket("1.0000-1.0005");
            stats.setSupportRatioBucket("0.7-0.8");
            stats.setCount(50);  // 每天50次
            stats.setAvgVolume(600000.0);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 执行分析
        Double suggested = statsAnalysisService.analyzeOptimalMinSupportRatio(TEST_SYMBOL, 7);
        
        // 验证结果
        assertNotNull(suggested, "Should return suggested minSupportRatio");
        assertTrue(suggested >= 0.5, "Should be at least 0.5");
        assertTrue(suggested <= 0.7, "Should not exceed 0.7");
        
        System.out.println("✓ Suggested minSupportRatio: " + suggested);
    }
    
    /**
     * 测试交易统计分析
     */
    @Test
    void testAnalyzeOptimalMaxHoldSeconds() throws Exception {
        // 准备测试数据 - 模拟最近7天的交易统计
        LocalDate today = LocalDate.now();
        
        for (int i = 0; i < 7; i++) {
            TradeStats stats = new TradeStats();
            stats.setId(TradeStats.generateId(TEST_SYMBOL, today.minusDays(i)));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setTotalTrades(10);  // 每天10笔交易
            stats.setProfitTrades(7);
            stats.setLossTrades(3);
            stats.setTotalPnl(5.0);
            stats.setAvgHoldSeconds(900.0);  // 平均持仓15分钟
            stats.setAvgSlippage(0.00005);
            stats.setMaxProfit(2.0);
            stats.setMaxLoss(-1.0);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 执行分析
        Integer suggested = statsAnalysisService.analyzeOptimalMaxHoldSeconds(TEST_SYMBOL, 7);
        
        // 验证结果
        assertNotNull(suggested, "Should return suggested maxHoldSeconds");
        assertTrue(suggested >= 600, "Should be at least 600 seconds (10 min)");
        assertTrue(suggested <= 3600, "Should not exceed 3600 seconds (1 hour)");
        assertTrue(suggested >= 900 * 1.5, "Should be at least 1.5x average hold time");
        
        System.out.println("✓ Suggested maxHoldSeconds: " + suggested);
    }
    
    /**
     * 测试配置变更记录
     */
    @Test
    void testConfigChangeLog() throws Exception {
        // 准备测试配置
        StrategyConfig config = mongoTemplate.findById(TEST_CONFIG_ID, StrategyConfig.class);
        if (config == null) {
            fail("Test config not found: " + TEST_CONFIG_ID);
        }
        
        double oldMaxBuyPrice = config.getMaxBuyPrice();
        double newMaxBuyPrice = oldMaxBuyPrice + 0.0001;
        
        // 执行配置更新
        strategyService.updateMaxBuyPrice(
            TEST_CONFIG_ID, 
            newMaxBuyPrice,
            ConfigChangeLog.ChangeSource.MANUAL,
            "Test config change"
        );
        
        // 等待异步操作完成
        Thread.sleep(2000);
        
        // 验证配置变更日志
        List<ConfigChangeLog> logs = mongoTemplate.findAll(ConfigChangeLog.class);
        assertFalse(logs.isEmpty(), "Should have config change logs");
        
        ConfigChangeLog log = logs.get(0);
        assertEquals(TEST_CONFIG_ID, log.getConfigId());
        assertEquals(ConfigChangeLog.ChangeSource.MANUAL, log.getSource());
        assertNotNull(log.getOldValues());
        assertNotNull(log.getNewValues());
        
        System.out.println("✓ Config change logged: " + log);
    }
    
    /**
     * 测试完整的自动分析和应用流程
     */
    @Test
    void testAutoAnalyzeAndApply() throws Exception {
        // 准备完整的测试数据
        LocalDate today = LocalDate.now();
        
        // 1. 准备价差统计数据
        for (int i = 0; i < 7; i++) {
            SpreadStats stats = new SpreadStats();
            stats.setId(SpreadStats.generateId(TEST_SYMBOL, today.minusDays(i), 1.0000, 1.0001));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setBidPrice(1.0000);
            stats.setAskPrice(1.0001);
            stats.setCount(100);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 2. 准备深度统计数据
        for (int i = 0; i < 7; i++) {
            DepthStats stats = new DepthStats();
            stats.setId(DepthStats.generateId(TEST_SYMBOL, today.minusDays(i), "1.0000-1.0005", "0.6-0.7"));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setPriceRangeBucket("1.0000-1.0005");
            stats.setSupportRatioBucket("0.6-0.7");
            stats.setCount(100);
            stats.setAvgVolume(500000.0);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 3. 准备交易统计数据
        for (int i = 0; i < 7; i++) {
            TradeStats stats = new TradeStats();
            stats.setId(TradeStats.generateId(TEST_SYMBOL, today.minusDays(i)));
            stats.setSymbol(TEST_SYMBOL);
            stats.setDate(today.minusDays(i));
            stats.setTotalTrades(10);
            stats.setProfitTrades(7);
            stats.setLossTrades(3);
            stats.setTotalPnl(5.0);
            stats.setAvgHoldSeconds(600.0);  // 平均10分钟（与当前配置30分钟差距大，应该触发更新）
            stats.setAvgSlippage(0.00005);
            stats.setMaxProfit(2.0);
            stats.setMaxLoss(-1.0);
            stats.setUpdateTime(LocalDateTime.now());
            mongoTemplate.insert(stats);
        }
        
        // 执行自动分析和应用
        boolean updated = statsAnalysisService.autoAnalyzeAndApply(TEST_CONFIG_ID, 7);
        
        // 等待异步操作完成
        Thread.sleep(3000);
        
        // 验证结果
        System.out.println("✓ Auto analysis completed, updated: " + updated);
        
        // 验证配置变更日志
        List<ConfigChangeLog> logs = mongoTemplate.findAll(ConfigChangeLog.class);
        if (updated) {
            assertFalse(logs.isEmpty(), "Should have config change logs when updated");
            System.out.println("✓ Config change logs: " + logs.size());
        }
    }
    
    /**
     * 测试统计数据记录
     */
    @Test
    void testStatsRecording() throws Exception {
        // 测试记录价差统计
        statsService.recordSpread(TEST_SYMBOL, 1.0000, 1.0001);
        Thread.sleep(1000);
        
        // 验证价差统计
        List<SpreadStats> spreadList = mongoTemplate.findAll(SpreadStats.class);
        assertFalse(spreadList.isEmpty(), "Should have spread stats");
        SpreadStats spreadStats = spreadList.get(0);
        assertEquals(TEST_SYMBOL, spreadStats.getSymbol());
        assertEquals(1.0000, spreadStats.getBidPrice(), 0.0001);
        assertEquals(1.0001, spreadStats.getAskPrice(), 0.0001);
        assertEquals(1, spreadStats.getCount());
        System.out.println("✓ Spread stats recorded: " + spreadStats);
        
        // 测试记录深度统计
        statsService.recordDepth(TEST_SYMBOL, 1.0002, 0.65, 500000.0);
        Thread.sleep(1000);
        
        // 验证深度统计
        String priceRange = DepthStats.generatePriceRangeBucket(1.0002);
        String supportRange = DepthStats.generateSupportRatioBucket(0.65);
        String depthId = DepthStats.generateId(TEST_SYMBOL, LocalDate.now(), priceRange, supportRange);
        DepthStats depthStats = mongoTemplate.findById(depthId, DepthStats.class);
        assertNotNull(depthStats, "Should have depth stats");
        assertEquals(TEST_SYMBOL, depthStats.getSymbol());
        assertEquals("1.0000-1.0005", depthStats.getPriceRangeBucket());
        assertEquals("0.6-0.7", depthStats.getSupportRatioBucket());
        assertEquals(1, depthStats.getCount());
        System.out.println("✓ Depth stats recorded: " + depthStats);
        
        // 测试记录交易统计
        statsService.recordTrade(TEST_SYMBOL, 0.5, 900, 0.00005);
        Thread.sleep(1000);
        
        // 验证交易统计
        List<TradeStats> tradeList = mongoTemplate.findAll(TradeStats.class);
        assertFalse(tradeList.isEmpty(), "Should have trade stats");
        TradeStats tradeStats = tradeList.get(0);
        assertEquals(TEST_SYMBOL, tradeStats.getSymbol());
        assertEquals(1, tradeStats.getTotalTrades());
        assertEquals(1, tradeStats.getProfitTrades());
        assertEquals(0.5, tradeStats.getTotalPnl(), 0.01);
        System.out.println("✓ Trade stats recorded: " + tradeStats);
    }
    
    /**
     * 测试多条DepthStats数据（按priceRangeBucket分组）
     */
    @Test
    void testMultipleDepthStatsRecords() throws Exception {
        LocalDate today = LocalDate.now();
        
        // 记录不同价格区间的深度统计
        statsService.recordDepth(TEST_SYMBOL, 0.9998, 0.65, 500000.0);  // 区间: 0.9995-1.0000
        statsService.recordDepth(TEST_SYMBOL, 1.0002, 0.70, 600000.0);  // 区间: 1.0000-1.0005
        statsService.recordDepth(TEST_SYMBOL, 1.0007, 0.75, 700000.0);  // 区间: 1.0005-1.0010
        
        Thread.sleep(2000);
        
        // 验证应该有3条不同的记录
        List<DepthStats> depthList = mongoTemplate.findAll(DepthStats.class);
        assertTrue(depthList.size() >= 3, "Should have at least 3 depth stats records");
        
        // 验证不同价格区间
        long distinctBuckets = depthList.stream()
            .map(DepthStats::getPriceRangeBucket)
            .distinct()
            .count();
        assertTrue(distinctBuckets >= 3, "Should have at least 3 distinct price range buckets");
        
        System.out.println("✓ Multiple depth stats records created:");
        depthList.forEach(stats -> 
            System.out.println("  - " + stats.getPriceRangeBucket() + " / " + stats.getSupportRatioBucket() + " : count=" + stats.getCount())
        );
    }
}
