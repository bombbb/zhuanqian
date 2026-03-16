package com.zq.strategy;


import com.zq.config.CacheService;
import com.zq.config.ConfigChangeLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.zq.config.Constant.SYMBOL_USDC;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Service
@Slf4j
public class StrategyService {
    @Resource
    private  CacheService cacheService;
    @Resource
    private  MongoTemplate mongoTemplate;

    public StrategyConfig getStrategyConfig() {
        return getStrategyConfig(SYMBOL_USDC);
    }
    
    public StrategyConfig getStrategyConfig(String symbol) {
        // 从缓存获取策略配置
        StrategyConfig strategyConfig = cacheService.getFromCache(symbol,StrategyConfig.class);
        // 使用 Optional 来判断缓存是否为 null，如果为 null 则查询数据库
        return Optional.ofNullable(strategyConfig)
                .orElseGet(() -> {
                    // 如果缓存中没有，查询数据库
                    // 优先查询启用的配置（enabled=true）
                    StrategyConfig strategyConfigFromDb = mongoTemplate.findOne(
                            query(where("symbol").is(symbol).and("enabled").is(true)),
                            StrategyConfig.class
                    );
                    if (strategyConfigFromDb != null) {
                        cacheService.putInCache(symbol, strategyConfigFromDb);
                        log.info("reload from DB = StrategyConfig(id={}, symbol={}, mode={}, enabled={}, " +
                                "referencePrice={}, maxBuyPrice={}, maxBuyAmountUsdt={}, minProfitTick={}, maxHoldSeconds={}, " +
                                "maxTotalInvestUsdt={}, minSupportRatio={}, supportRangeNear={}, " +
                                "supportRangeMid={}, supportRangeFar={}, priceLogIntervalSeconds={})",
                                strategyConfigFromDb.getId(),
                                strategyConfigFromDb.getSymbol(),
                                strategyConfigFromDb.getMode(),
                                strategyConfigFromDb.isEnabled(),
                                String.format("%.6f", strategyConfigFromDb.getReferencePrice()),
                                String.format("%.6f", strategyConfigFromDb.getMaxBuyPrice()),
                                String.format("%.2f", strategyConfigFromDb.getMaxBuyAmountUsdt()),
                                String.format("%.6f", strategyConfigFromDb.getMinProfitTick()),
                                strategyConfigFromDb.getMaxHoldSeconds(),
                                String.format("%.2f", strategyConfigFromDb.getMaxTotalInvestUsdt()),
                                String.format("%.2f", strategyConfigFromDb.getMinSupportRatio()),
                                String.format("%.6f", strategyConfigFromDb.getSupportRangeNear()),
                                String.format("%.6f", strategyConfigFromDb.getSupportRangeMid()),
                                String.format("%.6f", strategyConfigFromDb.getSupportRangeFar()),
                                strategyConfigFromDb.getPriceLogIntervalSeconds()
                        );
                    } else {
                        log.warn("No enabled strategy config found in DB for symbol: {}", symbol);
                    }
                    return strategyConfigFromDb;
                });
    }
    
    /**
     * 更新最高买入价（maxBuyPrice）
     * 同时清空缓存，确保下次获取到最新值
     * 
     * @param configId 配置ID，如 "USDCUSDT_TESTNET"
     * @param newMaxBuyPrice 新的最高买入价
     */
    public void updateMaxBuyPrice(String configId, double newMaxBuyPrice) {
        updateMaxBuyPrice(configId, newMaxBuyPrice, ConfigChangeLog.ChangeSource.MANUAL, "Manual update");
    }
    
    /**
     * 更新最高买入价（maxBuyPrice），带变更原因记录
     * 
     * @param configId 配置ID
     * @param newMaxBuyPrice 新的最高买入价
     * @param source 变更来源
     * @param reason 变更原因
     */
    public void updateMaxBuyPrice(String configId, double newMaxBuyPrice, 
                                   ConfigChangeLog.ChangeSource source, String reason) {
        try {
            // 0. 获取变更前的配置
            StrategyConfig oldConfig = mongoTemplate.findById(configId, StrategyConfig.class);
            if (oldConfig == null) {
                log.warn("Config not found: configId={}", configId);
                return;
            }
            
            // 1. 更新数据库
            Update update = new Update().set("maxBuyPrice", newMaxBuyPrice);
            mongoTemplate.updateFirst(
                query(where("_id").is(configId)), 
                update, 
                StrategyConfig.class
            );
            
            // 2. 记录配置变更日志
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("maxBuyPrice", oldConfig.getMaxBuyPrice());
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("maxBuyPrice", newMaxBuyPrice);
            logConfigChange(configId, oldConfig.getSymbol(), oldConfig.getMode().toString(), 
                            source, reason, oldValues, newValues);
            
            // 3. 清空缓存
            cacheService.evict(oldConfig.getSymbol());
            log.info("✓ MaxBuyPrice updated and cache cleared: configId={}, oldValue={}, newValue={}, source={}, reason={}", 
                configId, String.format("%.6f", oldConfig.getMaxBuyPrice()), 
                String.format("%.6f", newMaxBuyPrice), source, reason);
        } catch (Exception e) {
            log.error("Failed to update maxBuyPrice: configId={}, newPrice={}", 
                configId, newMaxBuyPrice, e);
        }
    }
    
    /**
     * 批量更新配置参数
     * 同时清空缓存
     * 
     * @param configId 配置ID
     * @param update 更新的字段映射
     */
    public void updateConfig(String configId, Update update) {
        updateConfig(configId, update, ConfigChangeLog.ChangeSource.MANUAL, "Manual update");
    }
    
    /**
     * 批量更新配置参数，带变更原因记录
     * 
     * @param configId 配置ID
     * @param update 更新的字段映射
     * @param source 变更来源
     * @param reason 变更原因
     */
    public void updateConfig(String configId, Update update, 
                            ConfigChangeLog.ChangeSource source, String reason) {
        try {
            // 0. 获取变更前的配置（这里简化处理，只记录configId）
            StrategyConfig oldConfig = mongoTemplate.findById(configId, StrategyConfig.class);
            if (oldConfig == null) {
                log.warn("Config not found: configId={}", configId);
                return;
            }
            
            // 1. 更新数据库
            mongoTemplate.updateFirst(
                query(where("_id").is(configId)), 
                update, 
                StrategyConfig.class
            );
            
            // 2. 记录配置变更日志（简化：记录整个update对象）
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("configState", "before update");
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("updateFields", update.toString());
            logConfigChange(configId, oldConfig.getSymbol(), oldConfig.getMode().toString(),
                           source, reason, oldValues, newValues);
            
            // 3. 清空缓存
            cacheService.evict(oldConfig.getSymbol());
            log.info("✓ Config updated and cache cleared: configId={}, source={}, reason={}", 
                     configId, source, reason);
        } catch (Exception e) {
            log.error("Failed to update config: configId={}", configId, e);
        }
    }
    
    /**
     * 清空指定symbol的缓存
     * 用于手动刷新配置
     */
    public void clearCache(String symbol) {
        cacheService.evict(symbol);
        log.info("✓ Cache cleared for symbol: {}", symbol);
    }
    
    /**
     * 记录配置变更日志（异步）
     */
    private void logConfigChange(String configId, String symbol, String mode,
                                  ConfigChangeLog.ChangeSource source, String reason,
                                  Map<String, Object> oldValues, Map<String, Object> newValues) {
        Thread.startVirtualThread(() -> {
            try {
                ConfigChangeLog changeLog = new ConfigChangeLog();
                changeLog.setId(UUID.randomUUID().toString());
                changeLog.setConfigId(configId);
                changeLog.setSymbol(symbol);
                changeLog.setMode(mode);
                changeLog.setSource(source);
                changeLog.setReason(reason);
                changeLog.setOldValues(oldValues);
                changeLog.setNewValues(newValues);
                changeLog.setChangeTime(LocalDateTime.now());
                changeLog.setOperator(source == ConfigChangeLog.ChangeSource.MANUAL ? "admin" : "system");
                
                mongoTemplate.insert(changeLog);
                log.info("✓ Config change logged: configId={}, source={}, reason={}", 
                         configId, source, reason);
            } catch (Exception e) {
                log.error("Failed to log config change: configId={}", configId, e);
            }
        });
    }
}
