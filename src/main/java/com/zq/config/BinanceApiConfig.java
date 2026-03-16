package com.zq.config;

import com.zq.api.BinanceApiService;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Binance API配置类
 * 根据策略配置动态创建BinanceApiService Bean
 */
@Configuration
@Profile("!test")
public class BinanceApiConfig {
    
    /**
     * 创建BinanceApiService Bean
     * 从策略配置中读取API URL和密钥
     */
    @Bean
    public BinanceApiService binanceApiService(StrategyService strategyService) {
        StrategyConfig config = strategyService.getStrategyConfig();
        
        String baseUrl;
        String apiKey;
        String secretKey;
        
        // 根据模式选择对应的API配置
        switch (config.getMode()) {
            case TESTNET:
                baseUrl = config.getTestnetApiUrl();
                apiKey = config.getTestnetApiKey();
                secretKey = config.getTestnetSecretKey();
                break;
            case PRODUCTION:
                baseUrl = config.getProductionApiUrl();
                apiKey = config.getProductionApiKey();
                secretKey = config.getProductionSecretKey();
                break;
            case SIMULATION:
            default:
                // 模拟模式使用测试网配置，但不实际发送请求
                baseUrl = config.getTestnetApiUrl() != null ? config.getTestnetApiUrl() : "https://testnet.binance.vision";
                apiKey = config.getTestnetApiKey() != null ? config.getTestnetApiKey() : "test";
                secretKey = config.getTestnetSecretKey() != null ? config.getTestnetSecretKey() : "test";
                break;
        }
        
        return new BinanceApiService(baseUrl, apiKey, secretKey);
    }
}
