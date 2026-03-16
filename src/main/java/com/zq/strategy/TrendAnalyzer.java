package com.zq.strategy;

import com.zq.stats.DepthStats;
import com.zq.stats.SpreadStats;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * 趋势分析器
 * 根据历史价格和深度统计数据，预测未来价格走势
 * 
 * 分析逻辑：
 * 1. 分析近期价格分布（最近N天的价格统计）
 * 2. 分析深度变化（买盘深度是否增强/减弱）
 * 3. 综合判断趋势：上涨、下跌、震荡
 */
@Component
@Slf4j
public class TrendAnalyzer {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    /**
     * 趋势方向
     */
    public enum Trend {
        RISING,      // 上涨趋势（价格走高）
        FALLING,     // 下跌趋势（价格走低）
        CONSOLIDATING // 震荡趋势（横盘）
    }
    
    /**
     * 趋势分析结果
     */
    @Data
    public static class TrendAnalysis {
        private Trend trend;                    // 趋势方向
        private double confidence;              // 置信度 (0-1)
        private double avgPriceLast3Days;       // 最近3天平均价格
        private double avgPriceLast7Days;       // 最近7天平均价格
        private double priceVolatility;         // 价格波动率
        private double avgSupportRatio;         // 平均支撑比率
        private String reason;                  // 判断理由
    }
    
    /**
     * 分析价格趋势
     * 
     * @param symbol 交易对
     * @param lookbackDays 回看天数
     * @return 趋势分析结果
     */
    public TrendAnalysis analyzeTrend(String symbol, int lookbackDays) {
        TrendAnalysis analysis = new TrendAnalysis();
        
        try {
            // 1. 获取最近N天的价格统计
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(lookbackDays);
            
            Query query = new Query(
                where("symbol").is(symbol)
                    .and("date").gte(startDate).lte(today)
            );
            
            List<SpreadStats> spreadStats = mongoTemplate.find(query, SpreadStats.class);
            
            if (spreadStats.isEmpty()) {
                log.warn("No spread stats found for trend analysis, symbol={}, lookbackDays={}", 
                    symbol, lookbackDays);
                // 返回默认震荡趋势
                analysis.setTrend(Trend.CONSOLIDATING);
                analysis.setConfidence(0.0);
                analysis.setReason("无历史数据");
                return analysis;
            }
            
            // 2. 计算最近3天和7天的加权平均价格
            double avgPrice3d = calculateWeightedAveragePrice(spreadStats, today.minusDays(3), today);
            double avgPrice7d = calculateWeightedAveragePrice(spreadStats, today.minusDays(7), today);
            
            analysis.setAvgPriceLast3Days(avgPrice3d);
            analysis.setAvgPriceLast7Days(avgPrice7d);
            
            // 3. 计算价格波动率
            double volatility = calculatePriceVolatility(spreadStats);
            analysis.setPriceVolatility(volatility);
            
            // 4. 获取深度统计，计算平均支撑比率
            List<DepthStats> depthStats = mongoTemplate.find(query, DepthStats.class);
            double avgSupportRatio = calculateAverageSupportRatio(depthStats);
            analysis.setAvgSupportRatio(avgSupportRatio);
            
            // 5. 判断趋势
            determineTrend(analysis, avgPrice3d, avgPrice7d, volatility, avgSupportRatio);
            
            log.info("Trend analysis: trend={}, confidence={}, avgPrice3d={}, avgPrice7d={}, volatility={}, support={}, reason={}", 
                analysis.getTrend(), 
                String.format("%.2f", analysis.getConfidence()),
                String.format("%.6f", avgPrice3d),
                String.format("%.6f", avgPrice7d),
                String.format("%.4f%%", volatility * 100),
                String.format("%.2f", avgSupportRatio),
                analysis.getReason());
            
        } catch (Exception e) {
            log.error("Error analyzing trend", e);
            analysis.setTrend(Trend.CONSOLIDATING);
            analysis.setConfidence(0.0);
            analysis.setReason("分析异常");
        }
        
        return analysis;
    }
    
    /**
     * 计算加权平均价格
     * 权重 = count（出现次数越多，权重越大）
     */
    private double calculateWeightedAveragePrice(List<SpreadStats> stats, LocalDate startDate, LocalDate endDate) {
        double totalWeightedPrice = 0.0;
        int totalCount = 0;
        
        for (SpreadStats stat : stats) {
            if (stat.getDate().isBefore(startDate) || stat.getDate().isAfter(endDate)) {
                continue;
            }
            
            // 使用中间价 (bid + ask) / 2
            double midPrice = (stat.getBidPrice() + stat.getAskPrice()) / 2.0;
            int count = stat.getCount();
            
            totalWeightedPrice += midPrice * count;
            totalCount += count;
        }
        
        return totalCount > 0 ? totalWeightedPrice / totalCount : 0.0;
    }
    
    /**
     * 计算价格波动率（标准差）
     */
    private double calculatePriceVolatility(List<SpreadStats> stats) {
        if (stats.isEmpty()) {
            return 0.0;
        }
        
        // 计算平均价格
        double totalPrice = 0.0;
        int totalCount = 0;
        
        for (SpreadStats stat : stats) {
            double midPrice = (stat.getBidPrice() + stat.getAskPrice()) / 2.0;
            totalPrice += midPrice * stat.getCount();
            totalCount += stat.getCount();
        }
        
        double avgPrice = totalPrice / totalCount;
        
        // 计算方差
        double variance = 0.0;
        for (SpreadStats stat : stats) {
            double midPrice = (stat.getBidPrice() + stat.getAskPrice()) / 2.0;
            double diff = midPrice - avgPrice;
            variance += diff * diff * stat.getCount();
        }
        
        variance = variance / totalCount;
        
        // 返回标准差除以平均价格（相对波动率）
        return Math.sqrt(variance) / avgPrice;
    }
    
    /**
     * 计算平均支撑比率
     */
    private double calculateAverageSupportRatio(List<DepthStats> depthStats) {
        if (depthStats.isEmpty()) {
            return 0.8; // 默认值
        }
        
        double totalWeightedRatio = 0.0;
        int totalCount = 0;
        
        for (DepthStats stat : depthStats) {
            // 从supportRatioBucket解析出中间值
            // 例如 "0.6-0.7" -> 0.65
            String bucket = stat.getSupportRatioBucket();
            double midRatio = parseMidValueFromBucket(bucket);
            
            totalWeightedRatio += midRatio * stat.getCount();
            totalCount += stat.getCount();
        }
        
        return totalCount > 0 ? totalWeightedRatio / totalCount : 0.8;
    }
    
    /**
     * 从区间桶解析中间值
     * 例如 "0.6-0.7" -> 0.65
     */
    private double parseMidValueFromBucket(String bucket) {
        try {
            String[] parts = bucket.split("-");
            double lower = Double.parseDouble(parts[0]);
            double upper = Double.parseDouble(parts[1]);
            return (lower + upper) / 2.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * 判断趋势
     * 
     * 判断规则：
     * 1. 如果3天均价 > 7天均价，且差距 > 0.02%，判断为上涨
     * 2. 如果3天均价 < 7天均价，且差距 > 0.02%，判断为下跌
     * 3. 否则判断为震荡
     * 4. 支撑比率低（< 0.5）增加下跌置信度
     * 5. 波动率高（> 0.1%）降低置信度
     */
    private void determineTrend(TrendAnalysis analysis, double avgPrice3d, double avgPrice7d, 
                                double volatility, double avgSupportRatio) {
        
        // 价格变化百分比
        double priceChange = (avgPrice3d - avgPrice7d) / avgPrice7d;
        
        // 基础置信度
        double confidence = 0.5;
        
        // 判断趋势
        if (priceChange > 0.0002) {  // 上涨超过0.02%
            analysis.setTrend(Trend.RISING);
            confidence = Math.min(0.9, 0.5 + Math.abs(priceChange) * 100);
            analysis.setReason(String.format("近3天均价(%.6f)高于7天均价(%.6f) %.4f%%", 
                avgPrice3d, avgPrice7d, priceChange * 100));
            
        } else if (priceChange < -0.0002) {  // 下跌超过0.02%
            analysis.setTrend(Trend.FALLING);
            confidence = Math.min(0.9, 0.5 + Math.abs(priceChange) * 100);
            analysis.setReason(String.format("近3天均价(%.6f)低于7天均价(%.6f) %.4f%%", 
                avgPrice3d, avgPrice7d, priceChange * 100));
            
            // 支撑比率低，增加下跌置信度
            if (avgSupportRatio < 0.5) {
                confidence += 0.1;
                analysis.setReason(analysis.getReason() + ", 且买盘支撑弱");
            }
            
        } else {
            analysis.setTrend(Trend.CONSOLIDATING);
            confidence = 0.6;
            analysis.setReason(String.format("价格震荡，3天均价与7天均价接近(%.6f vs %.6f)", 
                avgPrice3d, avgPrice7d));
        }
        
        // 波动率过高，降低置信度
        if (volatility > 0.001) {  // 波动率 > 0.1%
            confidence *= 0.8;
            analysis.setReason(analysis.getReason() + ", 但波动较大");
        }
        
        // 限制置信度范围
        confidence = Math.max(0.1, Math.min(0.95, confidence));
        analysis.setConfidence(confidence);
    }
}

