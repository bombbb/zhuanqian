package com.zq.strategy;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 策略配置Repository
 */
@Repository
public interface StrategyConfigRepository extends MongoRepository<StrategyConfig, String> {
    
    /**
     * 查找启用的策略配置
     */
    StrategyConfig findFirstBySymbolAndEnabledTrue(String symbol);
}

