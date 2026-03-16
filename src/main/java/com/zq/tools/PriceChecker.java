package com.zq.tools;

import com.zq.api.BinanceApiService;
import com.zq.api.TickerData;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * 价格检查工具
 * 用于检查正式网的 bid/ask 价格并分析下单条件
 * 
 * 使用方法：
 * java -cp ... com.zq.tools.PriceChecker
 * 或者作为 Spring Boot 应用运行
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.zq")
@Slf4j
public class PriceChecker implements CommandLineRunner {
    
    private final StrategyService strategyService;
    private final MongoTemplate mongoTemplate;
    
    // 正式网 API URL（用于获取行情）
    private static final String PRODUCTION_API_URL = "https://api.binance.com";
    
    public PriceChecker(StrategyService strategyService, 
                       MongoTemplate mongoTemplate) {
        this.strategyService = strategyService;
        this.mongoTemplate = mongoTemplate;
    }
    
    @Override
    public void run(String... args) {
        try {
            log.info("========================================");
            log.info("价格检查工具启动");
            log.info("========================================");
            
            // 获取当前启用的配置（enabled=true）
            StrategyConfig config = strategyService.getStrategyConfig();
            if (config == null) {
                log.error("未找到启用的策略配置（enabled=true）");
                return;
            }
            
            log.info("当前配置ID: {}", config.getId());
            log.info("当前模式: {}", config.getMode());
            log.info("是否启用: {}", config.isEnabled());
            
            String symbol = config.getSymbol();
            log.info("检查交易对: {}", symbol);
            
            // 创建正式网 API 服务（只用于查询，不需要密钥）
            BinanceApiService productionApi = new BinanceApiService(
                PRODUCTION_API_URL, "", ""
            );
            
            // 获取正式网价格
            log.info("正在从正式网获取价格...");
            TickerData ticker = productionApi.getBookTicker(symbol);
            
            double bid = ticker.getBestBidPrice().doubleValue();
            double ask = ticker.getBestAskPrice().doubleValue();
            double midPrice = (bid + ask) / 2.0;
            double spread = ask - bid;
            
            log.info("========================================");
            log.info("正式网价格信息:");
            log.info("  Symbol: {}", symbol);
            log.info("  Bid (买价): {}", String.format("%.6f", bid));
            log.info("  Ask (卖价): {}", String.format("%.6f", ask));
            log.info("  Mid (中间价): {}", String.format("%.6f", midPrice));
            log.info("  Spread (价差): {}", String.format("%.6f", spread));
            log.info("========================================");
            
            // 显示当前配置
            log.info("当前配置:");
            
            double refPrice = config.getReferencePrice();
            double maxBuyPrice = config.getMaxBuyPrice();
            double maxBuyAmount = config.getMaxBuyAmountUsdt();
            
            log.info("  referencePrice (参考价格/锚定价): {}", String.format("%.6f", refPrice));
            log.info("  maxBuyPrice (最高买入价): {}", String.format("%.6f", maxBuyPrice));
            log.info("  maxBuyAmountUsdt (单次最大买入): {} USDT", String.format("%.2f", maxBuyAmount));
            log.info("  minProfitTick (最小利润): {}", String.format("%.6f", config.getMinProfitTick()));
            log.info("  maxTotalInvestUsdt (最大总投入): {} USDT", String.format("%.2f", config.getMaxTotalInvestUsdt()));
            log.info("========================================");
            
            // 分析为什么没有下单
            double lastPrice = ticker.getLastPrice().doubleValue();
            
            // 计算价格偏离（相对于参考价格）
            double deviation = (refPrice - lastPrice) / refPrice;
            double deviationPercent = deviation * 100;
            
            // 条件1: 价格低于最高买入价
            boolean priceCondition = lastPrice < maxBuyPrice;
            
            // 条件2: 利润空间足够（简化逻辑）
            boolean profitCondition = config.getMinProfitTick() >= 0.0001;
            
            // 计算可能的买入金额
            double buyRatio = Math.min(deviation / 0.001, 1.0);
            buyRatio = Math.max(buyRatio, 0.3);
            double calculatedAmount = maxBuyAmount * buyRatio;
            
            log.info("市场分析:");
            log.info("  当前价格: {}", String.format("%.6f", lastPrice));
            log.info("  参考价格: {}", String.format("%.6f", refPrice));
            log.info("  最高买入价: {}", String.format("%.6f", maxBuyPrice));
            log.info("  价格偏离: {} ({}%)", 
                String.format("%.6f", refPrice - lastPrice),
                String.format("%.4f", deviationPercent));
            log.info("  距离上限: {} ({}%)", 
                String.format("%.6f", maxBuyPrice - lastPrice),
                String.format("%.4f", (maxBuyPrice - lastPrice) / maxBuyPrice * 100));
            log.info("========================================");
            
            log.info("下单条件分析:");
            log.info("  ✓ 条件1 - 价格低于最高买入价 (lastPrice < maxBuyPrice): {} ({} < {})", 
                priceCondition ? "✓满足" : "✗不满足", 
                String.format("%.6f", lastPrice), 
                String.format("%.6f", maxBuyPrice));
            log.info("  ✓ 条件2 - 利润要求合理 (minProfitTick >= 0.0001): {} ({} >= 0.0001)", 
                profitCondition ? "✓满足" : "✗不满足",
                String.format("%.6f", config.getMinProfitTick()));
            log.info("  ✓ 条件3 - 支撑比率足够: 待实现（当前使用固定值0.8）");
            log.info("========================================");
            
            if (priceCondition && profitCondition) {
                log.info("✓ 所有条件满足，可以下单！");
                log.info("  预期买入金额: {} USDT (基于 {}% 偏离)", 
                    String.format("%.2f", calculatedAmount),
                    String.format("%.2f", buyRatio * 100));
                log.info("  预期卖出价: {}", String.format("%.6f", lastPrice + config.getMinProfitTick()));
                log.info("  预期利润空间: {}", String.format("%.6f", config.getMinProfitTick()));
            } else {
                log.warn("✗ 暂不满足下单条件:");
                if (!priceCondition) {
                    log.warn("  - 价格过高：当前价格 {} 不低于最高买入价 {}", 
                        String.format("%.6f", lastPrice), 
                        String.format("%.6f", maxBuyPrice));
                    log.info("    建议：适当提高 maxBuyPrice");
                }
                if (!profitCondition) {
                    log.warn("  - 利润要求过低：{} < 0.0001", 
                        String.format("%.6f", config.getMinProfitTick()));
                    log.info("    建议：设置 minProfitTick >= 0.0001");
                }
            }
            log.info("========================================");
            
            // 配置建议
            log.info("配置建议:");
            log.info("  referencePrice: 1.0 (稳定币锚定价格，作为参考)");
            log.info("  maxBuyPrice: 1.0005 (最高买入价，低于此价才买入)");
            log.info("  maxBuyAmountUsdt: {} USDT (当前值)", String.format("%.2f", maxBuyAmount));
            log.info("  minProfitTick: 0.0001 (最小利润0.01%)");
            log.info("  如果想增加交易频率，可以:");
            log.info("    1. 适当提高 maxBuyPrice (如 1.001)");
            log.info("    2. 降低 minProfitTick (如 0.00005)");
            log.info("========================================");
            
            // 更新配置（如果指定了update参数）
            if (args.length > 0 && "update".equals(args[0])) {
                double suggestedRefPrice = 1.0;
                double suggestedMaxBuyPrice = 1.0005;
                double suggestedMaxBuy = 20.0;
                
                log.info("更新配置 {} ...", config.getId());
                updateConfig(config.getId(), suggestedRefPrice, suggestedMaxBuyPrice, suggestedMaxBuy);
                log.info("✅ 配置已更新:");
                log.info("  referencePrice = {}", suggestedRefPrice);
                log.info("  maxBuyPrice = {}", suggestedMaxBuyPrice);
                log.info("  maxBuyAmountUsdt = {} USDT", suggestedMaxBuy);
                log.info("提示：需要重启应用才能生效");
            } else {
                log.info("提示：要更新配置，请运行: ./check-price.sh update");
            }
            
        } catch (Exception e) {
            log.error("价格检查失败", e);
        }
    }
    
    /**
     * 更新配置
     */
    private void updateConfig(String configId, double referencePrice, double maxBuyPrice, double maxBuyAmountUsdt) {
        Query query = new Query(Criteria.where("_id").is(configId));
        Update update = new Update()
            .set("referencePrice", referencePrice)
            .set("maxBuyPrice", maxBuyPrice)
            .set("maxBuyAmountUsdt", maxBuyAmountUsdt);
        mongoTemplate.updateFirst(query, update, StrategyConfig.class);
    }
    
    public static void main(String[] args) {
        SpringApplication.run(PriceChecker.class, args);
    }
}
