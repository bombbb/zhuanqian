package com.zq.api;

import com.zq.strategy.StrategyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BinanceApiService 测试用例
 * 测试币安API交互功能（使用测试网）
 */
@SpringBootTest
@Tag("integration")
class BinanceApiServiceTest {
    
    private BinanceApiService apiService;

    // 测试网配置（从usdt项目提取）
    private static final String TESTNET_API_URL = "https://testnet.binance.vision";
    private static final String TESTNET_API_KEY = "J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k";
    private static final String TESTNET_SECRET_KEY = "HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V";

    @BeforeEach
    void setUp() {
        // 初始化API服务（使用测试网）
        apiService = new BinanceApiService(
            TESTNET_API_URL,
            TESTNET_API_KEY,
            TESTNET_SECRET_KEY
        );
    }
    
    /**
     * 测试：查询USDT余额
     */
    @Test
    void testGetBalance() throws Exception {
        Balance balance = apiService.getBalance("USDT");
        
        assertNotNull(balance, "Balance should not be null");
        assertNotNull(balance.getAsset(), "Asset should not be null");
        assertEquals("USDT", balance.getAsset());
        assertTrue(balance.getFree() >= 0, "Free balance should be >= 0");
        assertTrue(balance.getLocked() >= 0, "Locked balance should be >= 0");
        
        System.out.println("USDT Balance: Free=" + balance.getFree() + ", Locked=" + balance.getLocked());
    }
    
    /**
     * 测试：获取账户信息
     */
    @Test
    void testGetAccountInfo() throws Exception {
        AccountInfo accountInfo = apiService.getAccountInfo();
        
        assertNotNull(accountInfo, "AccountInfo should not be null");
        assertTrue(accountInfo.isCanTrade(), "Account should be able to trade");
        assertNotNull(accountInfo.getBalances(), "Balances should not be null");
        assertTrue(accountInfo.getBalances().size() > 0, "Should have at least one balance");
        
        System.out.println("Account Info: canTrade=" + accountInfo.isCanTrade() + 
                           ", balances count=" + accountInfo.getBalances().size());
    }
    
    /**
     * 测试：下限价买单
     */
    @Test
    void testPlaceLimitBuyOrder() throws Exception {
        String symbol = "USDCUSDT";
        String side = "BUY";
        double quantity = 100.0;  // 100 USDC
        double price = 0.9995;    // 低价买入
        
        OrderResult result = apiService.placeLimitOrder(symbol, side, quantity, price);
        
        assertNotNull(result, "OrderResult should not be null");
        assertNotNull(result.getOrderId(), "OrderId should not be null");
        assertTrue(result.getOrderId() > 0, "OrderId should be > 0");
        assertEquals(symbol, result.getSymbol());
        assertEquals(side, result.getSide());
        assertEquals("LIMIT", result.getType());
        
        System.out.println("Limit Buy Order Placed: orderId=" + result.getOrderId() + 
                           ", status=" + result.getStatus());
        
        // 立即撤单（清理测试数据）
        OrderResult cancelResult = apiService.cancelOrder(symbol, result.getOrderId());
        assertEquals("CANCELED", cancelResult.getStatus());
        System.out.println("Order Canceled: orderId=" + cancelResult.getOrderId());
    }
    
    /**
     * 测试：下限价卖单
     */
    @Test
    void testPlaceLimitSellOrder() throws Exception {
        String symbol = "USDCUSDT";
        String side = "SELL";
        double quantity = 100.0;  // 100 USDC
        double price = 1.0050;    // 高价卖出（不会成交）
        
        OrderResult result = apiService.placeLimitOrder(symbol, side, quantity, price);
        
        assertNotNull(result, "OrderResult should not be null");
        assertNotNull(result.getOrderId(), "OrderId should not be null");
        assertTrue(result.getOrderId() > 0, "OrderId should be > 0");
        assertEquals(symbol, result.getSymbol());
        assertEquals(side, result.getSide());
        
        System.out.println("Limit Sell Order Placed: orderId=" + result.getOrderId());
        
        // 立即撤单
        OrderResult cancelResult = apiService.cancelOrder(symbol, result.getOrderId());
        assertEquals("CANCELED", cancelResult.getStatus());
        System.out.println("Order Canceled: orderId=" + cancelResult.getOrderId());
    }
    
    /**
     * 测试：查询订单状态
     */
    @Test
    void testGetOrderStatus() throws Exception {
        String symbol = "USDCUSDT";
        
        // 先下单
        OrderResult placedOrder = apiService.placeLimitOrder(symbol, "BUY", 100.0, 0.9995);
        assertNotNull(placedOrder.getOrderId());
        
        // 查询订单状态
        BinanceOrder order = apiService.getOrder(symbol, placedOrder.getOrderId());
        
        assertNotNull(order, "Order should not be null");
        assertEquals(placedOrder.getOrderId(), order.getOrderId());
        assertEquals(symbol, order.getSymbol());
        assertNotNull(order.getStatus());
        
        System.out.println("Order Status: orderId=" + order.getOrderId() + 
                           ", status=" + order.getStatus());
        
        // 清理
        apiService.cancelOrder(symbol, placedOrder.getOrderId());
    }
    
    /**
     * 测试：撤单
     */
    @Test
    void testCancelOrder() throws Exception {
        String symbol = "USDCUSDT";
        
        // 先下单
        OrderResult placedOrder = apiService.placeLimitOrder(symbol, "BUY", 100.0, 0.9995);
        long orderId = placedOrder.getOrderId();
        
        // 撤单
        OrderResult cancelResult = apiService.cancelOrder(symbol, orderId);
        
        assertNotNull(cancelResult, "Cancel result should not be null");
        assertEquals(orderId, cancelResult.getOrderId());
        assertEquals("CANCELED", cancelResult.getStatus());
        
        System.out.println("Order Canceled Successfully: orderId=" + orderId);
    }
    
    /**
     * 测试：获取当前挂单
     */
    @Test
    void testGetOpenOrders() throws Exception {
        String symbol = "USDCUSDT";
        
        // 先下两个挂单
        OrderResult order1 = apiService.placeLimitOrder(symbol, "BUY", 100.0, 0.9995);
        OrderResult order2 = apiService.placeLimitOrder(symbol, "BUY", 50.0, 0.9990);
        
        // 查询挂单
        java.util.List<BinanceOrder> openOrders = apiService.getOpenOrders(symbol);
        
        assertNotNull(openOrders, "Open orders should not be null");
        assertTrue(openOrders.size() >= 2, "Should have at least 2 open orders");
        
        System.out.println("Open Orders Count: " + openOrders.size());
        
        // 清理
        apiService.cancelOrder(symbol, order1.getOrderId());
        apiService.cancelOrder(symbol, order2.getOrderId());
    }
    
    /**
     * 测试：获取服务器时间
     */
    @Test
    void testGetServerTime() throws Exception {
        long serverTime = apiService.getServerTime();
        
        assertTrue(serverTime > 0, "Server time should be > 0");
        
        long localTime = System.currentTimeMillis();
        long diff = Math.abs(serverTime - localTime);
        assertTrue(diff < 10000, "Server time should be within 10 seconds of local time");
        
        System.out.println("Server Time: " + serverTime + ", Local Time: " + localTime + 
                           ", Diff: " + diff + "ms");
    }
    
    /**
     * 测试：获取当前价格
     */
    @Test
    void testGetCurrentPrice() throws Exception {
        String symbol = "USDCUSDT";
        
        double price = apiService.getCurrentPrice(symbol);
        
        assertTrue(price > 0, "Price should be > 0");
        assertTrue(price > 0.99 && price < 1.01, "USDC price should be around 1.0");
        
        System.out.println("Current Price of " + symbol + ": " + price);
    }
}
