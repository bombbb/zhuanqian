package com.zq.config;

import com.zq.api.BinanceWebSocketClient;
import com.zq.api.MarketDataHandler;
import com.zq.strategy.StrategyConfig;
import com.zq.strategy.StrategyService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;

/**
 * Binance WebSocket配置类
 * 根据策略配置动态创建WebSocket连接
 * 注意：测试网和正式网的行情都使用正式网的WebSocket
 */
@Configuration
@Profile("!test")
@Slf4j
public class BinanceWebSocketConfig {
    
    @Autowired
    private StrategyService strategyService;
    
    @Autowired
    private MarketDataHandler marketDataHandler;
    
    private BinanceWebSocketClient webSocketClient;
    
    /**
     * 创建MarketDataHandler Bean
     * 注意：MarketDataHandler现在是@Component，Spring会自动创建，这里不再需要@Bean
     */
    
    /**
     * 启动WebSocket连接
     */
    @PostConstruct
    public void startWebSocket() {
        log.info("BinanceWebSocketConfig initialized");
        
        // 延迟启动WebSocket，确保所有Bean都已初始化
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 等待2秒，确保应用完全启动
                
                // 检查配置是否启用
                StrategyConfig config = strategyService.getStrategyConfig();
                if (config != null && config.isEnabled()) {
                    log.info("Starting WebSocket connection for enabled strategy: {}", config.getId());
                    createWebSocketConnection(strategyService, marketDataHandler);
                } else {
                    log.warn("No enabled strategy config found, WebSocket not started");
                }
            } catch (Exception e) {
                log.error("Failed to auto-start WebSocket connection", e);
            }
        }, "WebSocket-Starter").start();
    }
    
    /**
     * 创建并启动WebSocket连接
     * 
     * @param strategyService 策略服务
     * @param marketDataHandler 市场数据处理器
     */
    public void createWebSocketConnection(StrategyService strategyService, MarketDataHandler marketDataHandler) {
        try {
            StrategyConfig config = strategyService.getStrategyConfig();
            
            // 获取WebSocket URL，如果配置中没有则使用默认的正式网地址
            String wsBaseUrl = config.getMarketDataWsUrl();
            if (wsBaseUrl == null || wsBaseUrl.isEmpty()) {
                wsBaseUrl = "wss://stream.binance.com:9443";
                log.info("Using default WebSocket URL: {}", wsBaseUrl);
            }
            
            // 注意：无论是测试网还是正式网模式，行情都使用正式网的WebSocket
            log.info("Mode: {}, Market data WebSocket URL: {} (always using production market data)", 
                config.getMode(), wsBaseUrl);
            
            // 构建WebSocket URI
            String symbol = config.getSymbol().toLowerCase();
            String streamUrl = wsBaseUrl + "/ws/" + symbol + "@ticker";
            URI serverUri = new URI(streamUrl);
            
            log.info("Connecting to Binance WebSocket: {}", streamUrl);
            
            // 注册交易对到MarketDataHandler
            marketDataHandler.addDecisionEngine(config.getSymbol());
            
            // 创建WebSocket客户端
            webSocketClient = new BinanceWebSocketClient(serverUri, symbol + "@ticker", marketDataHandler);
            
            // 建立连接
            webSocketClient.connect();
            log.info("WebSocket connection initiated for symbol: {}", config.getSymbol());
            
        } catch (Exception e) {
            log.error("Failed to create WebSocket connection", e);
            throw new RuntimeException("Failed to create WebSocket connection", e);
        }
    }
    
    /**
     * 关闭WebSocket连接
     */
    @PreDestroy
    public void stopWebSocket() {
        if (webSocketClient != null) {
            try {
                log.info("Stopping WebSocket connection...");
                webSocketClient.stopReconnect();
                webSocketClient.close();
                log.info("WebSocket connection stopped");
            } catch (Exception e) {
                log.error("Error stopping WebSocket connection", e);
            }
        }
    }
    
    /**
     * 获取WebSocket客户端
     */
    public BinanceWebSocketClient getWebSocketClient() {
        return webSocketClient;
    }
}
