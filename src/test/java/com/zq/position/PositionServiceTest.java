package com.zq.position;

import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PositionService 测试用例
 * 测试持仓管理、盈亏计算、可用资金计算
 */
@SpringBootTest
class PositionServiceTest {
    
    @Autowired
    private PositionService positionService;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private StrategyService strategyService;
    
    private static final String TEST_SYMBOL = "USDCUSDT";
    private static final StrategyConfig.Mode TEST_MODE = StrategyConfig.Mode.TESTNET;
    
    private double maxTotalInvest;
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Position.class);
        maxTotalInvest = strategyService.getStrategyConfig().getMaxTotalInvestUsdt();
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据
        mongoTemplate.remove(new Query(), Position.class);
    }
    
    /**
     * 测试：买入更新持仓（首次买入）
     */
    @Test
    void testUpdatePositionOnBuy_FirstTime() throws InterruptedException {
        double buyQty = 100.0;
        double buyPrice = 1.0000;
        
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, buyQty, buyPrice);
        
        // 等待虚拟线程完成
        Thread.sleep(200);
        
        // 验证持仓已创建
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(buyQty, position.getQuantity());
        assertEquals(buyPrice, position.getAvgBuyPrice());
        assertEquals(buyQty * buyPrice, position.getTotalInvestedUsdt());
    }
    
    /**
     * 测试：买入更新持仓（追加买入）
     */
    @Test
    void testUpdatePositionOnBuy_Additional() throws InterruptedException {
        // 首次买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        // 追加买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 50.0, 1.0010);
        Thread.sleep(200);
        
        // 验证持仓已更新
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(150.0, position.getQuantity());
        
        // 验证平均买入价格：(100*1.0000 + 50*1.0010) / 150 = 1.000333...
        double expectedAvgPrice = (100.0 * 1.0000 + 50.0 * 1.0010) / 150.0;
        assertEquals(expectedAvgPrice, position.getAvgBuyPrice(), 0.0001);
        
        double expectedInvested = 100.0 * 1.0000 + 50.0 * 1.0010;
        assertEquals(expectedInvested, position.getTotalInvestedUsdt(), 0.01);
    }
    
    /**
     * 测试：卖出更新持仓（部分卖出）
     */
    @Test
    void testUpdatePositionOnSell_Partial() throws InterruptedException {
        // 先买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        // 部分卖出
        positionService.updatePositionOnSell(TEST_SYMBOL, TEST_MODE, 30.0, 1.0005);
        Thread.sleep(200);
        
        // 验证持仓已更新
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(70.0, position.getQuantity());
        
        // 投入金额应该按比例减少：100 * (70/100) = 70
        assertEquals(70.0, position.getTotalInvestedUsdt(), 0.01);
    }
    
    /**
     * 测试：卖出更新持仓（全部卖出）
     */
    @Test
    void testUpdatePositionOnSell_All() throws InterruptedException {
        // 先买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        // 全部卖出
        positionService.updatePositionOnSell(TEST_SYMBOL, TEST_MODE, 100.0, 1.0005);
        Thread.sleep(200);
        
        // 验证持仓已清空
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        assertEquals(0.0, position.getQuantity());
        assertEquals(0.0, position.getTotalInvestedUsdt());
    }
    
    /**
     * 测试：计算未实现盈亏
     */
    @Test
    void testCalculateUnrealizedPnl() throws InterruptedException {
        // 买入
        double buyQty = 100.0;
        double buyPrice = 1.0000;
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, buyQty, buyPrice);
        Thread.sleep(200);
        
        // 当前价格上涨
        double currentPrice = 1.0010;
        double pnl = positionService.calculateUnrealizedPnl(TEST_SYMBOL, TEST_MODE, currentPrice);
        
        // 预期盈亏：100 * (1.0010 - 1.0000) = 0.1 USDT
        double expectedPnl = buyQty * (currentPrice - buyPrice);
        assertEquals(expectedPnl, pnl, 0.001);
    }
    
    /**
     * 测试：计算未实现盈亏（亏损）
     */
    @Test
    void testCalculateUnrealizedPnl_Loss() throws InterruptedException {
        // 买入
        double buyQty = 100.0;
        double buyPrice = 1.0000;
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, buyQty, buyPrice);
        Thread.sleep(200);
        
        // 当前价格下跌
        double currentPrice = 0.9995;
        double pnl = positionService.calculateUnrealizedPnl(TEST_SYMBOL, TEST_MODE, currentPrice);
        
        // 预期盈亏：100 * (0.9995 - 1.0000) = -0.05 USDT
        double expectedPnl = buyQty * (currentPrice - buyPrice);
        assertEquals(expectedPnl, pnl, 0.001);
        assertTrue(pnl < 0, "Should be negative (loss)");
    }
    
    /**
     * 测试：获取可用资金（无持仓）
     */
    @Test
    void testGetAvailableFunds_NoPosition() {
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        
        // 应该返回最大总投入金额
        assertEquals(maxTotalInvest, availableFunds);
    }
    
    /**
     * 测试：获取可用资金（有持仓）
     */
    @Test
    void testGetAvailableFunds_WithPosition() throws InterruptedException {
        // 买入100 USDT
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        
        // 可用资金 = 最大总投入 - 100
        assertEquals(maxTotalInvest - 100.0, availableFunds);
    }
    
    /**
     * 测试：获取可用资金（多次买入）
     */
    @Test
    void testGetAvailableFunds_MultipleBuys() throws InterruptedException {
        // 第一次买入100 USDT
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        // 第二次买入50 USDT
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 50.0, 1.0000);
        Thread.sleep(200);
        
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        
        // 可用资金 = 最大总投入 - 100 - 50
        assertEquals(maxTotalInvest - 150.0, availableFunds);
    }
    
    /**
     * 测试：获取可用资金（买入后卖出）
     */
    @Test
    void testGetAvailableFunds_AfterSell() throws InterruptedException {
        // 买入100 USDT
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        // 卖出50 USDC（释放一半资金）
        positionService.updatePositionOnSell(TEST_SYMBOL, TEST_MODE, 50.0, 1.0005);
        Thread.sleep(200);
        
        double availableFunds = positionService.getAvailableFunds(TEST_SYMBOL, TEST_MODE);
        
        // 可用资金 = 最大总投入 - 50
        assertEquals(maxTotalInvest - 50.0, availableFunds);
    }
    
    /**
     * 测试：查询持仓（不存在）
     */
    @Test
    void testGetPosition_NotFound() {
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNull(position);
    }
    
    /**
     * 测试：持仓计算方法
     */
    @Test
    void testPositionCalculations() throws InterruptedException {
        // 买入
        positionService.updatePositionOnBuy(TEST_SYMBOL, TEST_MODE, 100.0, 1.0000);
        Thread.sleep(200);
        
        Position position = positionService.getPosition(TEST_SYMBOL, TEST_MODE);
        assertNotNull(position);
        
        // 设置当前价格
        position.setCurrentPrice(1.0010);
        position.setUnrealizedPnl(0.10);
        
        // 测试持仓市值
        assertEquals(100.1, position.getPositionValue(), 0.01);
        
        // 测试盈亏百分比
        assertEquals(0.1, position.getUnrealizedPnlPercent(), 0.01);
    }
}
