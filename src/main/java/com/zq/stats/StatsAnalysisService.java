package com.zq.stats;

import com.zq.config.ConfigChangeLog;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * 统计分析服务
 * 基于历史统计数据，自动分析并触发配置变更
 */
@Service
@Slf4j
public class StatsAnalysisService {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    @Resource
    private StrategyService strategyService;
    
    /**
     * 分析价差统计，建议 minProfitTick 调整
     * 
     * 算法：
     * 1. 统计最近N天最常见的价差（bid-ask spread）
     * 2. 取出现频率 top 20% 的价差
     * 3. 建议 minProfitTick = 最小常见价差 * 1.2（留20%安全边际）
     * 
     * @param symbol 交易对
     * @param days 回看天数
     * @return 建议的 minProfitTick，如果数据不足返回 null
     */
    public Double analyzeOptimalMinProfitTick(String symbol, int days) {
        try {
            LocalDate startDate = LocalDate.now().minusDays(days);
            
            // 查询最近N天的价差统计，按出现次数降序排序
            Criteria criteria = Criteria.where("symbol").is(symbol)
                .and("date").gte(startDate);
            
            Aggregation aggregation = newAggregation(
                match(criteria),
                sort(Sort.by(Sort.Direction.DESC, "count")),
                limit(100)  // 取前100条
            );
            
            AggregationResults<SpreadStats> results = mongoTemplate.aggregate(
                aggregation, "spread_stats", SpreadStats.class);
            
            List<SpreadStats> spreadList = results.getMappedResults();
            
            if (spreadList.isEmpty()) {
                log.warn("No spread stats found for analysis: symbol={}, days={}", symbol, days);
                return null;
            }
            
            // 计算总出现次数
            long totalCount = spreadList.stream()
                .mapToLong(s -> s.getCount() != null ? s.getCount() : 0)
                .sum();
            
            // 取累计占比达到20%的最小价差
            long cumulativeCount = 0;
            double minSpread = Double.MAX_VALUE;
            
            for (SpreadStats stats : spreadList) {
                cumulativeCount += (stats.getCount() != null ? stats.getCount() : 0);
                double spread = stats.getSpread();
                
                if (spread > 0 && spread < minSpread) {
                    minSpread = spread;
                }
                
                // 累计达到20%，停止
                if (cumulativeCount >= totalCount * 0.2) {
                    break;
                }
            }
            
            // 建议值 = 最小常见价差 * 1.2（留20%安全边际）
            double suggestedMinProfitTick = minSpread * 1.2;
            
            log.info("Spread analysis completed: symbol={}, days={}, totalSamples={}, minSpread={}, suggested={}", 
                symbol, days, spreadList.size(), 
                String.format("%.6f", minSpread), 
                String.format("%.6f", suggestedMinProfitTick));
            
            return suggestedMinProfitTick;
            
        } catch (Exception e) {
            log.error("Failed to analyze optimal minProfitTick: symbol={}", symbol, e);
            return null;
        }
    }
    
    /**
     * 分析深度统计，建议 minSupportRatio 调整
     * 
     * 算法：
     * 1. 统计最近N天各支撑比率区间的出现次数
     * 2. 找出出现频率最高的支撑比率区间
     * 3. 建议 minSupportRatio = 该区间下限 - 0.1（略低于常见值，增加交易机会）
     * 
     * @param symbol 交易对
     * @param days 回看天数
     * @return 建议的 minSupportRatio，如果数据不足返回 null
     */
    public Double analyzeOptimalMinSupportRatio(String symbol, int days) {
        try {
            LocalDate startDate = LocalDate.now().minusDays(days);
            
            // 按支撑比率区间分组，统计出现次数
            Criteria criteria = Criteria.where("symbol").is(symbol)
                .and("date").gte(startDate);
            
            Aggregation aggregation = newAggregation(
                match(criteria),
                group("supportRatioBucket").sum("count").as("totalCount"),
                sort(Sort.by(Sort.Direction.DESC, "totalCount")),
                limit(10)
            );
            
            AggregationResults<Map<String, Object>> results = mongoTemplate.aggregate(
                aggregation, "depth_stats", (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            List<Map<String, Object>> bucketList = results.getMappedResults();
            
            if (bucketList.isEmpty()) {
                log.warn("No depth stats found for analysis: symbol={}, days={}", symbol, days);
                return null;
            }
            
            // 取出现最多的支撑比率区间
            Map<String, Object> topBucket = bucketList.get(0);
            String bucketRange = (String) topBucket.get("_id");  // 如 "0.6-0.7"
            
            // 解析区间下限
            String[] parts = bucketRange.split("-");
            double bucketLower = Double.parseDouble(parts[0]);
            
            // 建议值 = 区间下限 - 0.1（略低于常见值，但不低于0.5）
            double suggestedMinSupportRatio = Math.max(0.5, bucketLower - 0.1);
            
            log.info("Depth analysis completed: symbol={}, days={}, topBucket={}, totalCount={}, suggested={}", 
                symbol, days, bucketRange, topBucket.get("totalCount"), 
                String.format("%.2f", suggestedMinSupportRatio));
            
            return suggestedMinSupportRatio;
            
        } catch (Exception e) {
            log.error("Failed to analyze optimal minSupportRatio: symbol={}", symbol, e);
            return null;
        }
    }
    
    /**
     * 分析交易统计，建议 maxHoldSeconds 调整
     * 
     * 算法：
     * 1. 统计最近N天的平均持仓时间
     * 2. 分析盈利交易 vs 亏损交易的平均持仓时间
     * 3. 建议 maxHoldSeconds = 盈利交易平均持仓时间 * 1.5（留50%缓冲）
     * 
     * @param symbol 交易对
     * @param days 回看天数
     * @return 建议的 maxHoldSeconds，如果数据不足返回 null
     */
    public Integer analyzeOptimalMaxHoldSeconds(String symbol, int days) {
        try {
            LocalDate startDate = LocalDate.now().minusDays(days);
            
            // 查询最近N天的交易统计
            Criteria criteria = Criteria.where("symbol").is(symbol)
                .and("date").gte(startDate);
            
            List<TradeStats> statsList = mongoTemplate.find(
                org.springframework.data.mongodb.core.query.Query.query(criteria), 
                TradeStats.class);
            
            if (statsList.isEmpty()) {
                log.warn("No trade stats found for analysis: symbol={}, days={}", symbol, days);
                return null;
            }
            
            // 计算加权平均持仓时间（按交易次数加权）
            double totalWeightedHoldTime = 0;
            int totalTrades = 0;
            
            for (TradeStats stats : statsList) {
                if (stats.getAvgHoldSeconds() != null && stats.getTotalTrades() != null) {
                    totalWeightedHoldTime += stats.getAvgHoldSeconds() * stats.getTotalTrades();
                    totalTrades += stats.getTotalTrades();
                }
            }
            
            if (totalTrades == 0) {
                log.warn("No valid trade data for analysis: symbol={}", symbol);
                return null;
            }
            
            double avgHoldSeconds = totalWeightedHoldTime / totalTrades;
            
            // 建议值 = 平均持仓时间 * 1.5（留50%缓冲），但不低于600秒，不高于3600秒
            int suggestedMaxHoldSeconds = (int) Math.min(3600, Math.max(600, avgHoldSeconds * 1.5));
            
            log.info("Trade analysis completed: symbol={}, days={}, totalTrades={}, avgHoldTime={}s, suggested={}s", 
                symbol, days, totalTrades, (int) avgHoldSeconds, suggestedMaxHoldSeconds);
            
            return suggestedMaxHoldSeconds;
            
        } catch (Exception e) {
            log.error("Failed to analyze optimal maxHoldSeconds: symbol={}", symbol, e);
            return null;
        }
    }
    
    /**
     * 自动分析并应用配置变更
     * 基于最近N天的统计数据，分析并更新策略配置
     * 
     * @param configId 配置ID
     * @param days 回看天数
     * @return 是否有配置被更新
     */
    public boolean autoAnalyzeAndApply(String configId, int days) {
        try {
            // 获取当前配置
            StrategyConfig config = mongoTemplate.findById(configId, StrategyConfig.class);
            if (config == null) {
                log.warn("Config not found: configId={}", configId);
                return false;
            }
            
            boolean updated = false;
            
            // 1. 分析并更新 minProfitTick
            Double suggestedMinProfitTick = analyzeOptimalMinProfitTick(config.getSymbol(), days);
            if (suggestedMinProfitTick != null && shouldUpdateMinProfitTick(config, suggestedMinProfitTick)) {
                strategyService.updateMaxBuyPrice(
                    configId, 
                    suggestedMinProfitTick, 
                    ConfigChangeLog.ChangeSource.AUTO_SPREAD_STATS,
                    String.format("Auto adjusted based on %d days spread stats analysis", days)
                );
                log.info("✓ minProfitTick updated: configId={}, old={}, new={}", 
                    configId, config.getMinProfitTick(), suggestedMinProfitTick);
                updated = true;
            }
            
            // 2. 分析并更新 minSupportRatio
            Double suggestedMinSupportRatio = analyzeOptimalMinSupportRatio(config.getSymbol(), days);
            if (suggestedMinSupportRatio != null && shouldUpdateMinSupportRatio(config, suggestedMinSupportRatio)) {
                org.springframework.data.mongodb.core.query.Update update = 
                    new org.springframework.data.mongodb.core.query.Update()
                        .set("minSupportRatio", suggestedMinSupportRatio);
                
                strategyService.updateConfig(
                    configId, 
                    update,
                    ConfigChangeLog.ChangeSource.AUTO_DEPTH_STATS,
                    String.format("Auto adjusted based on %d days depth stats analysis", days)
                );
                log.info("✓ minSupportRatio updated: configId={}, old={}, new={}", 
                    configId, config.getMinSupportRatio(), suggestedMinSupportRatio);
                updated = true;
            }
            
            // 3. 分析并更新 maxHoldSeconds
            Integer suggestedMaxHoldSeconds = analyzeOptimalMaxHoldSeconds(config.getSymbol(), days);
            if (suggestedMaxHoldSeconds != null && shouldUpdateMaxHoldSeconds(config, suggestedMaxHoldSeconds)) {
                org.springframework.data.mongodb.core.query.Update update = 
                    new org.springframework.data.mongodb.core.query.Update()
                        .set("maxHoldSeconds", suggestedMaxHoldSeconds);
                
                strategyService.updateConfig(
                    configId, 
                    update,
                    ConfigChangeLog.ChangeSource.AUTO_TRADE_STATS,
                    String.format("Auto adjusted based on %d days trade stats analysis", days)
                );
                log.info("✓ maxHoldSeconds updated: configId={}, old={}, new={}", 
                    configId, config.getMaxHoldSeconds(), suggestedMaxHoldSeconds);
                updated = true;
            }
            
            if (!updated) {
                log.info("No configuration changes needed: configId={}, days={}", configId, days);
            }
            
            return updated;
            
        } catch (Exception e) {
            log.error("Failed to auto analyze and apply config changes: configId={}", configId, e);
            return false;
        }
    }
    
    /**
     * 判断是否应该更新 minProfitTick
     * 只有当建议值与当前值相差超过10%时才更新
     */
    private boolean shouldUpdateMinProfitTick(StrategyConfig config, double suggested) {
        double current = config.getMinProfitTick();
        double diff = Math.abs(suggested - current) / current;
        return diff > 0.1;  // 差异超过10%
    }
    
    /**
     * 判断是否应该更新 minSupportRatio
     * 只有当建议值与当前值相差超过0.1时才更新
     */
    private boolean shouldUpdateMinSupportRatio(StrategyConfig config, double suggested) {
        double current = config.getMinSupportRatio();
        return Math.abs(suggested - current) > 0.1;  // 差异超过0.1
    }
    
    /**
     * 判断是否应该更新 maxHoldSeconds
     * 只有当建议值与当前值相差超过20%时才更新
     */
    private boolean shouldUpdateMaxHoldSeconds(StrategyConfig config, int suggested) {
        int current = config.getMaxHoldSeconds();
        double diff = Math.abs(suggested - current) / (double) current;
        return diff > 0.2;  // 差异超过20%
    }
}

