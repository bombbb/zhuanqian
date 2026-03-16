package com.zq.strategy;

import com.zq.api.BinanceApiService;
import com.zq.order.Order;
import com.zq.order.OrderService;
import com.zq.stats.SpreadStats;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * 动态价格调整器
 * 根据趋势分析结果，动态调整 referencePrice/maxBuyPrice
 * 
 * 调整策略：
 * 1. 上涨趋势：提高 maxBuyPrice（不那么保守，价格稍高也可以买）
 * 2. 下跌趋势：降低 maxBuyPrice（更保守，等更低价格才买）+ 撤销所有买单
 * 3. 震荡趋势：保持当前价格或微调
 * 
 * 调整幅度受置信度影响：置信度越高，调整幅度越大
 */
@Component
@Slf4j
public class DynamicPriceAdjuster {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    @Resource
    private TrendAnalyzer trendAnalyzer;
    
    @Resource
    private StrategyService strategyService;
    
    @Resource
    private OrderService orderService;
    
    @Resource
    private BinanceApiService binanceApiService;
    
    /**
     * 调整结果
     */
    public static class AdjustmentResult {
        private double oldPrice;
        private double newPrice;
        private double adjustmentPercent;
        private TrendAnalyzer.Trend trend;
        private double confidence;
        private String reason;
        
        public double getOldPrice() { return oldPrice; }
        public void setOldPrice(double oldPrice) { this.oldPrice = oldPrice; }
        
        public double getNewPrice() { return newPrice; }
        public void setNewPrice(double newPrice) { this.newPrice = newPrice; }
        
        public double getAdjustmentPercent() { return adjustmentPercent; }
        public void setAdjustmentPercent(double adjustmentPercent) { this.adjustmentPercent = adjustmentPercent; }
        
        public TrendAnalyzer.Trend getTrend() { return trend; }
        public void setTrend(TrendAnalyzer.Trend trend) { this.trend = trend; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * 分析并调整参考价格
     * 
     * @param configId 策略配置ID
     * @param lookbackDays 回看天数（用于趋势分析）
     * @param currentMarketPrice 当前市场价格（用于参考）
     * @return 调整结果
     */
    public AdjustmentResult analyzeAndAdjustPrice(String configId, int lookbackDays, double currentMarketPrice) {
        AdjustmentResult result = new AdjustmentResult();
        
        try {
            // 1. 获取当前配置
            StrategyConfig config = mongoTemplate.findById(configId, StrategyConfig.class);
            if (config == null) {
                log.error("Strategy config not found: {}", configId);
                return null;
            }
            
            // 2. 获取当前最高买入价
            double currentMaxBuyPrice = config.getMaxBuyPrice();
            
            if (currentMaxBuyPrice <= 0) {
                currentMaxBuyPrice = 1.0005; // 默认值
            }
            
            result.setOldPrice(currentMaxBuyPrice);
            
            // 3. 分析趋势
            TrendAnalyzer.TrendAnalysis analysis = trendAnalyzer.analyzeTrend(
                config.getSymbol(), 
                lookbackDays
            );
            
            result.setTrend(analysis.getTrend());
            result.setConfidence(analysis.getConfidence());
            
            // 4. 根据趋势调整最高买入价
            double newMaxBuyPrice = currentMaxBuyPrice;
            boolean trendAdjusted = false;
            
            if (analysis.getConfidence() >= config.getMinConfidenceForAdjustment()) {
                newMaxBuyPrice = calculateNewMaxBuyPrice(
                    currentMaxBuyPrice, 
                    currentMarketPrice,
                    analysis
                );
                trendAdjusted = true;
            } else {
                log.info("Confidence too low, skip trend-based adjustment: confidence={} < threshold={}",
                    String.format("%.2f", analysis.getConfidence()),
                    String.format("%.2f", config.getMinConfidenceForAdjustment()));
            }
            
            // 4.1 近期价格区间约束（更保守的上限）
            if (config.isEnableRecentPriceBandAdjust()) {
                Double bandPrice = calculateRecentBandMaxBuyPrice(
                    config.getSymbol(),
                    config.getRecentPriceLookbackDays(),
                    config.getRecentPricePercentile(),
                    config.getRecentPriceSafetyMargin(),
                    config.getMaxBuyPriceCeiling()
                );
                if (bandPrice != null && bandPrice > 0) {
                    if (trendAdjusted) {
                        newMaxBuyPrice = Math.min(newMaxBuyPrice, bandPrice);
                    } else {
                        newMaxBuyPrice = bandPrice;
                    }
                }
            } else if (config.getMaxBuyPriceCeiling() > 0) {
                newMaxBuyPrice = Math.min(newMaxBuyPrice, config.getMaxBuyPriceCeiling());
            }

            // 4.2 避免上限过低导致长期无法下单（以当前价格为地板）
            double floorMargin = Math.max(0.00002, config.getRecentPriceSafetyMargin() * 2);
            double floorPrice = currentMarketPrice - floorMargin;
            if (newMaxBuyPrice < floorPrice) {
                newMaxBuyPrice = floorPrice;
            }
            if (config.getMaxBuyPriceCeiling() > 0) {
                newMaxBuyPrice = Math.min(newMaxBuyPrice, config.getMaxBuyPriceCeiling());
            }
            
            result.setNewPrice(newMaxBuyPrice);
            result.setAdjustmentPercent((newMaxBuyPrice - currentMaxBuyPrice) / currentMaxBuyPrice * 100);
            
            // 5. 生成调整理由
            String reason = generateAdjustmentReason(analysis, currentMaxBuyPrice, newMaxBuyPrice);
            result.setReason(reason);
            
            // 6. 如果价格有变化，更新数据库
            if (Math.abs(newMaxBuyPrice - currentMaxBuyPrice) > 0.000001) {
                // 使用StrategyService更新（会自动清空缓存）
                strategyService.updateMaxBuyPrice(configId, newMaxBuyPrice);
                
                log.info("Price adjusted: configId={}, trend={}, confidence={}, oldMaxBuyPrice={}, newMaxBuyPrice={}, change={}%, reason={}", 
                    configId,
                    analysis.getTrend(),
                    String.format("%.2f", analysis.getConfidence()),
                    String.format("%.6f", currentMaxBuyPrice),
                    String.format("%.6f", newMaxBuyPrice),
                    String.format("%.4f", result.getAdjustmentPercent()),
                    reason);
                
                // 7. 如果是下跌趋势且价格下调，撤销所有买单
                if (analysis.getTrend() == TrendAnalyzer.Trend.FALLING && newMaxBuyPrice < currentMaxBuyPrice) {
                    cancelAllBuyOrders(config.getSymbol(), config.getMode());
                }
            } else {
                log.info("No price adjustment needed: configId={}, trend={}, confidence={}, currentMaxBuyPrice={}", 
                    configId,
                    analysis.getTrend(),
                    String.format("%.2f", analysis.getConfidence()),
                    String.format("%.6f", currentMaxBuyPrice));
            }
            
        } catch (Exception e) {
            log.error("Error adjusting price", e);
            return null;
        }
        
        return result;
    }
    
    /**
     * 计算新的参考价格
     * 
     * 调整规则：
     * 1. 上涨趋势：提高价格，最多提高 0.05% * confidence
     * 2. 下跌趋势：降低价格，最多降低 0.05% * confidence
     * 3. 震荡趋势：微调至当前市场价格
     * 
     * 限制：
     * - 调整后的价格不能偏离当前市场价太多（最多±0.1%）
     * - 最小调整幅度 0.01%（避免频繁小幅调整）
     */
    private double calculateNewMaxBuyPrice(double currentMaxBuyPrice, double currentMarketPrice, 
                                               TrendAnalyzer.TrendAnalysis analysis) {
        
        double newPrice = currentMaxBuyPrice;
        double maxAdjustmentPercent = 0.0005; // 最大调整幅度 0.05%
        
        switch (analysis.getTrend()) {
            case RISING:
                // 上涨趋势：提高最高买入价（不那么保守）
                // 但不要超过当前市场价太多
                double increaseAmount = currentMaxBuyPrice * maxAdjustmentPercent * analysis.getConfidence();
                newPrice = currentMaxBuyPrice + increaseAmount;
                
                // 限制：不超过当前市场价 + 0.1%
                double upperLimit = currentMarketPrice * 1.001;
                newPrice = Math.min(newPrice, upperLimit);
                break;
                
            case FALLING:
                // 下跌趋势：降低最高买入价（更保守）
                // 等待更低的价格才买入
                double decreaseAmount = currentMaxBuyPrice * maxAdjustmentPercent * analysis.getConfidence();
                newPrice = currentMaxBuyPrice - decreaseAmount;
                
                // 限制：不低于当前市场价 - 0.1%
                double lowerLimit = currentMarketPrice * 0.999;
                newPrice = Math.max(newPrice, lowerLimit);
                break;
                
            case CONSOLIDATING:
                // 震荡趋势：微调至接近当前市场价
                // 如果当前设置偏离市场价较多，逐步调整
                double diff = currentMarketPrice - currentMaxBuyPrice;
                if (Math.abs(diff) > currentMaxBuyPrice * 0.0002) {  // 偏离 > 0.02%
                    // 调整一半的差距
                    newPrice = currentMaxBuyPrice + diff * 0.3;
                }
                break;
        }
        
        // 最小调整幅度检查：如果调整太小（< 0.01%），则不调整
        double adjustmentPercent = Math.abs(newPrice - currentMaxBuyPrice) / currentMaxBuyPrice;
        if (adjustmentPercent < 0.0001) {  // < 0.01%
            newPrice = currentMaxBuyPrice;
        }
        
        return newPrice;
    }
    
    /**
     * 基于最近统计数据计算最高买入价上限（分位数）
     * 使用最近N天的卖价(ask)分布，取指定分位数并下调安全边际
     */
    private Double calculateRecentBandMaxBuyPrice(String symbol,
                                                  int lookbackDays,
                                                  double percentile,
                                                  double safetyMargin,
                                                  double hardCeiling) {
        try {
            if (lookbackDays <= 0 || percentile <= 0 || percentile >= 1) {
                return null;
            }
            
            LocalDate startDate = LocalDate.now().minusDays(lookbackDays);
            Query query = new Query(where("symbol").is(symbol).and("date").gte(startDate));
            List<SpreadStats> stats = mongoTemplate.find(query, SpreadStats.class);
            if (stats.isEmpty()) {
                return null;
            }
            
            // 按中间价排序，做加权分位数
            stats.sort(Comparator.comparingDouble(SpreadStats::getAskPrice));
            
            long totalCount = 0;
            for (SpreadStats stat : stats) {
                totalCount += stat.getCount() != null ? stat.getCount() : 0;
            }
            if (totalCount == 0) {
                return null;
            }
            
            long threshold = (long) Math.ceil(totalCount * percentile);
            long cumulative = 0;
            double percentilePrice = 0.0;
            
            for (SpreadStats stat : stats) {
                cumulative += stat.getCount() != null ? stat.getCount() : 0;
                if (cumulative >= threshold) {
                    percentilePrice = stat.getAskPrice();
                    break;
                }
            }
            
            if (percentilePrice <= 0) {
                return null;
            }
            
            double bandPrice = percentilePrice - safetyMargin;
            if (hardCeiling > 0) {
                bandPrice = Math.min(bandPrice, hardCeiling);
            }
            
            return bandPrice;
        } catch (Exception e) {
            log.warn("Failed to calculate recent band max buy price: symbol={}", symbol, e);
            return null;
        }
    }
    
    /**
     * 生成调整理由
     */
    private String generateAdjustmentReason(TrendAnalyzer.TrendAnalysis analysis, 
                                           double oldPrice, double newPrice) {
        StringBuilder reason = new StringBuilder();
        
        reason.append(analysis.getReason()).append("; ");
        
        if (newPrice > oldPrice) {
            reason.append("提高价格阈值，可以在稍高价格买入");
        } else if (newPrice < oldPrice) {
            reason.append("降低价格阈值，等待更低价格买入");
        } else {
            reason.append("维持当前价格阈值");
        }
        
        return reason.toString();
    }
    
    /**
     * 撤销所有买单
     * 当价格下跌趋势明显时，撤销所有挂单，避免在不利价格成交
     * 
     * @param symbol 交易对
     * @param mode 运行模式
     */
    private void cancelAllBuyOrders(String symbol, StrategyConfig.Mode mode) {
        try {
            // 查询所有挂单（BUY方向）
            List<Order> buyOrders = orderService.getAllOrders(symbol, mode).stream()
                .filter(order -> "BUY".equals(order.getSide()))
                .filter(order -> order.getStatus() == Order.OrderStatus.NEW || 
                               order.getStatus() == Order.OrderStatus.SUBMITTED)
                .toList();
            
            if (buyOrders.isEmpty()) {
                log.info("No buy orders to cancel for symbol={}, mode={}", symbol, mode);
                return;
            }
            
            log.warn("⚠️ 价格下跌趋势，开始撤销所有买单: symbol={}, mode={}, count={}", 
                symbol, mode, buyOrders.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Order order : buyOrders) {
                try {
                    // 调用币安API撤单
                    binanceApiService.cancelOrder(symbol, order.getOrderId());
                    
                    // 更新本地订单状态
                    orderService.markOrderCanceled(order.getId());
                    
                    successCount++;
                    log.info("✓ Buy order canceled: orderId={}, price={}, qty={}", 
                        order.getOrderId(), order.getPrice(), order.getQuantity());
                } catch (Exception e) {
                    failCount++;
                    log.error("✗ Failed to cancel buy order: orderId={}, error={}", 
                        order.getOrderId(), e.getMessage());
                }
            }
            
            log.info("Buy orders cancellation completed: success={}, failed={}, total={}", 
                successCount, failCount, buyOrders.size());
            
        } catch (Exception e) {
            log.error("Error canceling buy orders: symbol={}, mode={}", symbol, mode, e);
        }
    }
    
    /**
     * 同时调整其他相关参数
     * 
     * 根据趋势和市场状况，可能需要调整：
     * 1. minProfitTick - 下跌趋势时可以降低利润要求，增加成交机会
     * 2. minSupportRatio - 根据平均支撑比率调整
     * 3. maxHoldSeconds - 波动大时缩短持仓时间
     */
    public void adjustRelatedParameters(String configId, TrendAnalyzer.TrendAnalysis analysis) {
        try {
            StrategyConfig config = mongoTemplate.findById(configId, StrategyConfig.class);
            if (config == null) {
                return;
            }
            
            Query query = new Query(where("_id").is(configId));
            Update update = new Update();
            boolean hasUpdates = false;
            
            // 1. 调整 minProfitTick
            double currentMinProfit = config.getMinProfitTick();
            double newMinProfit = currentMinProfit;
            
            if (analysis.getTrend() == TrendAnalyzer.Trend.FALLING && analysis.getConfidence() > 0.7) {
                // 下跌趋势且置信度高：降低利润要求10%，增加成交机会
                newMinProfit = currentMinProfit * 0.9;
                update.set("minProfitTick", newMinProfit);
                hasUpdates = true;
                log.info("Adjusted minProfitTick: {} -> {} (下跌趋势，降低利润要求)", 
                    String.format("%.6f", currentMinProfit), 
                    String.format("%.6f", newMinProfit));
            } else if (analysis.getTrend() == TrendAnalyzer.Trend.RISING && analysis.getConfidence() > 0.7) {
                // 上涨趋势且置信度高：可以提高利润要求
                newMinProfit = currentMinProfit * 1.1;
                update.set("minProfitTick", newMinProfit);
                hasUpdates = true;
                log.info("Adjusted minProfitTick: {} -> {} (上涨趋势，提高利润要求)", 
                    String.format("%.6f", currentMinProfit), 
                    String.format("%.6f", newMinProfit));
            }
            
            // 2. 调整 minSupportRatio
            double currentMinSupport = config.getMinSupportRatio();
            double avgSupport = analysis.getAvgSupportRatio();
            
            // 如果平均支撑比率明显不同，调整阈值
            if (Math.abs(avgSupport - currentMinSupport) > 0.1 && avgSupport > 0.3) {
                double newMinSupport = avgSupport * 0.9; // 设置为平均值的90%
                update.set("minSupportRatio", newMinSupport);
                hasUpdates = true;
                log.info("Adjusted minSupportRatio: {} -> {} (根据历史平均支撑比率)", 
                    String.format("%.2f", currentMinSupport), 
                    String.format("%.2f", newMinSupport));
            }
            
            // 3. 调整 maxHoldSeconds（波动大时缩短持仓）
            int currentMaxHold = config.getMaxHoldSeconds();
            if (analysis.getPriceVolatility() > 0.001) {  // 波动率 > 0.1%
                int newMaxHold = (int)(currentMaxHold * 0.8); // 缩短20%
                update.set("maxHoldSeconds", newMaxHold);
                hasUpdates = true;
                log.info("Adjusted maxHoldSeconds: {} -> {} (波动较大，缩短持仓时间)", 
                    currentMaxHold, newMaxHold);
            }
            
            // 执行更新
            if (hasUpdates) {
                mongoTemplate.updateFirst(query, update, StrategyConfig.class);
                log.info("Related parameters adjusted for configId={}", configId);
            }
            
        } catch (Exception e) {
            log.error("Error adjusting related parameters", e);
        }
    }
}
