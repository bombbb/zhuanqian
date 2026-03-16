package com.zq.order;

import com.zq.strategy.StrategyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderService 测试用例
 * 测试订单CRUD操作、虚拟线程异步写入
 */
@SpringBootTest
class OrderServiceTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Order.class);
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Order.class);
    }
    
    /**
     * 测试：创建订单
     */
    @Test
    void testCreateOrder() throws InterruptedException {
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setSymbol("USDCUSDT");
        order.setMode(StrategyConfig.Mode.TESTNET);
        order.setSide("BUY");
        order.setPrice(1.0000);
        order.setQuantity(100.0);
        order.setStatus(Order.OrderStatus.NEW);
        
        Order created = orderService.createOrder(order);
        
        assertNotNull(created);
        assertEquals("USDCUSDT", created.getSymbol());
        assertEquals(Order.OrderStatus.NEW, created.getStatus());
        assertNotNull(created.getCreateTime());
        
        // 等待虚拟线程完成
        Thread.sleep(200);
        
        // 验证数据库中存在
        Order found = mongoTemplate.findById(created.getId(), Order.class);
        assertNotNull(found);
        assertEquals("USDCUSDT", found.getSymbol());
    }
    
    /**
     * 测试：更新订单状态
     */
    @Test
    void testUpdateOrderStatus() throws InterruptedException {
        // 先创建订单
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setSymbol("USDCUSDT");
        order.setMode(StrategyConfig.Mode.TESTNET);
        order.setSide("BUY");
        order.setStatus(Order.OrderStatus.NEW);
        
        mongoTemplate.insert(order);
        
        // 更新状态
        orderService.updateOrderStatus(order.getId(), Order.OrderStatus.SUBMITTED);
        
        // 等待虚拟线程完成
        Thread.sleep(200);
        
        // 验证状态已更新
        Order updated = mongoTemplate.findById(order.getId(), Order.class);
        assertNotNull(updated);
        assertEquals(Order.OrderStatus.SUBMITTED, updated.getStatus());
    }
    
    /**
     * 测试：记录订单成交
     */
    @Test
    void testRecordFill() throws InterruptedException {
        // 先创建订单
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setSymbol("USDCUSDT");
        order.setMode(StrategyConfig.Mode.TESTNET);
        order.setSide("BUY");
        order.setPrice(1.0000);
        order.setQuantity(100.0);
        order.setStatus(Order.OrderStatus.SUBMITTED);
        
        mongoTemplate.insert(order);
        
        // 记录成交
        double executedPrice = 1.0002;
        double executedQty = 100.0;
        double slippage = 0.0002;
        
        orderService.recordFill(order.getId(), executedPrice, executedQty, slippage);
        
        // 等待虚拟线程完成
        Thread.sleep(200);
        
        // 验证成交信息已记录
        Order filled = mongoTemplate.findById(order.getId(), Order.class);
        assertNotNull(filled);
        assertEquals(Order.OrderStatus.FILLED, filled.getStatus());
        assertEquals(executedPrice, filled.getExecutedPrice());
        assertEquals(executedQty, filled.getExecutedQty());
        assertEquals(slippage, filled.getSlippage());
        assertNotNull(filled.getFillTime());
    }
    
    /**
     * 测试：查询挂单
     */
    @Test
    void testGetOpenOrders() throws InterruptedException {
        // 创建多个订单
        Order order1 = new Order();
        order1.setId(UUID.randomUUID().toString());
        order1.setSymbol("USDCUSDT");
        order1.setMode(StrategyConfig.Mode.TESTNET);
        order1.setSide("BUY");
        order1.setStatus(Order.OrderStatus.SUBMITTED);
        mongoTemplate.insert(order1);
        
        Order order2 = new Order();
        order2.setId(UUID.randomUUID().toString());
        order2.setSymbol("USDCUSDT");
        order2.setMode(StrategyConfig.Mode.TESTNET);
        order2.setSide("SELL");
        order2.setStatus(Order.OrderStatus.NEW);
        mongoTemplate.insert(order2);
        
        Order order3 = new Order();
        order3.setId(UUID.randomUUID().toString());
        order3.setSymbol("USDCUSDT");
        order3.setMode(StrategyConfig.Mode.TESTNET);
        order3.setSide("BUY");
        order3.setStatus(Order.OrderStatus.FILLED);
        mongoTemplate.insert(order3);
        
        // 查询挂单（应该返回order1和order2，不包括已成交的order3）
        List<Order> openOrders = orderService.getOpenOrders("USDCUSDT");
        
        assertNotNull(openOrders);
        assertEquals(2, openOrders.size());
        
        // 验证返回的是未成交订单
        assertTrue(openOrders.stream().allMatch(o -> 
            o.getStatus() == Order.OrderStatus.NEW || 
            o.getStatus() == Order.OrderStatus.SUBMITTED));
    }
    
    /**
     * 测试：关联买卖单
     */
    @Test
    void testLinkOrders() throws InterruptedException {
        // 创建买单和卖单
        Order buyOrder = new Order();
        buyOrder.setId(UUID.randomUUID().toString());
        buyOrder.setSymbol("USDCUSDT");
        buyOrder.setSide("BUY");
        buyOrder.setStatus(Order.OrderStatus.FILLED);
        mongoTemplate.insert(buyOrder);
        
        Order sellOrder = new Order();
        sellOrder.setId(UUID.randomUUID().toString());
        sellOrder.setSymbol("USDCUSDT");
        sellOrder.setSide("SELL");
        sellOrder.setStatus(Order.OrderStatus.SUBMITTED);
        mongoTemplate.insert(sellOrder);
        
        // 关联订单
        orderService.linkOrders(buyOrder.getId(), sellOrder.getId());
        
        // 等待虚拟线程完成
        Thread.sleep(200);
        
        // 验证关联关系
        Order updatedBuy = mongoTemplate.findById(buyOrder.getId(), Order.class);
        Order updatedSell = mongoTemplate.findById(sellOrder.getId(), Order.class);
        
        assertNotNull(updatedBuy);
        assertNotNull(updatedSell);
        assertEquals(sellOrder.getId(), updatedBuy.getRelatedOrderId());
        assertEquals(buyOrder.getId(), updatedSell.getRelatedOrderId());
    }
    
    /**
     * 测试：根据ID查询订单
     */
    @Test
    void testGetOrderById() {
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setSymbol("USDCUSDT");
        order.setSide("BUY");
        mongoTemplate.insert(order);
        
        Order found = orderService.getOrderById(order.getId());
        
        assertNotNull(found);
        assertEquals(order.getId(), found.getId());
        assertEquals("USDCUSDT", found.getSymbol());
    }
    
    /**
     * 测试：查询订单（不存在）
     */
    @Test
    void testGetOrderByIdNotFound() {
        Order found = orderService.getOrderById("non-existent-id");
        assertNull(found);
    }
    
    /**
     * 测试：批量创建订单（虚拟线程并发）
     */
    @Test
    void testBatchCreateOrders() throws InterruptedException {
        int count = 10;
        for (int i = 0; i < count; i++) {
            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setSymbol("USDCUSDT");
            order.setSide(i % 2 == 0 ? "BUY" : "SELL");
            order.setPrice(1.0000 + i * 0.0001);
            order.setQuantity(100.0);
            order.setStatus(Order.OrderStatus.NEW);
            
            orderService.createOrder(order);
        }
        
        // 等待所有虚拟线程完成
        Thread.sleep(500);
        
        // 验证所有订单都已创建
        List<Order> allOrders = mongoTemplate.findAll(Order.class);
        assertEquals(count, allOrders.size());
    }
}

