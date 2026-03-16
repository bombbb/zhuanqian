package com.zq.position;

import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * 持仓管理服务
 * 管理持仓数量、平均买入价格、未实现盈亏等
 * 使用Java 21虚拟线程异步处理MongoDB写操作
 */
@Service
@Slf4j
public class PositionService {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    @Resource
    private StrategyService strategyService;
    
    /**
     * 买入更新持仓
     * 如果持仓不存在，创建新持仓；如果已存在，累加数量并重新计算平均买入价格
     */
    public void updatePositionOnBuy(String symbol, StrategyConfig.Mode mode, double buyQty, double buyPrice) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("symbol").is(symbol).and("mode").is(mode));
                Position position = mongoTemplate.findOne(query, Position.class);
                
                if (position == null) {
                    // 首次买入，创建新持仓
                    position = new Position();
                    position.setSymbol(symbol);
                    position.setMode(mode);
                    position.setQuantity(buyQty);
                    position.setAvgBuyPrice(buyPrice);
                    position.setTotalInvestedUsdt(buyQty * buyPrice);
                    position.setCurrentPrice(buyPrice);
                    position.setUnrealizedPnl(0.0);
                    position.setCreateTime(LocalDateTime.now());
                    position.setUpdateTime(LocalDateTime.now());
                    
                    mongoTemplate.insert(position);
                    log.info("Position created: symbol={}, mode={}, qty={}, price={}", 
                        symbol, mode, buyQty, buyPrice);
                } else {
                    // 追加买入，更新持仓
                    double newTotalQty = position.getQuantity() + buyQty;
                    double newTotalInvested = position.getTotalInvestedUsdt() + (buyQty * buyPrice);
                    double newAvgPrice = newTotalInvested / newTotalQty;
                    
                    Update update = new Update()
                        .set("quantity", newTotalQty)
                        .set("avgBuyPrice", newAvgPrice)
                        .set("totalInvestedUsdt", newTotalInvested)
                        .set("updateTime", LocalDateTime.now());
                    
                    mongoTemplate.updateFirst(query, update, Position.class);
                    log.info("Position updated (buy): symbol={}, mode={}, newQty={}, newAvgPrice={}", 
                        symbol, mode, newTotalQty, newAvgPrice);
                }
            } catch (Exception e) {
                log.error("Failed to update position on buy: symbol={}, mode={}", symbol, mode, e);
            }
        });
    }
    
    /**
     * 卖出更新持仓
     * 按比例减少持仓数量和投入金额
     */
    public void updatePositionOnSell(String symbol, StrategyConfig.Mode mode, double sellQty, double sellPrice) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("symbol").is(symbol).and("mode").is(mode));
                Position position = mongoTemplate.findOne(query, Position.class);
                
                if (position == null) {
                    log.warn("Position not found for sell: symbol={}, mode={}", symbol, mode);
                    return;
                }
                
                // 计算卖出比例
                double sellRatio = sellQty / position.getQuantity();
                
                // 新持仓数量
                double newQty = Math.max(0, position.getQuantity() - sellQty);
                
                // 按比例减少投入金额
                double soldInvested = position.getTotalInvestedUsdt() * sellRatio;
                double newInvested = Math.max(0, position.getTotalInvestedUsdt() - soldInvested);
                
                Update update = new Update()
                    .set("quantity", newQty)
                    .set("totalInvestedUsdt", newInvested)
                    .set("updateTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Position.class);
                log.info("Position updated (sell): symbol={}, mode={}, newQty={}, newInvested={}", 
                    symbol, mode, newQty, newInvested);
            } catch (Exception e) {
                log.error("Failed to update position on sell: symbol={}, mode={}", symbol, mode, e);
            }
        });
    }
    
    /**
     * 计算未实现盈亏
     * 未实现盈亏 = 持仓数量 * (当前价格 - 平均买入价格)
     */
    public double calculateUnrealizedPnl(String symbol, StrategyConfig.Mode mode, double currentPrice) {
        Query query = query(where("symbol").is(symbol).and("mode").is(mode));
        Position position = mongoTemplate.findOne(query, Position.class);
        
        if (position == null || position.getQuantity() == 0) {
            return 0.0;
        }
        
        double pnl = position.getQuantity() * (currentPrice - position.getAvgBuyPrice());
        
        // 异步更新持仓的当前价格和未实现盈亏
        Thread.startVirtualThread(() -> {
            try {
                Update update = new Update()
                    .set("currentPrice", currentPrice)
                    .set("unrealizedPnl", pnl)
                    .set("updateTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Position.class);
            } catch (Exception e) {
                log.error("Failed to update unrealized PnL: symbol={}, mode={}", symbol, mode, e);
            }
        });
        
        return pnl;
    }
    
    /**
     * 获取可用资金
     * 可用资金 = 最大总投入金额 - 已投入金额
     */
    public double getAvailableFunds(String symbol, StrategyConfig.Mode mode) {
        Query query = query(where("symbol").is(symbol).and("mode").is(mode));
        Position position = mongoTemplate.findOne(query, Position.class);
        
        // 从配置读取最大总投入金额
        StrategyConfig config = strategyService.getStrategyConfig();
        double maxTotalInvest = config.getMaxTotalInvestUsdt();
        
        if (position == null) {
            return maxTotalInvest;
        }
        
        double invested = position.getTotalInvestedUsdt();
        return maxTotalInvest - invested;
    }
    
    /**
     * 获取持仓
     */
    public Position getPosition(String symbol, StrategyConfig.Mode mode) {
        Query query = query(where("symbol").is(symbol).and("mode").is(mode));
        return mongoTemplate.findOne(query, Position.class);
    }
    
    /**
     * 清空持仓（用于测试）
     */
    public void clearPosition(String symbol, StrategyConfig.Mode mode) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("symbol").is(symbol).and("mode").is(mode));
                mongoTemplate.remove(query, Position.class);
                log.info("Position cleared: symbol={}, mode={}", symbol, mode);
            } catch (Exception e) {
                log.error("Failed to clear position: symbol={}, mode={}", symbol, mode, e);
            }
        });
    }
}

