package com.zq;

import com.zq.config.CacheService;
import com.zq.order.Order;
import com.zq.position.Position;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 价格模拟测试
 * <p>
 * 测试场景：
 * 1. 价格上涨：1.0002 -> 1.0003 -> 1.0005
 * 2. 价格下跌：1.0005 -> 1.0003 -> 1.0002
 * 3. 验证下单、撤单逻辑
 */
@SpringBootTest
public class PriceSimulationTest {

    private static final Logger log = LoggerFactory.getLogger(PriceSimulationTest.class);
    
    private static final String TEST_SYMBOL = "USDCUSDT";
    private static final String TEST_CONFIG_ID = "USDCUSDT_TESTNET";

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    public void setup() {
        log.info("========================================");
        log.info("开始价格模拟测试");
        log.info("========================================");
        
        // 清空缓存
        cacheService.evictAll();
        log.info("✅ 缓存已清空");
    }

    @Test
    public void testPriceRising() throws InterruptedException {
        log.info("\n========================================");
        log.info("场景1: 价格上涨测试");
        log.info("模拟价格从 1.0002 -> 1.0003 -> 1.0005");
        log.info("========================================");

        // 获取初始配置
        StrategyConfig config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertNotNull(config, "配置不应为空");
        double initialMaxBuyPrice = config.getMaxBuyPrice();
        log.info("初始maxBuyPrice: {}", initialMaxBuyPrice);

        // 创建几个测试买单
        createTestBuyOrder(1.0001, 10.0, "NEW");
        createTestBuyOrder(1.0002, 15.0, "SUBMITTED");
        
        int initialOrderCount = countBuyOrders();
        log.info("初始挂单数量: {}", initialOrderCount);
        assertTrue(initialOrderCount >= 2, "应该有测试买单");

        // 阶段1: 价格上涨到 1.0003
        log.info("\n--- 阶段1: 价格上涨到 1.0003 ---");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0003);
        Thread.sleep(1000); // 等待更新生效

        config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(1.0003, config.getMaxBuyPrice(), 0.000001, "maxBuyPrice应更新为1.0003");
        
        int afterRise1OrderCount = countBuyOrders();
        log.info("价格上涨后挂单数量: {}", afterRise1OrderCount);
        log.info("ℹ️  价格上涨时：不应撤单，允许在稍高价格买入");
        // 价格上涨时不应该撤单
        assertEquals(initialOrderCount, afterRise1OrderCount, "价格上涨时不应撤单");

        // 阶段2: 价格继续上涨到 1.0005
        log.info("\n--- 阶段2: 价格继续上涨到 1.0005 ---");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0005);
        Thread.sleep(1000);

        config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(1.0005, config.getMaxBuyPrice(), 0.000001, "maxBuyPrice应更新为1.0005");
        
        int afterRise2OrderCount = countBuyOrders();
        log.info("继续上涨后挂单数量: {}", afterRise2OrderCount);
        log.info("ℹ️  继续上涨：更新买入上限，不撤单");
        assertEquals(initialOrderCount, afterRise2OrderCount, "价格继续上涨时仍不应撤单");

        log.info("\n✅ 价格上涨测试通过");
    }

    @Test
    public void testPriceFalling() throws InterruptedException {
        log.info("\n========================================");
        log.info("场景2: 价格下跌测试");
        log.info("模拟价格从 1.0005 -> 1.0003 -> 1.0002");
        log.info("========================================");

        // 先设置为较高价格
        log.info("设置初始价格为 1.0005");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0005);
        Thread.sleep(1000);

        // 创建几个测试买单
        createTestBuyOrder(1.0004, 10.0, "NEW");
        createTestBuyOrder(1.0005, 15.0, "SUBMITTED");
        createTestBuyOrder(1.0003, 20.0, "NEW");
        
        int initialOrderCount = countBuyOrders();
        log.info("初始挂单数量: {}", initialOrderCount);
        assertTrue(initialOrderCount >= 3, "应该有测试买单");

        // 阶段1: 价格下跌到 1.0003
        log.info("\n--- 阶段1: 价格下跌到 1.0003 ---");
        log.info("⚠️  价格下跌时：应该撤销所有买单！");
        
        // 注意：实际撤单需要DynamicPriceAdjuster调用
        // 这里我们测试的是updateMaxBuyPrice方法和缓存清空
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0003);
        Thread.sleep(1000);

        StrategyConfig config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(1.0003, config.getMaxBuyPrice(), 0.000001, "maxBuyPrice应更新为1.0003");
        
        log.info("价格下跌后maxBuyPrice已更新为: {}", config.getMaxBuyPrice());
        log.info("ℹ️  注意：实际撤单由DynamicPriceAdjuster在趋势分析时执行");

        // 阶段2: 价格继续下跌到 1.0002
        log.info("\n--- 阶段2: 价格继续下跌到 1.0002 ---");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0002);
        Thread.sleep(1000);

        config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(1.0002, config.getMaxBuyPrice(), 0.000001, "maxBuyPrice应更新为1.0002");

        log.info("\n✅ 价格下跌测试通过");
        log.info("ℹ️  完整的撤单测试需要启动应用并模拟真实行情数据");
    }

    @Test
    public void testPriceOscillation() throws InterruptedException {
        log.info("\n========================================");
        log.info("场景3: 价格震荡测试");
        log.info("模拟价格震荡: 1.0002 -> 1.0004 -> 1.0003");
        log.info("========================================");

        // 创建测试买单
        createTestBuyOrder(1.0002, 10.0, "NEW");
        int initialOrderCount = countBuyOrders();
        assertTrue(initialOrderCount >= 1, "应该有测试买单");

        // 上涨
        log.info("\n--- 价格上涨到 1.0004 ---");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0004);
        Thread.sleep(500);

        // 回落
        log.info("\n--- 价格回落到 1.0003 ---");
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, 1.0003);
        Thread.sleep(500);

        StrategyConfig config = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(1.0003, config.getMaxBuyPrice(), 0.000001, "maxBuyPrice应为1.0003");

        log.info("\n✅ 价格震荡测试通过");
    }

    @Test
    public void testCacheEviction() {
        log.info("\n========================================");
        log.info("场景4: 缓存清空测试");
        log.info("========================================");

        // 加载配置到缓存
        StrategyConfig config1 = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertNotNull(config1);
        log.info("✅ 配置已加载到缓存");

        // 更新配置（应该自动清空缓存）
        double newPrice = config1.getMaxBuyPrice() + 0.0001;
        strategyService.updateMaxBuyPrice(TEST_CONFIG_ID, newPrice);
        log.info("✅ 更新maxBuyPrice到: {}", newPrice);

        // 重新加载配置，应该从数据库读取最新值
        StrategyConfig config2 = strategyService.getStrategyConfig(TEST_SYMBOL);
        assertEquals(newPrice, config2.getMaxBuyPrice(), 0.000001, "应该读取到最新的maxBuyPrice");
        log.info("✅ 缓存已正确清空，读取到最新配置");

        // 测试手动清空缓存
        strategyService.clearCache(TEST_SYMBOL);
        log.info("✅ 手动清空缓存成功");
        
        log.info("\n✅ 缓存清空测试通过");
    }

    @Test
    public void testPositionCreation() throws InterruptedException {
        log.info("\n========================================");
        log.info("场景5: 持仓创建和更新测试");
        log.info("========================================");

        // 创建一个测试持仓
        Position position = new Position();
        position.setId("TEST_POS_" + System.currentTimeMillis());
        position.setSymbol(TEST_SYMBOL);
        position.setQuantity(100.0);
        position.setAvgBuyPrice(1.0003);
        position.setTotalInvestedUsdt(100.03);
        position.setMode(StrategyConfig.Mode.TESTNET);
        position.setCreateTime(LocalDateTime.now());
        position.setUpdateTime(LocalDateTime.now());

        Position saved = mongoTemplate.save(position);
        assertNotNull(saved);
        log.info("✅ 创建持仓: 数量={}, 均价={}, 投入={}", 
            saved.getQuantity(), saved.getAvgBuyPrice(), saved.getTotalInvestedUsdt());

        // 验证查询
        Position found = mongoTemplate.findById(saved.getId(), Position.class);
        assertNotNull(found);
        assertEquals(100.0, found.getQuantity(), 0.001);
        
        log.info("✅ 持仓创建和更新测试通过");
        
        // 清理测试数据
        mongoTemplate.remove(found);
    }

    /**
     * 创建测试买单
     */
    private void createTestBuyOrder(double price, double quantity, String status) {
        Order order = new Order();
        order.setId("TEST_ORDER_" + System.currentTimeMillis() + "_" + Math.random());
        order.setClientOrderId("TEST_" + System.currentTimeMillis());
        order.setSymbol(TEST_SYMBOL);
        order.setSide("BUY");
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(Order.OrderStatus.valueOf(status));
        order.setMode(StrategyConfig.Mode.TESTNET);
        order.setCreateTime(LocalDateTime.now());
        
        mongoTemplate.save(order);
        log.info("创建测试买单: 价格={}, 数量={}, 状态={}", price, quantity, status);
    }

    /**
     * 统计买单数量
     */
    private int countBuyOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("symbol").is(TEST_SYMBOL)
            .and("side").is("BUY")
            .and("status").in(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED));
        
        List<Order> orders = mongoTemplate.find(query, Order.class);
        return orders.size();
    }
}

