package com.zq;

import com.zq.api.BinanceApiService;
import com.zq.api.Balance;
import com.zq.api.BinanceOrder;
import com.zq.api.OrderResult;
import com.zq.api.TickerData;
import com.zq.config.CacheService;
import com.zq.order.Order;
import com.zq.order.OrderService;
import com.zq.position.Position;
import com.zq.position.PositionService;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 交易操作综合测试类
 * <p>
 * 功能：
 * 1. 行情查询 - 获取实时价格和深度数据
 * 2. 下单 - 限价单和市价单
 * 3. 撤单 - 单个订单撤销
 * 4. 全部撤单 - 批量撤销所有挂单
 * 5. 挂单查询 - 查看当前所有挂单
 * <p>
 * 注意事项：
 * - 本测试类会从配置文件读取币安API配置
 * - 默认使用测试网环境，避免影响生产数据
 * - 所有下单操作都会在测试结束时清理
 * <p>
 * 使用方法：
 * 1. 确保MongoDB和应用配置正确
 * 2. 运行单个测试: ./gradlew test --tests TradingOperationsTest.testMethodName
 * 3. 运行全部测试: ./gradlew test --tests TradingOperationsTest
 * 
 * @author zhuanqian
 * @since 2026-01-18
 */
@SpringBootTest
@Tag("integration")
public class TradingOperationsTest {

    private static final Logger log = LoggerFactory.getLogger(TradingOperationsTest.class);
    
    // ==================== 配置参数 ====================
    
    /**
     * 测试交易对（默认使用USDCUSDT）
     */
    private String testSymbol = "USDCUSDT";
    
    /**
     * 测试运行模式（默认使用TESTNET）
     */
    private String testMode = "TESTNET";
    
    /**
     * 币安API URL（从数据库配置读取）
     */
    private String binanceApiUrl;
    
    /**
     * 币安API Key（从数据库配置读取）
     */
    private String binanceApiKey;
    
    /**
     * 币安Secret Key（从数据库配置读取）
     */
    private String binanceSecretKey;
    
    // ==================== 依赖注入 ====================
    
    @Autowired
    private StrategyService strategyService;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PositionService positionService;
    
    /**
     * 币安API服务（手动初始化）
     */
    private BinanceApiService apiService;
    
    /**
     * 测试过程中创建的订单ID列表（用于清理）
     */
    private final List<Long> testOrderIds = new ArrayList<>();
    
    /**
     * 应用上下文（用于main方法运行）
     */
    private static ApplicationContext applicationContext;
    
    // ==================== 测试初始化 ====================
    
    /**
     * 测试前初始化
     * 1. 从数据库加载策略配置
     * 2. 初始化币安API服务
     * 3. 清空缓存
     * 4. 打印测试配置信息
     */
    @BeforeEach
    public void setup() {
        log.info("========================================");
        log.info("交易操作综合测试 - 初始化");
        log.info("========================================");
        
        // 1. 从数据库加载策略配置
        log.info("\n--- 加载策略配置 ---");
        String configId = testSymbol + "_" + testMode;
        StrategyConfig config = strategyService.getStrategyConfig(testSymbol);
        
        if (config == null) {
            throw new RuntimeException("未找到配置: " + configId + 
                "\n请确保数据库中存在该配置，或修改 testSymbol 和 testMode");
        }
        
        log.info("✅ 配置已加载: {}", configId);
        
        // 2. 根据运行模式获取API配置
        StrategyConfig.Mode mode = config.getMode();
        switch (mode) {
            case TESTNET:
                binanceApiUrl = config.getTestnetApiUrl();
                binanceApiKey = config.getTestnetApiKey();
                binanceSecretKey = config.getTestnetSecretKey();
                break;
            case PRODUCTION:
                binanceApiUrl = config.getProductionApiUrl();
                binanceApiKey = config.getProductionApiKey();
                binanceSecretKey = config.getProductionSecretKey();
                break;
            case SIMULATION:
            default:
                // 模拟模式使用测试网配置
                binanceApiUrl = config.getTestnetApiUrl() != null ? 
                    config.getTestnetApiUrl() : "https://testnet.binance.vision";
                binanceApiKey = config.getTestnetApiKey() != null ? 
                    config.getTestnetApiKey() : "test";
                binanceSecretKey = config.getTestnetSecretKey() != null ? 
                    config.getTestnetSecretKey() : "test";
                break;
        }
        
        // 验证API配置完整性
        if (binanceApiUrl == null || binanceApiKey == null || binanceSecretKey == null) {
            throw new RuntimeException("API配置不完整，请检查数据库配置:\n" +
                "  - API URL: " + binanceApiUrl + "\n" +
                "  - API Key: " + (binanceApiKey != null ? "已配置" : "未配置") + "\n" +
                "  - Secret Key: " + (binanceSecretKey != null ? "已配置" : "未配置"));
        }
        
        // 3. 初始化币安API服务
        apiService = new BinanceApiService(binanceApiUrl, binanceApiKey, binanceSecretKey);
        log.info("✅ 币安API服务已初始化: {}", binanceApiUrl);
        
        // 4. 清空缓存
        cacheService.evictAll();
        log.info("✅ 缓存已清空");
        
        // 5. 清空测试订单列表
        testOrderIds.clear();
        
        // 6. 清理历史挂单（避免 MAX_NUM_ORDERS 错误）
        try {
            log.info("\n--- 清理历史挂单 ---");
            List<BinanceOrder> openOrders = apiService.getOpenOrders(testSymbol);
            if (!openOrders.isEmpty()) {
                log.info("发现 {} 个历史挂单，准备清理...", openOrders.size());
                List<Long> orderIds = new ArrayList<>();
                for (BinanceOrder order : openOrders) {
                    orderIds.add(order.getOrderId());
                }
                int canceledCount = apiService.cancelOrders(testSymbol, orderIds);
                log.info("✅ 已清理 {} 个历史挂单", canceledCount);
            } else {
                log.info("✅ 无历史挂单需要清理");
            }
        } catch (Exception e) {
            log.warn("清理历史挂单失败: {}", e.getMessage());
        }
        
        // 7. 打印测试配置
        log.info("\n📋 测试配置:");
        log.info("  - 配置ID: {}", configId);
        log.info("  - 交易对: {}", config.getSymbol());
        log.info("  - 运行模式: {}", config.getMode());
        log.info("  - API URL: {}", binanceApiUrl);
        log.info("  - 最高买入价: {}", config.getMaxBuyPrice());
        log.info("  - 最大买入金额: {} USDT", config.getMaxBuyAmountUsdt());
        log.info("========================================\n");
    }
    
    // ==================== 测试1: 行情查询 ====================
    
    /**
     * 测试1: 行情查询
     * 功能：获取实时价格、深度数据、最佳买卖价
     */
    @Test
    public void test01_MarketData() throws Exception {
        log.info("\n========================================");
        log.info("测试1: 行情查询");
        log.info("========================================");
        
        // 1.1 获取当前价格
        log.info("\n--- 1.1 获取当前价格 ---");
        double currentPrice = apiService.getCurrentPrice(testSymbol);
        assertNotNull(currentPrice);
        assertTrue(currentPrice > 0, "价格应该大于0");
        log.info("✅ 当前价格: {} ({})", currentPrice, testSymbol);
        
        // 1.2 获取深度数据（Book Ticker）
        log.info("\n--- 1.2 获取深度数据 (Book Ticker) ---");
        TickerData ticker = apiService.getBookTicker(testSymbol);
        assertNotNull(ticker);
        assertNotNull(ticker.getBestBidPrice());
        assertNotNull(ticker.getBestAskPrice());
        
        log.info("✅ 深度数据:");
        log.info("  - 最佳买价(Bid): {} (数量: {})", 
            ticker.getBestBidPrice(), ticker.getBestBidQty());
        log.info("  - 最佳卖价(Ask): {} (数量: {})", 
            ticker.getBestAskPrice(), ticker.getBestAskQty());
        log.info("  - 价差(Spread): {}", 
            ticker.getBestAskPrice().subtract(ticker.getBestBidPrice()));
        
        // 1.3 验证价格合理性（稳定币价格应接近1.0）
        assertTrue(currentPrice > 0.95 && currentPrice < 1.05, 
            "稳定币价格应在0.95-1.05之间");
        log.info("✅ 价格合理性验证通过");
        
        log.info("\n✅ 测试1通过: 行情查询正常");
    }
    
    // ==================== 测试2: 账户余额查询 ====================
    
    /**
     * 测试2: 账户余额查询
     * 功能：查询USDT和交易币种的余额
     */
    @Test
    public void test02_AccountBalance() throws Exception {
        log.info("\n========================================");
        log.info("测试2: 账户余额查询");
        log.info("========================================");
        
        // 2.1 查询USDT余额
        log.info("\n--- 2.1 查询USDT余额 ---");
        Balance usdtBalance = apiService.getBalance("USDT");
        assertNotNull(usdtBalance);
        assertTrue(usdtBalance.getFree() >= 0, "可用余额应>=0");
        assertTrue(usdtBalance.getLocked() >= 0, "锁定余额应>=0");
        
        double totalUsdt = usdtBalance.getFree() + usdtBalance.getLocked();
        log.info("✅ USDT余额:");
        log.info("  - 可用: {} USDT", usdtBalance.getFree());
        log.info("  - 锁定: {} USDT", usdtBalance.getLocked());
        log.info("  - 总计: {} USDT", totalUsdt);
        
        // 2.2 查询交易币种余额（如USDC）
        String baseCurrency = testSymbol.replace("USDT", "");
        log.info("\n--- 2.2 查询{}余额 ---", baseCurrency);
        Balance baseBalance = apiService.getBalance(baseCurrency);
        assertNotNull(baseBalance);
        
        double totalBase = baseBalance.getFree() + baseBalance.getLocked();
        log.info("✅ {}余额:", baseCurrency);
        log.info("  - 可用: {} {}", baseBalance.getFree(), baseCurrency);
        log.info("  - 锁定: {} {}", baseBalance.getLocked(), baseCurrency);
        log.info("  - 总计: {} {}", totalBase, baseCurrency);
        
        // 2.3 检查余额是否足够交易
        if (totalUsdt < 10.0) {
            log.warn("⚠️  USDT余额不足10U，可能无法完成后续交易测试");
        } else {
            log.info("✅ USDT余额充足，可以进行交易测试");
        }
        
        log.info("\n✅ 测试2通过: 账户余额查询正常");
    }
    
    // ==================== 测试3: 下单操作 ====================
    
    /**
     * 测试3: 下单操作
     * 功能：测试限价买单、限价卖单
     */
    @Test
    public void test03_PlaceOrders() throws Exception {
        log.info("\n========================================");
        log.info("测试3: 下单操作");
        log.info("========================================");
        
        // 获取当前价格
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double currentPrice = ticker.getLastPrice().doubleValue();
        
        // 3.1 下限价买单（低于市场价，不会立即成交）
        log.info("\n--- 3.1 下限价买单 ---");
        double buyPrice = currentPrice - 0.0010;  // 低于当前价0.1%
        buyPrice = apiService.adjustPrice(testSymbol, buyPrice);  // 调整价格精度
        double buyQuantity = 10.0;  // 10个币
        buyQuantity = apiService.adjustQuantityAndNotional(testSymbol, buyQuantity, buyPrice);  // 调整数量
        
        log.info("准备下买单:");
        log.info("  - 价格: {} (当前价: {})", buyPrice, currentPrice);
        log.info("  - 数量: {} {}", buyQuantity, testSymbol.replace("USDT", ""));
        log.info("  - 金额: ~{} USDT", buyPrice * buyQuantity);
        
        OrderResult buyOrder = apiService.placeLimitOrder(testSymbol, "BUY", buyQuantity, buyPrice);
        assertNotNull(buyOrder);
        assertNotNull(buyOrder.getOrderId());
        assertTrue(buyOrder.getOrderId() > 0);
        assertEquals("BUY", buyOrder.getSide());
        
        testOrderIds.add(buyOrder.getOrderId()); // 记录订单ID用于清理
        
        log.info("✅ 买单已下单:");
        log.info("  - 订单ID: {}", buyOrder.getOrderId());
        log.info("  - 状态: {}", buyOrder.getStatus());
        log.info("  - 价格: {}", buyOrder.getPrice());
        log.info("  - 数量: {}", buyOrder.getOrigQty());
        
        // 3.2 下限价卖单（高于市场价，不会立即成交）
        log.info("\n--- 3.2 下限价卖单 ---");
        double sellPrice = currentPrice + 0.0010;  // 高于当前价0.1%
        sellPrice = apiService.adjustPrice(testSymbol, sellPrice);  // 调整价格精度
        double sellQuantity = 10.0;
        sellQuantity = apiService.adjustQuantityAndNotional(testSymbol, sellQuantity, sellPrice);  // 调整数量
        
        log.info("准备下卖单:");
        log.info("  - 价格: {} (当前价: {})", sellPrice, currentPrice);
        log.info("  - 数量: {} {}", sellQuantity, testSymbol.replace("USDT", ""));
        log.info("  - 预计收入: ~{} USDT", sellPrice * sellQuantity);
        
        OrderResult sellOrder = apiService.placeLimitOrder(testSymbol, "SELL", sellQuantity, sellPrice);
        assertNotNull(sellOrder);
        assertNotNull(sellOrder.getOrderId());
        assertTrue(sellOrder.getOrderId() > 0);
        assertEquals("SELL", sellOrder.getSide());
        
        testOrderIds.add(sellOrder.getOrderId()); // 记录订单ID用于清理
        
        log.info("✅ 卖单已下单:");
        log.info("  - 订单ID: {}", sellOrder.getOrderId());
        log.info("  - 状态: {}", sellOrder.getStatus());
        log.info("  - 价格: {}", sellOrder.getPrice());
        log.info("  - 数量: {}", sellOrder.getOrigQty());
        
        log.info("\n✅ 测试3通过: 下单操作正常");
        
        // 等待一下，让订单进入系统
        Thread.sleep(500);
    }
    
    // ==================== 测试4: 挂单查询 ====================
    
    /**
     * 测试4: 挂单查询
     * 功能：查询当前所有挂单
     */
    @Test
    public void test04_QueryOpenOrders() throws Exception {
        log.info("\n========================================");
        log.info("测试4: 挂单查询");
        log.info("========================================");
        
        // 先下几个测试订单
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double currentPrice = ticker.getLastPrice().doubleValue();
        
        log.info("准备下2个测试订单...");
        double price1 = apiService.adjustPrice(testSymbol, currentPrice - 0.0010);
        double qty1 = apiService.adjustQuantityAndNotional(testSymbol, 10.0, price1);
        OrderResult order1 = apiService.placeLimitOrder(testSymbol, "BUY", qty1, price1);
        
        double price2 = apiService.adjustPrice(testSymbol, currentPrice - 0.0015);
        double qty2 = apiService.adjustQuantityAndNotional(testSymbol, 15.0, price2);
        OrderResult order2 = apiService.placeLimitOrder(testSymbol, "BUY", qty2, price2);
        testOrderIds.add(order1.getOrderId());
        testOrderIds.add(order2.getOrderId());
        log.info("✅ 已下2个买单: {} 和 {}", order1.getOrderId(), order2.getOrderId());
        
        Thread.sleep(500); // 等待订单进入系统
        
        // 4.1 查询指定交易对的挂单
        log.info("\n--- 4.1 查询{}的挂单 ---", testSymbol);
        List<BinanceOrder> openOrders = apiService.getOpenOrders(testSymbol);
        assertNotNull(openOrders);
        assertTrue(openOrders.size() >= 2, "应该至少有2个挂单");
        
        log.info("✅ 当前挂单数量: {}", openOrders.size());
        log.info("\n挂单列表:");
        log.info("----------------------------------------------------------------");
        log.info("订单ID\t\t方向\t价格\t\t数量\t状态");
        log.info("----------------------------------------------------------------");
        
        for (BinanceOrder order : openOrders) {
            log.info("{}\t{}\t{}\t{}\t{}", 
                order.getOrderId(),
                order.getSide(),
                order.getPrice(),
                order.getOrigQty(),
                order.getStatus()
            );
        }
        log.info("----------------------------------------------------------------");
        
        // 4.2 查询所有交易对的挂单
        log.info("\n--- 4.2 查询所有交易对的挂单 ---");
        List<BinanceOrder> allOpenOrders = apiService.getOpenOrders(null);
        assertNotNull(allOpenOrders);
        log.info("✅ 所有交易对的挂单总数: {}", allOpenOrders.size());
        
        log.info("\n✅ 测试4通过: 挂单查询正常");
    }
    
    // ==================== 测试5: 撤单操作 ====================
    
    /**
     * 测试5: 撤单操作
     * 功能：撤销单个订单
     */
    @Test
    public void test05_CancelOrder() throws Exception {
        log.info("\n========================================");
        log.info("测试5: 撤单操作");
        log.info("========================================");
        
        // 先下一个测试订单
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double currentPrice = ticker.getLastPrice().doubleValue();
        double buyPrice = apiService.adjustPrice(testSymbol, currentPrice - 0.0010);
        double buyQty = apiService.adjustQuantityAndNotional(testSymbol, 10.0, buyPrice);
        
        log.info("准备下测试订单...");
        OrderResult placedOrder = apiService.placeLimitOrder(testSymbol, "BUY", buyQty, buyPrice);
        long orderId = placedOrder.getOrderId();
        testOrderIds.add(orderId);
        log.info("✅ 订单已下单: ID={}, 价格={}, 数量={}", 
            orderId, placedOrder.getPrice(), placedOrder.getOrigQty());
        
        Thread.sleep(500); // 等待订单进入系统
        
        // 5.1 查询订单状态
        log.info("\n--- 5.1 查询订单状态 ---");
        BinanceOrder orderStatus = apiService.getOrder(testSymbol, orderId);
        assertNotNull(orderStatus);
        assertEquals(orderId, orderStatus.getOrderId());
        log.info("✅ 订单状态: {} (订单ID: {})", orderStatus.getStatus(), orderId);
        
        // 5.2 撤销订单
        log.info("\n--- 5.2 撤销订单 ---");
        log.info("准备撤销订单: {}", orderId);
        OrderResult cancelResult = apiService.cancelOrder(testSymbol, orderId);
        assertNotNull(cancelResult);
        assertEquals(orderId, cancelResult.getOrderId());
        assertEquals("CANCELED", cancelResult.getStatus());
        log.info("✅ 订单已撤销: ID={}, 状态={}", orderId, cancelResult.getStatus());
        
        // 从清理列表中移除（已经撤销）
        testOrderIds.remove(orderId);
        
        // 5.3 验证订单已撤销
        log.info("\n--- 5.3 验证订单已撤销 ---");
        BinanceOrder canceledOrder = apiService.getOrder(testSymbol, orderId);
        assertEquals("CANCELED", canceledOrder.getStatus());
        log.info("✅ 订单状态验证: {}", canceledOrder.getStatus());
        
        log.info("\n✅ 测试5通过: 撤单操作正常");
    }
    
    // ==================== 测试6: 批量撤单 ====================
    
    /**
     * 测试6: 批量撤单
     * 功能：一次性撤销多个订单
     */
    @Test
    public void test06_BatchCancelOrders() throws Exception {
        log.info("\n========================================");
        log.info("测试6: 批量撤单");
        log.info("========================================");
        
        // 先下多个测试订单
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double currentPrice = ticker.getLastPrice().doubleValue();
        
        List<Long> orderIds = new ArrayList<>();
        
        log.info("准备下5个测试订单...");
        for (int i = 0; i < 5; i++) {
            double buyPrice = apiService.adjustPrice(testSymbol, currentPrice - (0.0010 + i * 0.0001));
            double buyQty = apiService.adjustQuantityAndNotional(testSymbol, 10.0, buyPrice);
            OrderResult order = apiService.placeLimitOrder(testSymbol, "BUY", buyQty, buyPrice);
            orderIds.add(order.getOrderId());
            testOrderIds.add(order.getOrderId());
            log.info("  - 订单{}已下单: ID={}, 价格={}", (i + 1), order.getOrderId(), buyPrice);
        }
        
        Thread.sleep(500); // 等待订单进入系统
        
        log.info("✅ 已下5个买单");
        
        // 6.1 查询挂单数量
        log.info("\n--- 6.1 撤单前挂单统计 ---");
        List<BinanceOrder> beforeOrders = apiService.getOpenOrders(testSymbol);
        int beforeCount = beforeOrders.size();
        log.info("撤单前挂单数量: {}", beforeCount);
        assertTrue(beforeCount >= 5, "应该至少有5个挂单");
        
        // 6.2 批量撤销订单
        log.info("\n--- 6.2 批量撤销订单 ---");
        log.info("准备批量撤销{}个订单...", orderIds.size());
        int successCount = apiService.cancelOrders(testSymbol, orderIds);
        
        log.info("✅ 批量撤单完成:");
        log.info("  - 成功撤销: {} 个", successCount);
        log.info("  - 失败: {} 个", orderIds.size() - successCount);
        
        // 从清理列表中移除已撤销的订单
        testOrderIds.removeAll(orderIds);
        
        Thread.sleep(500); // 等待撤单生效
        
        // 6.3 验证撤单结果
        log.info("\n--- 6.3 撤单后挂单统计 ---");
        List<BinanceOrder> afterOrders = apiService.getOpenOrders(testSymbol);
        int afterCount = afterOrders.size();
        log.info("撤单后挂单数量: {}", afterCount);
        assertTrue(afterCount < beforeCount, "挂单数量应该减少");
        log.info("✅ 成功减少了 {} 个挂单", beforeCount - afterCount);
        
        log.info("\n✅ 测试6通过: 批量撤单正常");
    }
    
    // ==================== 测试7: 全部撤单 ====================
    
    /**
     * 测试7: 全部撤单
     * 功能：撤销指定交易对的所有挂单
     */
    @Test
    public void test07_CancelAllOrders() throws Exception {
        log.info("\n========================================");
        log.info("测试7: 全部撤单");
        log.info("========================================");
        
        // 先下几个测试订单
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double currentPrice = ticker.getLastPrice().doubleValue();
        
        log.info("准备下3个测试订单...");
        for (int i = 0; i < 3; i++) {
            double buyPrice = apiService.adjustPrice(testSymbol, currentPrice - (0.0010 + i * 0.0001));
            double buyQty = apiService.adjustQuantityAndNotional(testSymbol, 10.0, buyPrice);
            OrderResult order = apiService.placeLimitOrder(testSymbol, "BUY", buyQty, buyPrice);
            testOrderIds.add(order.getOrderId());
            log.info("  - 订单{}已下单: ID={}", (i + 1), order.getOrderId());
        }
        
        Thread.sleep(500); // 等待订单进入系统
        log.info("✅ 已下3个买单");
        
        // 7.1 查询当前挂单
        log.info("\n--- 7.1 撤单前挂单统计 ---");
        List<BinanceOrder> openOrders = apiService.getOpenOrders(testSymbol);
        int beforeCount = openOrders.size();
        log.info("当前挂单数量: {}", beforeCount);
        assertTrue(beforeCount >= 3, "应该至少有3个挂单");
        
        // 7.2 全部撤单
        log.info("\n--- 7.2 撤销所有挂单 ---");
        log.info("准备撤销所有{}的挂单...", testSymbol);
        
        List<Long> allOrderIds = new ArrayList<>();
        for (BinanceOrder order : openOrders) {
            allOrderIds.add(order.getOrderId());
        }
        
        int successCount = apiService.cancelOrders(testSymbol, allOrderIds);
        
        log.info("✅ 全部撤单完成:");
        log.info("  - 尝试撤销: {} 个", allOrderIds.size());
        log.info("  - 成功撤销: {} 个", successCount);
        log.info("  - 失败: {} 个", allOrderIds.size() - successCount);
        
        // 清空清理列表
        testOrderIds.clear();
        
        Thread.sleep(500); // 等待撤单生效
        
        // 7.3 验证全部撤单结果
        log.info("\n--- 7.3 撤单后验证 ---");
        List<BinanceOrder> afterOrders = apiService.getOpenOrders(testSymbol);
        int afterCount = afterOrders.size();
        log.info("撤单后挂单数量: {}", afterCount);
        log.info("✅ 已撤销 {} 个挂单", beforeCount - afterCount);
        
        if (afterCount == 0) {
            log.info("✅ 所有挂单已清空");
        } else {
            log.warn("⚠️  仍有 {} 个挂单未撤销（可能是其他测试创建的）", afterCount);
        }
        
        log.info("\n✅ 测试7通过: 全部撤单正常");
    }
    
    // ==================== 测试8: 配置读取 ====================
    
    /**
     * 测试8: 配置读取
     * 功能：验证从数据库读取策略配置
     */
    @Test
    public void test08_ConfigurationLoading() {
        log.info("\n========================================");
        log.info("测试8: 配置读取");
        log.info("========================================");
        
        // 8.1 读取策略配置
        log.info("\n--- 8.1 读取策略配置 ---");
        StrategyConfig config = strategyService.getStrategyConfig(testSymbol);
        
        assertNotNull(config, "配置不应为空");
        assertEquals(testSymbol, config.getSymbol());
        
        log.info("✅ 配置已加载:");
        log.info("  - 交易对: {}", config.getSymbol());
        log.info("  - 运行模式: {}", config.getMode());
        log.info("  - 参考价格: {}", config.getReferencePrice());
        log.info("  - 最高买入价: {}", config.getMaxBuyPrice());
        log.info("  - 最大买入金额: {} USDT", config.getMaxBuyAmountUsdt());
        log.info("  - 最小利润空间: {}", config.getMinProfitTick());
        log.info("  - 最大持仓时间: {} 秒", config.getMaxHoldSeconds());
        log.info("  - 最大总投入: {} USDT", config.getMaxTotalInvestUsdt());
        
        // 8.2 验证配置参数合理性
        log.info("\n--- 8.2 验证配置参数 ---");
        assertTrue(config.getMaxBuyPrice() > 0, "最高买入价应>0");
        assertTrue(config.getMaxBuyAmountUsdt() > 0, "最大买入金额应>0");
        assertTrue(config.getMinProfitTick() > 0, "最小利润空间应>0");
        assertTrue(config.getMaxHoldSeconds() > 0, "最大持仓时间应>0");
        assertTrue(config.getMaxTotalInvestUsdt() > 0, "最大总投入应>0");
        log.info("✅ 配置参数验证通过");
        
        // 8.3 缓存测试
        log.info("\n--- 8.3 缓存测试 ---");
        StrategyConfig cachedConfig = strategyService.getStrategyConfig(testSymbol);
        assertEquals(config.getMaxBuyPrice(), cachedConfig.getMaxBuyPrice(), 
            "缓存配置应与原配置一致");
        log.info("✅ 缓存功能正常");
        
        log.info("\n✅ 测试8通过: 配置读取正常");
    }
    
    // ==================== 测试9: 综合测试 ====================
    
    /**
     * 测试9: 综合测试
     * 功能：完整的交易流程测试
     */
    @Test
    public void test09_CompleteTrading() throws Exception {
        log.info("\n========================================");
        log.info("测试9: 综合测试 - 完整交易流程");
        log.info("========================================");
        
        // 9.1 加载配置
        log.info("\n--- 9.1 加载配置 ---");
        StrategyConfig config = strategyService.getStrategyConfig(testSymbol);
        assertNotNull(config);
        log.info("✅ 配置已加载: maxBuyPrice={}", config.getMaxBuyPrice());
        
        // 9.2 查询账户余额
        log.info("\n--- 9.2 查询账户余额 ---");
        Balance usdtBalance = apiService.getBalance("USDT");
        double availableUsdt = usdtBalance.getFree();
        log.info("✅ USDT可用余额: {} USDT", availableUsdt);
        
        if (availableUsdt < 10.0) {
            log.warn("⚠️  余额不足，跳过下单测试");
            return;
        }
        
        // 9.3 获取实时行情
        log.info("\n--- 9.3 获取实时行情 ---");
        TickerData ticker = apiService.getBookTicker(testSymbol);
        double bidPrice = ticker.getBestBidPrice().doubleValue();
        double askPrice = ticker.getBestAskPrice().doubleValue();
        double spread = askPrice - bidPrice;
        log.info("✅ 行情数据:");
        log.info("  - 买价: {}", bidPrice);
        log.info("  - 卖价: {}", askPrice);
        log.info("  - 价差: {}", spread);
        
        // 9.4 下买单（低于市价，不会成交）
        log.info("\n--- 9.4 下买单 ---");
        double buyPrice = apiService.adjustPrice(testSymbol, bidPrice - 0.0005);  // 低于买价
        double buyQuantity = apiService.adjustQuantityAndNotional(testSymbol, 10.0, buyPrice);
        log.info("准备下买单: 价格={}, 数量={}", buyPrice, buyQuantity);
        
        OrderResult buyOrder = apiService.placeLimitOrder(testSymbol, "BUY", buyQuantity, buyPrice);
        assertNotNull(buyOrder);
        testOrderIds.add(buyOrder.getOrderId());
        log.info("✅ 买单已下单: ID={}", buyOrder.getOrderId());
        
        Thread.sleep(500);
        
        // 9.5 查询挂单
        log.info("\n--- 9.5 查询挂单 ---");
        List<BinanceOrder> openOrders = apiService.getOpenOrders(testSymbol);
        boolean foundOrder = openOrders.stream()
            .anyMatch(o -> o.getOrderId() == buyOrder.getOrderId());
        assertTrue(foundOrder, "应该能找到刚下的订单");
        log.info("✅ 找到挂单: 当前共有 {} 个挂单", openOrders.size());
        
        // 9.6 撤销订单
        log.info("\n--- 9.6 撤销订单 ---");
        OrderResult cancelResult = apiService.cancelOrder(testSymbol, buyOrder.getOrderId());
        assertEquals("CANCELED", cancelResult.getStatus());
        testOrderIds.remove(buyOrder.getOrderId());
        log.info("✅ 订单已撤销: ID={}", buyOrder.getOrderId());
        
        // 9.7 验证撤单
        log.info("\n--- 9.7 验证撤单 ---");
        BinanceOrder canceledOrder = apiService.getOrder(testSymbol, buyOrder.getOrderId());
        assertEquals("CANCELED", canceledOrder.getStatus());
        log.info("✅ 撤单验证成功");
        
        log.info("\n✅ 测试9通过: 完整交易流程正常");
    }
    
    // ==================== 测试清理 ====================
    
    /**
     * 测试后清理
     * 撤销所有测试过程中创建的订单
     */
    @org.junit.jupiter.api.AfterEach
    public void cleanup() {
        log.info("\n========================================");
        log.info("测试清理 - 撤销未清理的订单");
        log.info("========================================");
        
        if (testOrderIds.isEmpty()) {
            log.info("✅ 无需清理");
            return;
        }
        
        log.info("准备清理 {} 个订单...", testOrderIds.size());
        
        int successCount = 0;
        for (Long orderId : testOrderIds) {
            try {
                apiService.cancelOrder(testSymbol, orderId);
                successCount++;
                log.info("  - 订单{}已撤销", orderId);
            } catch (Exception e) {
                log.warn("  - 订单{}撤销失败: {}", orderId, e.getMessage());
            }
        }
        
        log.info("✅ 清理完成: 成功撤销 {} / {} 个订单", successCount, testOrderIds.size());
        testOrderIds.clear();
    }
    
    // ==================== 测试10: 持仓查询 ====================
    
    /**
     * 测试10: 持仓查询
     * 功能：查询当前持仓信息
     */
    @Test
    public void test10_QueryPosition() throws Exception {
        log.info("\n========================================");
        log.info("测试10: 持仓查询");
        log.info("========================================");
        
        StrategyConfig.Mode mode = StrategyConfig.Mode.valueOf(testMode);
        
        // 10.1 查询持仓
        log.info("\n--- 10.1 查询持仓信息 ---");
        Position position = positionService.getPosition(testSymbol, mode);
        
        if (position == null) {
            log.info("ℹ️  当前无持仓");
            log.info("✅ 测试10通过: 持仓查询正常（无持仓）");
            return;
        }
        
        log.info("✅ 持仓信息:");
        log.info("  - 交易对: {}", position.getSymbol());
        log.info("  - 运行模式: {}", position.getMode());
        log.info("  - 持仓数量: {} {}", position.getQuantity(), testSymbol.replace("USDT", ""));
        log.info("  - 平均买入价: {}", position.getAvgBuyPrice());
        log.info("  - 已投入金额: {} USDT", position.getTotalInvestedUsdt());
        log.info("  - 当前价格: {}", position.getCurrentPrice());
        log.info("  - 未实现盈亏: {} USDT", position.getUnrealizedPnl());
        log.info("  - 创建时间: {}", position.getCreateTime());
        log.info("  - 更新时间: {}", position.getUpdateTime());
        
        // 10.2 计算最新盈亏
        log.info("\n--- 10.2 计算最新未实现盈亏 ---");
        double currentPrice = apiService.getCurrentPrice(testSymbol);
        double unrealizedPnl = positionService.calculateUnrealizedPnl(testSymbol, mode, currentPrice);
        
        log.info("✅ 最新未实现盈亏:");
        log.info("  - 当前价格: {}", currentPrice);
        log.info("  - 未实现盈亏: {} USDT", unrealizedPnl);
        
        if (unrealizedPnl > 0) {
            log.info("  - 盈利: +{} USDT ({}%)", 
                unrealizedPnl, 
                String.format("%.2f", unrealizedPnl / position.getTotalInvestedUsdt() * 100));
        } else if (unrealizedPnl < 0) {
            log.info("  - 亏损: {} USDT ({}%)", 
                unrealizedPnl, 
                String.format("%.2f", unrealizedPnl / position.getTotalInvestedUsdt() * 100));
        } else {
            log.info("  - 持平");
        }
        
        log.info("\n✅ 测试10通过: 持仓查询正常");
    }
    
    // ==================== 测试11: 历史订单查询 ====================
    
    /**
     * 测试11: 历史订单查询
     * 功能：查询所有历史订单（包括已成交、已撤销等）
     */
    @Test
    public void test11_QueryHistoricalOrders() {
        log.info("\n========================================");
        log.info("测试11: 历史订单查询");
        log.info("========================================");
        
        StrategyConfig.Mode mode = StrategyConfig.Mode.valueOf(testMode);
        
        // 11.1 查询所有订单
        log.info("\n--- 11.1 查询历史订单 ---");
        List<Order> allOrders = orderService.getAllOrders(testSymbol, mode);
        
        if (allOrders.isEmpty()) {
            log.info("ℹ️  无历史订单记录");
            log.info("✅ 测试11通过: 历史订单查询正常（无记录）");
            return;
        }
        
        log.info("✅ 历史订单总数: {}", allOrders.size());
        
        // 11.2 统计订单状态
        log.info("\n--- 11.2 订单状态统计 ---");
        Map<Order.OrderStatus, Integer> statusCount = new HashMap<>();
        for (Order order : allOrders) {
            statusCount.put(order.getStatus(), statusCount.getOrDefault(order.getStatus(), 0) + 1);
        }
        
        log.info("订单状态分布:");
        for (Map.Entry<Order.OrderStatus, Integer> entry : statusCount.entrySet()) {
            log.info("  - {}: {} 个", entry.getKey(), entry.getValue());
        }
        
        // 11.3 显示最近10个订单
        log.info("\n--- 11.3 最近订单列表（最多显示10个） ---");
        int displayCount = Math.min(10, allOrders.size());
        log.info("----------------------------------------------------------------");
        log.info("订单ID\t\t方向\t价格\t\t数量\t状态");
        log.info("----------------------------------------------------------------");
        
        for (int i = 0; i < displayCount; i++) {
            Order order = allOrders.get(allOrders.size() - 1 - i); // 从最新开始显示
            log.info("{}\t{}\t{}\t{}\t{}", 
                order.getId(),
                order.getSide(),
                order.getPrice(),
                order.getQuantity(),
                order.getStatus()
            );
        }
        log.info("----------------------------------------------------------------");
        
        log.info("\n✅ 测试11通过: 历史订单查询正常");
    }
    
    // ==================== 测试12: 盈亏统计 ====================
    
    /**
     * 测试12: 盈亏统计
     * 功能：计算已实现盈亏、未实现盈亏、总盈亏
     */
    @Test
    public void test12_ProfitLossStatistics() throws Exception {
        log.info("\n========================================");
        log.info("测试12: 盈亏统计");
        log.info("========================================");
        
        StrategyConfig.Mode mode = StrategyConfig.Mode.valueOf(testMode);
        
        // 12.1 计算盈亏
        log.info("\n--- 12.1 计算盈亏统计 ---");
        LocalDate today = LocalDate.now();
        ProfitLossStats stats = calculateProfitLossForDate(testSymbol, mode, today);
        
        // 12.2 显示统计结果
        log.info("\n✅ 盈亏统计结果 (日期={}):", today);
        log.info("==================== 已实现盈亏 ====================");
        log.info("  - 已成交买单数量: {}", stats.filledBuyCount);
        log.info("  - 已成交卖单数量: {}", stats.filledSellCount);
        log.info("  - 总买入金额: {} USDT", String.format("%.4f", stats.totalBuyAmount));
        log.info("  - 总卖出金额: {} USDT", String.format("%.4f", stats.totalSellAmount));
        log.info("  - 已实现盈亏: {} USDT", String.format("%.4f", stats.realizedPnl));
        
        if (stats.totalBuyAmount > 0) {
            double realizedPnlPercent = (stats.realizedPnl / stats.totalBuyAmount) * 100;
            log.info("  - 已实现收益率: {}%", String.format("%.2f", realizedPnlPercent));
        }
        
        log.info("\n==================== 未实现盈亏 ====================");
        log.info("  - 当前持仓数量: {} {}", stats.currentPosition, testSymbol.replace("USDT", ""));
        log.info("  - 平均买入价: {}", String.format("%.4f", stats.avgBuyPrice));
        log.info("  - 当前价格: {}", String.format("%.4f", stats.currentPrice));
        log.info("  - 持仓成本: {} USDT", String.format("%.4f", stats.positionCost));
        log.info("  - 未实现盈亏: {} USDT", String.format("%.4f", stats.unrealizedPnl));
        
        if (stats.positionCost > 0) {
            double unrealizedPnlPercent = (stats.unrealizedPnl / stats.positionCost) * 100;
            log.info("  - 未实现收益率: {}%", String.format("%.2f", unrealizedPnlPercent));
        }
        
        log.info("\n==================== 总盈亏 ====================");
        log.info("  - 总盈亏: {} USDT", String.format("%.4f", stats.totalPnl));
        
        if (stats.totalBuyAmount > 0) {
            double totalPnlPercent = (stats.totalPnl / stats.totalBuyAmount) * 100;
            log.info("  - 总收益率: {}%", String.format("%.2f", totalPnlPercent));
        }
        
        log.info("\n==================== 其他统计 ====================");
        log.info("  - 挂单数量: {}", stats.openOrderCount);
        log.info("  - 已撤销订单数: {}", stats.canceledOrderCount);
        log.info("  - 总订单数: {}", stats.totalOrderCount);
        
        log.info("\n✅ 测试12通过: 盈亏统计正常");
    }
    
    // ==================== 辅助方法：盈亏统计 ====================
    
    /**
     * 盈亏统计数据结构
     */
    public static class ProfitLossStats {
        // 已实现盈亏相关
        public int filledBuyCount = 0;      // 已成交买单数量
        public int filledSellCount = 0;     // 已成交卖单数量
        public double totalBuyAmount = 0.0; // 总买入金额
        public double totalSellAmount = 0.0;// 总卖出金额
        public double realizedPnl = 0.0;    // 已实现盈亏
        
        // 未实现盈亏相关
        public double currentPosition = 0.0; // 当前持仓数量
        public double avgBuyPrice = 0.0;     // 平均买入价
        public double currentPrice = 0.0;    // 当前价格
        public double positionCost = 0.0;    // 持仓成本
        public double unrealizedPnl = 0.0;   // 未实现盈亏
        
        // 总盈亏
        public double totalPnl = 0.0;        // 总盈亏
        
        // 其他统计
        public int openOrderCount = 0;       // 挂单数量
        public int canceledOrderCount = 0;   // 已撤销订单数
        public int totalOrderCount = 0;      // 总订单数
    }

    /**
     * 计算指定日期的盈亏统计
     */
    private ProfitLossStats calculateProfitLossForDate(String symbol, StrategyConfig.Mode mode, LocalDate date) throws Exception {
        ProfitLossStats stats = new ProfitLossStats();

        // 1. 查询所有订单
        List<Order> allOrders = orderService.getAllOrders(symbol, mode);
        stats.totalOrderCount = allOrders.size();

        // 2. 统计已实现盈亏（仅统计指定日期的成交）
        for (Order order : allOrders) {
            if (order.getStatus() == Order.OrderStatus.FILLED) {
                if (!isSameDate(order.getFillTime(), date)) {
                    continue;
                }
                double amount = order.getExecutedPrice() * order.getExecutedQty();

                if ("BUY".equals(order.getSide())) {
                    stats.filledBuyCount++;
                    stats.totalBuyAmount += amount;
                } else if ("SELL".equals(order.getSide())) {
                    stats.filledSellCount++;
                    stats.totalSellAmount += amount;
                }
            } else if (order.getStatus() == Order.OrderStatus.CANCELED) {
                if (isSameDate(order.getCancelTime(), date)) {
                    stats.canceledOrderCount++;
                }
            } else if (order.getStatus() == Order.OrderStatus.NEW ||
                       order.getStatus() == Order.OrderStatus.SUBMITTED) {
                stats.openOrderCount++;
            }
        }

        // 已实现盈亏 = 卖出金额 - 买入金额
        stats.realizedPnl = stats.totalSellAmount - stats.totalBuyAmount;

        // 3. 统计未实现盈亏（当前持仓）
        Position position = positionService.getPosition(symbol, mode);
        if (position != null && position.getQuantity() > 0) {
            stats.currentPosition = position.getQuantity();
            stats.avgBuyPrice = position.getAvgBuyPrice();
            stats.positionCost = position.getTotalInvestedUsdt();

            // 获取当前价格
            stats.currentPrice = apiService.getCurrentPrice(symbol);

            // 计算未实现盈亏
            stats.unrealizedPnl = positionService.calculateUnrealizedPnl(symbol, mode, stats.currentPrice);
        }

        // 4. 总盈亏 = 已实现盈亏 + 未实现盈亏
        stats.totalPnl = stats.realizedPnl + stats.unrealizedPnl;

        return stats;
    }

    private boolean isSameDate(LocalDateTime time, LocalDate date) {
        return time != null && date != null && time.toLocalDate().equals(date);
    }
    
    /**
     * 计算盈亏统计
     * @param symbol 交易对
     * @param mode 运行模式
     * @return 盈亏统计数据
     */
    private ProfitLossStats calculateProfitLoss(String symbol, StrategyConfig.Mode mode) throws Exception {
        ProfitLossStats stats = new ProfitLossStats();
        
        // 1. 查询所有订单
        List<Order> allOrders = orderService.getAllOrders(symbol, mode);
        stats.totalOrderCount = allOrders.size();
        
        // 2. 统计已实现盈亏（已成交的订单）
        for (Order order : allOrders) {
            if (order.getStatus() == Order.OrderStatus.FILLED) {
                double amount = order.getExecutedPrice() * order.getExecutedQty();
                
                if ("BUY".equals(order.getSide())) {
                    stats.filledBuyCount++;
                    stats.totalBuyAmount += amount;
                } else if ("SELL".equals(order.getSide())) {
                    stats.filledSellCount++;
                    stats.totalSellAmount += amount;
                }
            } else if (order.getStatus() == Order.OrderStatus.CANCELED) {
                stats.canceledOrderCount++;
            } else if (order.getStatus() == Order.OrderStatus.NEW || 
                       order.getStatus() == Order.OrderStatus.SUBMITTED) {
                stats.openOrderCount++;
            }
        }
        
        // 已实现盈亏 = 卖出金额 - 买入金额
        stats.realizedPnl = stats.totalSellAmount - stats.totalBuyAmount;
        
        // 3. 统计未实现盈亏（当前持仓）
        Position position = positionService.getPosition(symbol, mode);
        if (position != null && position.getQuantity() > 0) {
            stats.currentPosition = position.getQuantity();
            stats.avgBuyPrice = position.getAvgBuyPrice();
            stats.positionCost = position.getTotalInvestedUsdt();
            
            // 获取当前价格
            stats.currentPrice = apiService.getCurrentPrice(symbol);
            
            // 计算未实现盈亏
            stats.unrealizedPnl = positionService.calculateUnrealizedPnl(symbol, mode, stats.currentPrice);
        }
        
        // 4. 总盈亏 = 已实现盈亏 + 未实现盈亏
        stats.totalPnl = stats.realizedPnl + stats.unrealizedPnl;
        
        return stats;
    }
    
    // ==================== Main方法：直接运行 ====================
    
    /**
     * Main方法 - 可以直接通过Java运行测试
     * 使用方式：
     * 1. 编译: ./gradlew clean bootJar
     * 2. 运行: java -cp "build/libs/*" com.zq.TradingOperationsTest
     */
    public static void main(String[] args) {
        try {
            log.info("========================================");
            log.info("交易操作测试 - 独立运行模式");
            log.info("========================================\n");
            
            // 启动Spring Boot应用上下文
            log.info("正在启动应用...");
            applicationContext = SpringApplication.run(TradingApplication.class, args);
            log.info("✅ 应用启动成功\n");
            
            // 获取测试实例
            TradingOperationsTest test = applicationContext.getBean(TradingOperationsTest.class);
            
            // 初始化
            test.setup();
            
            // 显示菜单
            Scanner scanner = new Scanner(System.in);
            boolean running = true;
            
            while (running) {
                printMenu();
                System.out.print("请选择操作 (1-13, 0退出): ");
                
                try {
                    int choice = scanner.nextInt();
                    scanner.nextLine(); // 消费换行符
                    
                    System.out.println();
                    
                    switch (choice) {
                        case 0:
                            log.info("退出测试程序");
                            running = false;
                            break;
                        case 1:
                            test.test01_MarketData();
                            break;
                        case 2:
                            test.test02_AccountBalance();
                            break;
                        case 3:
                            test.test03_PlaceOrders();
                            break;
                        case 4:
                            test.test04_QueryOpenOrders();
                            break;
                        case 5:
                            test.test05_CancelOrder();
                            break;
                        case 6:
                            test.test06_BatchCancelOrders();
                            break;
                        case 7:
                            test.test07_CancelAllOrders();
                            break;
                        case 8:
                            test.test08_ConfigurationLoading();
                            break;
                        case 9:
                            test.test09_CompleteTrading();
                            break;
                        case 10:
                            test.test10_QueryPosition();
                            break;
                        case 11:
                            test.test11_QueryHistoricalOrders();
                            break;
                        case 12:
                            test.test12_ProfitLossStatistics();
                            break;
                        case 13:
                            // 运行所有测试
                            runAllTests(test);
                            break;
                        default:
                            log.warn("无效的选项，请重新选择");
                    }
                    
                    if (running && choice != 0) {
                        System.out.println("\n按Enter键继续...");
                        scanner.nextLine();
                    }
                    
                } catch (Exception e) {
                    log.error("执行测试时出错", e);
                    scanner.nextLine(); // 清空输入缓冲
                    System.out.println("\n按Enter键继续...");
                    scanner.nextLine();
                }
            }
            
            scanner.close();
            
            // 清理
            test.cleanup();
            
            log.info("\n========================================");
            log.info("测试程序已退出");
            log.info("========================================");
            
            System.exit(0);
            
        } catch (Exception e) {
            log.error("运行测试失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 打印菜单
     */
    private static void printMenu() {
        System.out.println("\n========================================");
        System.out.println("交易操作测试菜单");
        System.out.println("========================================");
        System.out.println("1. 测试1: 行情查询");
        System.out.println("2. 测试2: 账户余额查询");
        System.out.println("3. 测试3: 下单操作");
        System.out.println("4. 测试4: 挂单查询");
        System.out.println("5. 测试5: 撤单操作");
        System.out.println("6. 测试6: 批量撤单");
        System.out.println("7. 测试7: 全部撤单");
        System.out.println("8. 测试8: 配置读取");
        System.out.println("9. 测试9: 综合测试");
        System.out.println("10. 测试10: 持仓查询");
        System.out.println("11. 测试11: 历史订单查询");
        System.out.println("12. 测试12: 盈亏统计");
        System.out.println("13. 运行所有测试");
        System.out.println("0. 退出");
        System.out.println("========================================");
    }
    
    /**
     * 运行所有测试
     */
    private static void runAllTests(TradingOperationsTest test) {
        log.info("\n========================================");
        log.info("开始运行所有测试");
        log.info("========================================\n");
        
        int passedCount = 0;
        int failedCount = 0;
        
        try {
            test.test01_MarketData();
            passedCount++;
        } catch (Exception e) {
            log.error("测试1失败", e);
            failedCount++;
        }
        
        try {
            test.test02_AccountBalance();
            passedCount++;
        } catch (Exception e) {
            log.error("测试2失败", e);
            failedCount++;
        }
        
        try {
            test.test03_PlaceOrders();
            passedCount++;
        } catch (Exception e) {
            log.error("测试3失败", e);
            failedCount++;
        }
        
        try {
            test.test04_QueryOpenOrders();
            passedCount++;
        } catch (Exception e) {
            log.error("测试4失败", e);
            failedCount++;
        }
        
        try {
            test.test05_CancelOrder();
            passedCount++;
        } catch (Exception e) {
            log.error("测试5失败", e);
            failedCount++;
        }
        
        try {
            test.test06_BatchCancelOrders();
            passedCount++;
        } catch (Exception e) {
            log.error("测试6失败", e);
            failedCount++;
        }
        
        try {
            test.test07_CancelAllOrders();
            passedCount++;
        } catch (Exception e) {
            log.error("测试7失败", e);
            failedCount++;
        }
        
        try {
            test.test08_ConfigurationLoading();
            passedCount++;
        } catch (Exception e) {
            log.error("测试8失败", e);
            failedCount++;
        }
        
        try {
            test.test09_CompleteTrading();
            passedCount++;
        } catch (Exception e) {
            log.error("测试9失败", e);
            failedCount++;
        }
        
        try {
            test.test10_QueryPosition();
            passedCount++;
        } catch (Exception e) {
            log.error("测试10失败", e);
            failedCount++;
        }
        
        try {
            test.test11_QueryHistoricalOrders();
            passedCount++;
        } catch (Exception e) {
            log.error("测试11失败", e);
            failedCount++;
        }
        
        try {
            test.test12_ProfitLossStatistics();
            passedCount++;
        } catch (Exception e) {
            log.error("测试12失败", e);
            failedCount++;
        }
        
        log.info("\n========================================");
        log.info("所有测试完成");
        log.info("========================================");
        log.info("通过: {} 个", passedCount);
        log.info("失败: {} 个", failedCount);
        log.info("总计: {} 个", passedCount + failedCount);
        log.info("========================================");
    }
}
