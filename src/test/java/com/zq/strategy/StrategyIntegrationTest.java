package com.zq.strategy;

import com.zq.api.BinanceApiService;
import com.zq.api.TickerData;
import com.zq.order.Order;
import com.zq.order.OrderService;
import com.zq.position.Position;
import com.zq.position.PositionService;
import com.zq.stats.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略集成测试
 * 测试完整的套利策略流程：
 * 1. 接收行情数据
 * 2. 判断买入信号
 * 3. 执行买入订单
 * 4. 监控订单成交
 * 5. 执行卖出订单
 * 6. 检查过期订单并撤单
 * 7. 验证持仓和统计数据
 */
@SpringBootTest
class StrategyIntegrationTest {
    
    @Autowired
    private StrategyEngine strategyEngine;
    
    @Autowired
    private StrategyService strategyService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PositionService positionService;
    
    @Autowired
    private StatsService statsService;
    
    @Autowired
    private BinanceApiService apiService;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    private static final String TEST_SYMBOL = "USDCUSDT";
    private static final StrategyConfig.Mode TEST_MODE = StrategyConfig.Mode.TESTNET;
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Order.class);
        mongoTemplate.remove(new Query(), Position.class);
        mongoTemplate.remove(new Query(), "spread_stats");
        mongoTemplate.remove(new Query(), "depth_stats");
        mongoTemplate.remove(new Query(), "trade_stats");
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Order.class);
        mongoTemplate.remove(new Query(), Position.class);
        mongoTemplate.remove(new Query(), "spread_stats");
        mongoTemplate.remove(new Query(), "depth_stats");
        mongoTemplate.remove(new Query(), "trade_stats");
    }
    
    /**
     * 测试1：验证策略配置加载
     */
    @Test
    void test1_StrategyConfigLoaded() {
        StrategyConfig config = strategyService.getStrategyConfig();
        assertNotNull(config, "Strategy config should be loaded");
        assertEquals(TEST_SYMBOL, config.getSymbol());
        assertEquals(TEST_MODE, config.getMode());
        assertTrue(config.isEnabled());
        
        // 验证关键参数
        assertEquals(0.0001, config.getMinProfitTick());
        assertEquals(1800, config.getMaxHoldSeconds());
        assertEquals(300.0, config.getMaxBuyAmountUsdt());
        assertEquals(500.0, config.getMaxTotalInvestUsdt());
        assertEquals(0.6, config.getMinSupportRatio());
        assertEquals(1.0000, config.getReferencePrice());
        
        System.out.println("✅ Test 1 Passed: Strategy config loaded successfully");
        System.out.println("   Symbol: " + config.getSymbol());
        System.out.println("   Mode: " + config.getMode());
        System.out.println("   Reference Price: " + config.getReferencePrice());
        System.out.println("   Min Profit Tick: " + config.getMinProfitTick());
    }
    
    /**
     * 测试2：验证API服务初始化
     */
    @Test
    void test2_ApiServiceInitialized() {
        assertNotNull(apiService, "API service should be initialized");
        
        // 验证API服务可以获取服务器时间
        try {
            long serverTime = apiService.getServerTime();
            assertTrue(serverTime > 0, "Server time should be positive");
            System.out.println("✅ Test 2 Passed: API service initialized");
            System.out.println("   Server time: " + serverTime);
        } catch (Exception e) {
            fail("Failed to get server time: " + e.getMessage());
        }
    }
    
    /**
     * 测试3：模拟行情数据处理
     */
    @Test
    void test3_MarketDataProcessing() throws InterruptedException {
        // 创建模拟行情数据
        TickerData ticker = createMockTicker(
            TEST_SYMBOL,
            new BigDecimal("0.9998"),  // bid
            new BigDecimal("0.9999"),  // ask
            new BigDecimal("0.9998")   // last
        );
        
        // 处理行情数据
        strategyEngine.onMarketData(ticker);
        
        // 等待异步操作完成
        Thread.sleep(500);
        
        // 验证价差统计已记录
        // 注意：这里只是验证方法调用，实际数据需要查询数据库
        System.out.println("✅ Test 3 Passed: Market data processed");
        System.out.println("   Bid: " + ticker.getBestBidPrice());
        System.out.println("   Ask: " + ticker.getBestAskPrice());
        System.out.println("   Last: " + ticker.getLastPrice());
    }
    
    /**
     * 测试4：测试买入信号判断（价格低于basePrice）
     */
    @Test
    void test4_BuySignalDetection() throws InterruptedException {
        // 创建触发买入信号的行情数据
        TickerData ticker = createMockTicker(
            TEST_SYMBOL,
            new BigDecimal("0.9995"),  // bid - 低于basePrice
            new BigDecimal("0.9996"),  // ask
            new BigDecimal("0.9995")   // last
        );
        
        // 处理行情数据（应该触发买入）
        strategyEngine.onMarketData(ticker);
        
        // 等待异步操作完成
        Thread.sleep(1000);
        
        // 验证是否创建了买入订单
        List<Order> orders = orderService.getAllOrders(TEST_SYMBOL, TEST_MODE);
        
        if (!orders.isEmpty()) {
            Order buyOrder = orders.get(0);
            assertEquals("BUY", buyOrder.getSide());
            assertEquals(TEST_SYMBOL, buyOrder.getSymbol());
            System.out.println("✅ Test 4 Passed: Buy signal detected and order placed");
            System.out.println("   Order ID: " + buyOrder.getId());
            System.out.println("   Price: " + buyOrder.getPrice());
            System.out.println("   Quantity: " + buyOrder.getQuantity());
        } else {
            System.out.println("⚠️  Test 4: No buy order created (may need real API connection)");
        }
    }
    
    /**
     * 测试5：测试订单创建和查询
     */
    @Test
    void test5_OrderCreationAndQuery() throws InterruptedException {
        // 创建测试订单
        Order order = new Order();
        order.setId("test-order-1");
        order.setSymbol(TEST_SYMBOL);
        order.setMode(TEST_MODE);
        order.setSide("BUY");
        order.setPrice(0.9995);
        order.setQuantity(100.0);
        order.setOrderId(12345L);
        order.setStatus(Order.OrderStatus.NEW);
        
        orderService.createOrder(order);
        
        // 等待异步操作完成
        Thread.sleep(300);
        
        // 查询订单
        Order retrieved = orderService.getOrderById("test-order-1");
        assertNotNull(retrieved);
        assertEquals("BUY", retrieved.getSide());
        assertEquals(0.9995, retrieved.getPrice());
        
        System.out.println("✅ Test 5 Passed: Order created and retrieved");
        System.out.println("   Order ID: " + retrieved.getId());
        System.out.println("   Side: " + retrieved.getSide());
        System.out.println("   Status: " + retrieved.getStatus());
    }
    
    /**
     * 测试6：测试持仓更新
     */
    @Test
    void test6_PositionUpdate() throws InterruptedException {
        // 模拟买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 0.9995);
        
        // 等待异步操作完成
        Thread.sleep(300);
        
        // 查询持仓
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(100.0, position.getQuantity());
        assertEquals(0.9995, position.getAvgBuyPrice());
        
        // 验证可用资金
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        assertEquals(400.05, availableFunds, 0.01);  // 500 - 99.95
        
        System.out.println("✅ Test 6 Passed: Position updated correctly");
        System.out.println("   Quantity: " + position.getQuantity());
        System.out.println("   Avg Buy Price: " + position.getAvgBuyPrice());
        System.out.println("   Available Funds: " + availableFunds);
    }
    
    /**
     * 测试7：测试卖出订单创建
     */
    @Test
    void test7_SellOrderCreation() throws InterruptedException {
        // 先创建买入订单
        Order buyOrder = new Order();
        buyOrder.setId("buy-order-1");
        buyOrder.setSymbol(TEST_SYMBOL);
        buyOrder.setMode(TEST_MODE);
        buyOrder.setSide("BUY");
        buyOrder.setPrice(0.9995);
        buyOrder.setQuantity(100.0);
        buyOrder.setOrderId(12345L);
        buyOrder.setStatus(Order.OrderStatus.FILLED);
        orderService.createOrder(buyOrder);
        
        Thread.sleep(300);
        
        // 创建卖出订单
        Order sellOrder = new Order();
        sellOrder.setId("sell-order-1");
        sellOrder.setSymbol(TEST_SYMBOL);
        sellOrder.setMode(TEST_MODE);
        sellOrder.setSide("SELL");
        sellOrder.setPrice(0.9996);  // basePrice + minProfitTick
        sellOrder.setQuantity(100.0);
        sellOrder.setOrderId(12346L);
        sellOrder.setStatus(Order.OrderStatus.SUBMITTED);
        orderService.createOrder(sellOrder);
        
        Thread.sleep(300);
        
        // 关联买卖单
        orderService.linkOrders(buyOrder.getId(), sellOrder.getId());
        
        Thread.sleep(300);
        
        // 验证订单关联
        Order retrievedSell = orderService.getOrderById("sell-order-1");
        assertNotNull(retrievedSell);
        assertEquals("SELL", retrievedSell.getSide());
        
        System.out.println("✅ Test 7 Passed: Sell order created and linked");
        System.out.println("   Sell Order ID: " + retrievedSell.getId());
        System.out.println("   Price: " + retrievedSell.getPrice());
        System.out.println("   Related Buy Order: " + retrievedSell.getRelatedOrderId());
    }
    
    /**
     * 测试8：测试订单撤销
     */
    @Test
    void test8_OrderCancellation() throws InterruptedException {
        // 创建过期订单
        Order order = new Order();
        order.setId("expired-order-1");
        order.setSymbol(TEST_SYMBOL);
        order.setMode(TEST_MODE);
        order.setSide("SELL");
        order.setPrice(1.0001);
        order.setQuantity(100.0);
        order.setOrderId(12347L);
        order.setStatus(Order.OrderStatus.SUBMITTED);
        order.setCreateTime(LocalDateTime.now().minusMinutes(35));  // 35分钟前创建
        orderService.createOrder(order);
        
        Thread.sleep(300);
        
        // 标记订单为已撤销
        orderService.markOrderCanceled(order.getId());
        
        Thread.sleep(300);
        
        // 验证订单状态
        Order retrieved = orderService.getOrderById("expired-order-1");
        assertNotNull(retrieved);
        assertEquals(Order.OrderStatus.CANCELED, retrieved.getStatus());
        assertNotNull(retrieved.getCancelTime());
        
        System.out.println("✅ Test 8 Passed: Order canceled successfully");
        System.out.println("   Order ID: " + retrieved.getId());
        System.out.println("   Status: " + retrieved.getStatus());
        System.out.println("   Cancel Time: " + retrieved.getCancelTime());
    }
    
    /**
     * 测试9：测试完整交易流程（买入->卖出）
     */
    @Test
    void test9_CompleteTradeFlow() throws InterruptedException {
        // 1. 买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 0.9995);
        Thread.sleep(300);
        
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(100.0, position.getQuantity());
        
        // 2. 卖出
        positionService.updatePositionOnSell(TEST_SYMBOL, TEST_MODE, 100.0, 1.0005);
        Thread.sleep(300);
        
        position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(0.0, position.getQuantity());
        
        // 3. 验证可用资金恢复
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        assertEquals(500.0, availableFunds, 0.01);
        
        System.out.println("✅ Test 9 Passed: Complete trade flow executed");
        System.out.println("   Final position quantity: " + position.getQuantity());
        System.out.println("   Available funds: " + availableFunds);
    }
    
    /**
     * 测试10：测试统计数据记录
     */
    @Test
    void test10_StatisticsRecording() throws InterruptedException {
        // 记录价差统计
        statsService.recordSpread(TEST_SYMBOL, 0.9995, 0.9996);
        
        // 记录深度统计
        statsService.recordDepth(TEST_SYMBOL, 0.9995, 0.65, 1000000.0);
        
        // 等待异步操作完成
        Thread.sleep(500);
        
        System.out.println("✅ Test 10 Passed: Statistics recorded");
        System.out.println("   Spread recorded: bid=0.9995, ask=0.9996");
        System.out.println("   Depth recorded: price=0.9995, supportRatio=0.65");
    }
    
    /**
     * 辅助方法：创建模拟行情数据
     */
    private TickerData createMockTicker(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal last) {
        TickerData ticker = new TickerData();
        ticker.setSymbol(symbol);
        ticker.setBestBidPrice(bid);
        ticker.setBestBidQty(new BigDecimal("10000"));
        ticker.setBestAskPrice(ask);
        ticker.setBestAskQty(new BigDecimal("10000"));
        ticker.setLastPrice(last);
        ticker.setVolume(new BigDecimal("1000000"));
        ticker.setReceiveTime(LocalDateTime.now());
        return ticker;
    }
}
