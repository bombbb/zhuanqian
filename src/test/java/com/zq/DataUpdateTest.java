package com.zq;

import com.zq.order.Order;
import com.zq.order.OrderService;
import com.zq.position.Position;
import com.zq.position.PositionService;
import com.zq.stats.DepthStats;
import com.zq.stats.SpreadStats;
import com.zq.stats.StatsService;
import com.zq.strategy.StrategyConfig;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * 数据更新测试类
 * 测试Position、Order、DepthStats等数据是否正确更新到数据库
 */
@SpringBootTest
public class DataUpdateTest {
    
    private static final Logger log = LoggerFactory.getLogger(DataUpdateTest.class);

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private PositionService positionService;

    @Resource
    private OrderService orderService;

    @Resource
    private StatsService statsService;

    private static final String TEST_SYMBOL = "USDCUSDT";
    private static final StrategyConfig.Mode TEST_MODE = StrategyConfig.Mode.TESTNET;
    
    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), Order.class);
        mongoTemplate.remove(new Query(), Position.class);
        mongoTemplate.dropCollection("spread_stats");
        mongoTemplate.dropCollection("depth_stats");
        mongoTemplate.dropCollection("trade_stats");
    }
    
    @AfterEach
    void tearDown() {
        mongoTemplate.remove(new Query(), Order.class);
        mongoTemplate.remove(new Query(), Position.class);
        mongoTemplate.dropCollection("spread_stats");
        mongoTemplate.dropCollection("depth_stats");
        mongoTemplate.dropCollection("trade_stats");
    }

    /**
     * 测试持仓数据更新
     */
    @Test
    public void testPositionUpdate() throws InterruptedException {
        log.info("========================================");
        log.info("测试持仓数据更新");
        log.info("========================================");

        // 清理测试数据
        positionService.clearPosition(TEST_SYMBOL, TEST_MODE);
        Thread.sleep(100); // 等待异步操作完成

        // 1. 测试买入更新持仓
        double buyQty1 = 10.0;
        double buyPrice1 = 1.0002;
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, buyQty1, buyPrice1);
        Thread.sleep(100);

        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position, "持仓数据应该已创建");
        assertEquals(buyQty1, position.getQuantity(), 0.0001, "持仓数量应该正确");
        assertEquals(buyPrice1, position.getAvgBuyPrice(), 0.0001, "平均买入价应该正确");
        log.info("✓ 首次买入持仓创建成功: qty={}, avgPrice={}", position.getQuantity(), position.getAvgBuyPrice());

        // 2. 测试追加买入
        double buyQty2 = 5.0;
        double buyPrice2 = 1.0003;
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, buyQty2, buyPrice2);
        Thread.sleep(100);

        position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertEquals(buyQty1 + buyQty2, position.getQuantity(), 0.0001, "持仓数量应该累加");
        double expectedAvgPrice = (buyQty1 * buyPrice1 + buyQty2 * buyPrice2) / (buyQty1 + buyQty2);
        assertEquals(expectedAvgPrice, position.getAvgBuyPrice(), 0.0001, "平均买入价应该重新计算");
        log.info("✓ 追加买入持仓更新成功: qty={}, avgPrice={}", position.getQuantity(), position.getAvgBuyPrice());

        // 3. 测试卖出更新持仓
        double sellQty = 8.0;
        double sellPrice = 1.0005;
        positionService.updatePositionOnSell(TEST_SYMBOL, TEST_MODE, sellQty, sellPrice);
        Thread.sleep(100);

        position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertEquals(buyQty1 + buyQty2 - sellQty, position.getQuantity(), 0.0001, "持仓数量应该减少");
        log.info("✓ 卖出持仓更新成功: qty={}, avgPrice={}", position.getQuantity(), position.getAvgBuyPrice());

        // 4. 测试计算未实现盈亏
        double currentPrice = 1.0004;
        double pnl = positionService.calculateUnrealizedPnl(TEST_SYMBOL, TEST_MODE, currentPrice);
        Thread.sleep(100);

        position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertEquals(currentPrice, position.getCurrentPrice(), 0.0001, "当前价格应该更新");
        assertNotNull(position.getUnrealizedPnl(), "未实现盈亏应该计算");
        assertEquals(pnl, position.getUnrealizedPnl(), 0.0001, "未实现盈亏应该与计算结果一致");
        log.info("✓ 未实现盈亏计算成功: pnl={}, currentPrice={}", position.getUnrealizedPnl(), position.getCurrentPrice());

        // 清理测试数据
        positionService.clearPosition(TEST_SYMBOL, TEST_MODE);

        log.info("========================================");
        log.info("✅ 持仓数据更新测试通过");
        log.info("========================================\n");
    }

    /**
     * 测试订单数据更新
     */
    @Test
    public void testOrderUpdate() throws InterruptedException {
        log.info("========================================");
        log.info("测试订单数据更新");
        log.info("========================================");

        // 1. 测试创建订单
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setSymbol(TEST_SYMBOL);
        order.setMode(TEST_MODE);
        order.setSide("BUY");
        order.setPrice(1.0002);
        order.setQuantity(10.0);
        order.setOrderId(System.currentTimeMillis());
        order.setClientOrderId("TEST_" + System.currentTimeMillis());

        orderService.createOrder(order);
        Thread.sleep(100);

        Order savedOrder = orderService.getOrderById(order.getId());
        assertNotNull(savedOrder, "订单应该已创建");
        assertEquals(order.getSymbol(), savedOrder.getSymbol(), "订单交易对应该正确");
        assertEquals(order.getPrice(), savedOrder.getPrice(), 0.0001, "订单价格应该正确");
        log.info("✓ 订单创建成功: orderId={}, side={}, price={}, qty={}", 
            savedOrder.getId(), savedOrder.getSide(), savedOrder.getPrice(), savedOrder.getQuantity());

        // 2. 测试更新订单状态
        orderService.updateOrderStatus(order.getId(), Order.OrderStatus.SUBMITTED);
        Thread.sleep(100);

        savedOrder = orderService.getOrderById(order.getId());
        assertEquals(Order.OrderStatus.SUBMITTED, savedOrder.getStatus(), "订单状态应该更新");
        log.info("✓ 订单状态更新成功: status={}", savedOrder.getStatus());

        // 3. 测试记录订单成交
        double executedPrice = 1.0002;
        double executedQty = 10.0;
        double slippage = 0.0;
        orderService.recordFill(order.getId(), executedPrice, executedQty, slippage);
        Thread.sleep(100);

        savedOrder = orderService.getOrderById(order.getId());
        assertEquals(Order.OrderStatus.FILLED, savedOrder.getStatus(), "订单状态应该为已成交");
        assertEquals(executedPrice, savedOrder.getExecutedPrice(), 0.0001, "成交价应该记录");
        assertNotNull(savedOrder.getFillTime(), "成交时间应该记录");
        log.info("✓ 订单成交记录成功: executedPrice={}, executedQty={}, fillTime={}", 
            savedOrder.getExecutedPrice(), savedOrder.getExecutedQty(), savedOrder.getFillTime());

        // 4. 测试订单关联
        Order sellOrder = new Order();
        sellOrder.setId(UUID.randomUUID().toString());
        sellOrder.setSymbol(TEST_SYMBOL);
        sellOrder.setMode(TEST_MODE);
        sellOrder.setSide("SELL");
        sellOrder.setPrice(1.0005);
        sellOrder.setQuantity(10.0);
        orderService.createOrder(sellOrder);
        Thread.sleep(100);

        orderService.linkOrders(order.getId(), sellOrder.getId());
        Thread.sleep(100);

        Order buyOrderLinked = orderService.getOrderById(order.getId());
        Order sellOrderLinked = orderService.getOrderById(sellOrder.getId());
        assertEquals(sellOrder.getId(), buyOrderLinked.getRelatedOrderId(), "买单应该关联卖单");
        assertEquals(order.getId(), sellOrderLinked.getRelatedOrderId(), "卖单应该关联买单");
        log.info("✓ 订单关联成功: buyOrder <-> sellOrder");

        // 5. 测试查询挂单
        Order openOrder = new Order();
        openOrder.setId(UUID.randomUUID().toString());
        openOrder.setSymbol(TEST_SYMBOL);
        openOrder.setMode(TEST_MODE);
        openOrder.setSide("SELL");
        openOrder.setStatus(Order.OrderStatus.NEW);
        openOrder.setPrice(1.0006);
        openOrder.setQuantity(5.0);
        orderService.createOrder(openOrder);
        Thread.sleep(100);

        List<Order> openOrders = orderService.getOpenOrders(TEST_SYMBOL);
        assertTrue(openOrders.size() > 0, "应该有挂单");
        log.info("✓ 查询挂单成功: count={}", openOrders.size());

        log.info("========================================");
        log.info("✅ 订单数据更新测试通过");
        log.info("========================================\n");
    }

    /**
     * 测试深度统计数据更新
     */
    @Test
    public void testDepthStatsUpdate() throws InterruptedException {
        log.info("========================================");
        log.info("测试深度统计数据更新");
        log.info("========================================");

        // 1. 测试记录价差统计
        double bid = 1.0002;
        double ask = 1.0003;
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(100);

        LocalDate today = LocalDate.now();
        String spreadId = SpreadStats.generateId(TEST_SYMBOL, today, bid, ask);
        SpreadStats spreadStats = mongoTemplate.findById(spreadId, SpreadStats.class);
        assertNotNull(spreadStats, "价差统计应该已创建");
        assertEquals(1, spreadStats.getCount(), "统计次数应该为1");
        log.info("✓ 价差统计创建成功: bid={}, ask={}, count={}", 
            spreadStats.getBidPrice(), spreadStats.getAskPrice(), spreadStats.getCount());

        // 再记录一次相同的价差，应该递增count
        statsService.recordSpread(TEST_SYMBOL, bid, ask);
        Thread.sleep(100);

        spreadStats = mongoTemplate.findById(spreadId, SpreadStats.class);
        assertEquals(2, spreadStats.getCount(), "统计次数应该递增");
        log.info("✓ 价差统计递增成功: count={}", spreadStats.getCount());

        // 2. 测试记录深度统计
        double price = 1.0002;
        double supportRatio = 0.75;
        double volume = 1000.0;
        statsService.recordDepth(TEST_SYMBOL, price, supportRatio, volume);
        Thread.sleep(100);

        String priceRange = DepthStats.generatePriceRangeBucket(price);
        String supportRange = DepthStats.generateSupportRatioBucket(supportRatio);
        String depthId = DepthStats.generateId(TEST_SYMBOL, today, priceRange, supportRange);
        
        DepthStats depthStats = mongoTemplate.findById(depthId, DepthStats.class);
        assertNotNull(depthStats, "深度统计应该已创建");
        assertEquals(1, depthStats.getCount(), "统计次数应该为1");
        log.info("✓ 深度统计创建成功: priceRange={}, supportRange={}, count={}, avgVolume={}", 
            depthStats.getPriceRangeBucket(), depthStats.getSupportRatioBucket(), 
            depthStats.getCount(), depthStats.getAvgVolume());

        // 3. 查询统计数据
        Query query = new Query(where("symbol").is(TEST_SYMBOL).and("date").is(today));
        List<SpreadStats> spreadStatsList = mongoTemplate.find(query, SpreadStats.class);
        assertTrue(spreadStatsList.size() > 0, "应该有价差统计数据");
        log.info("✓ 查询价差统计成功: count={}", spreadStatsList.size());

        List<DepthStats> depthStatsList = mongoTemplate.find(query, DepthStats.class);
        assertTrue(depthStatsList.size() > 0, "应该有深度统计数据");
        log.info("✓ 查询深度统计成功: count={}", depthStatsList.size());

        log.info("========================================");
        log.info("✅ 深度统计数据更新测试通过");
        log.info("========================================\n");
    }

    /**
     * 综合测试：测试所有数据更新功能
     */
    @Test
    public void testAllDataUpdates() throws InterruptedException {
        log.info("\n");
        log.info("╔════════════════════════════════════════╗");
        log.info("║     数据更新综合测试                   ║");
        log.info("╚════════════════════════════════════════╝");
        log.info("");

        testPositionUpdate();
        testOrderUpdate();
        testDepthStatsUpdate();

        log.info("╔════════════════════════════════════════╗");
        log.info("║     ✅ 所有数据更新测试通过            ║");
        log.info("╚════════════════════════════════════════╝");
        log.info("");
        log.info("测试结论：");
        log.info("1. ✓ Position（持仓）数据更新正常");
        log.info("2. ✓ Order（订单）数据更新正常");
        log.info("3. ✓ SpreadStats（价差统计）数据更新正常");
        log.info("4. ✓ DepthStats（深度统计）数据更新正常");
        log.info("");
    }
}
