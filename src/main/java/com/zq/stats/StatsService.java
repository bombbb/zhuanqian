package com.zq.stats;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * 统计服务
 * 所有统计操作使用Java 21虚拟线程异步执行
 * 实现聚合统计，避免重复明细
 */
@Service
@Slf4j
public class StatsService {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    @Value("${stats.async:true}")
    private boolean asyncEnabled;
    
    private void runTask(Runnable task) {
        if (asyncEnabled) {
            Thread.startVirtualThread(task);
        } else {
            task.run();
        }
    }
    
    /**
     * 记录价差统计
     * 如果相同日期+价格组合已存在，count+1；否则新建记录
     */
    public void recordSpread(String symbol, double bid, double ask) {
        runTask(() -> {
            try {
                LocalDate today = LocalDate.now();
                String id = SpreadStats.generateId(symbol, today, bid, ask);
                
                Query query = query(where("_id").is(id));
                SpreadStats stats = mongoTemplate.findOne(query, SpreadStats.class);
                
                if (stats == null) {
                    // 新建记录
                    stats = new SpreadStats();
                    stats.setId(id);
                    stats.setSymbol(symbol);
                    stats.setDate(today);
                    stats.setBidPrice(bid);
                    stats.setAskPrice(ask);
                    stats.setCount(1);
                    stats.setCreateTime(LocalDateTime.now());
                    stats.setUpdateTime(LocalDateTime.now());
                    
                    mongoTemplate.insert(stats);
                    log.debug("SpreadStats created: {}, bid={}, ask={}", symbol, bid, ask);
                } else {
                    // 递增count
                    Update update = new Update()
                        .inc("count", 1)
                        .set("updateTime", LocalDateTime.now());
                    
                    mongoTemplate.updateFirst(query, update, SpreadStats.class);
                    log.debug("SpreadStats updated: {}, count={}", id, stats.getCount() + 1);
                }
            } catch (Exception e) {
                log.error("Failed to record spread stats: symbol={}, bid={}, ask={}", 
                    symbol, bid, ask, e);
            }
        });
    }
    
    /**
     * 记录深度统计
     * 按价格区间和支撑比率区间分桶聚合
     */
    public void recordDepth(String symbol, double price, double supportRatio, double volume) {
        runTask(() -> {
            try {
                LocalDate today = LocalDate.now();
                String priceRangeBucket = DepthStats.generatePriceRangeBucket(price);
                String supportRatioBucket = DepthStats.generateSupportRatioBucket(supportRatio);
                String id = DepthStats.generateId(symbol, today, priceRangeBucket, supportRatioBucket);
                
                Query query = query(where("_id").is(id));
                DepthStats stats = mongoTemplate.findOne(query, DepthStats.class);
                
                if (stats == null) {
                    // 新建记录
                    stats = new DepthStats();
                    stats.setId(id);
                    stats.setSymbol(symbol);
                    stats.setDate(today);
                    stats.setPriceRangeBucket(priceRangeBucket);
                    stats.setSupportRatioBucket(supportRatioBucket);
                    stats.setCount(1);
                    stats.setAvgVolume(volume);
                    stats.setUpdateTime(LocalDateTime.now());
                    
                    mongoTemplate.insert(stats);
                    log.debug("DepthStats created: {}, priceRange={}, supportRatio={}", 
                        symbol, priceRangeBucket, supportRatioBucket);
                } else {
                    // 递增count并更新平均volume
                    int newCount = stats.getCount() + 1;
                    double newAvgVolume = (stats.getAvgVolume() * stats.getCount() + volume) / newCount;
                    
                    Update update = new Update()
                        .inc("count", 1)
                        .set("avgVolume", newAvgVolume)
                        .set("updateTime", LocalDateTime.now());
                    
                    mongoTemplate.updateFirst(query, update, DepthStats.class);
                    log.debug("DepthStats updated: {}, count={}", id, newCount);
                }
            } catch (Exception e) {
                log.error("Failed to record depth stats: symbol={}, price={}, supportRatio={}", 
                    symbol, price, supportRatio, e);
            }
        });
    }
    
    /**
     * 记录交易统计
     * 按日期聚合，统计盈亏、胜率、平均持仓时间等
     */
    public void recordTrade(String symbol, double pnl, int holdSeconds, double slippage) {
        runTask(() -> {
            try {
                LocalDate today = LocalDate.now();
                String id = TradeStats.generateId(symbol, today);
                
                Query query = query(where("_id").is(id));
                TradeStats stats = mongoTemplate.findOne(query, TradeStats.class);
                
                boolean isProfit = pnl > 0;
                
                if (stats == null) {
                    // 新建记录
                    stats = new TradeStats();
                    stats.setId(id);
                    stats.setSymbol(symbol);
                    stats.setDate(today);
                    stats.setTotalTrades(1);
                    stats.setProfitTrades(isProfit ? 1 : 0);
                    stats.setLossTrades(isProfit ? 0 : 1);
                    stats.setTotalPnl(pnl);
                    stats.setAvgHoldSeconds((double) holdSeconds);
                    stats.setAvgSlippage(slippage);
                    stats.setMaxProfit(isProfit ? pnl : 0.0);
                    stats.setMaxLoss(isProfit ? 0.0 : pnl);
                    stats.setUpdateTime(LocalDateTime.now());
                    
                    mongoTemplate.insert(stats);
                    log.info("TradeStats created: {}, pnl={}, holdSeconds={}", symbol, pnl, holdSeconds);
                } else {
                    // 更新统计
                    int newTotalTrades = stats.getTotalTrades() + 1;
                    double newAvgHoldSeconds = (stats.getAvgHoldSeconds() * stats.getTotalTrades() + holdSeconds) / newTotalTrades;
                    double newAvgSlippage = (stats.getAvgSlippage() * stats.getTotalTrades() + slippage) / newTotalTrades;
                    
                    Update update = new Update()
                        .inc("totalTrades", 1)
                        .inc(isProfit ? "profitTrades" : "lossTrades", 1)
                        .inc("totalPnl", pnl)
                        .set("avgHoldSeconds", newAvgHoldSeconds)
                        .set("avgSlippage", newAvgSlippage)
                        .set("updateTime", LocalDateTime.now());
                    
                    // 更新最大盈利/亏损
                    if (isProfit && (stats.getMaxProfit() == null || pnl > stats.getMaxProfit())) {
                        update.set("maxProfit", pnl);
                    }
                    if (!isProfit && (stats.getMaxLoss() == null || pnl < stats.getMaxLoss())) {
                        update.set("maxLoss", pnl);
                    }
                    
                    mongoTemplate.updateFirst(query, update, TradeStats.class);
                    log.info("TradeStats updated: {}, totalTrades={}, totalPnl={}", 
                        id, newTotalTrades, stats.getTotalPnl() + pnl);
                }
            } catch (Exception e) {
                log.error("Failed to record trade stats: symbol={}, pnl={}", symbol, pnl, e);
            }
        });
    }
    
    /**
     * 获取指定日期的交易统计
     */
    public TradeStats getTradeStats(String symbol, LocalDate date) {
        String id = TradeStats.generateId(symbol, date);
        return mongoTemplate.findById(id, TradeStats.class);
    }
}
