package com.zq.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zq.strategy.StrategyEngine;
import com.zq.strategy.StrategyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 市场数据处理器
 * 
 * 负责处理从Binance WebSocket接收到的市场数据
 * 包括Ticker（价格快照）数据，实时打印最佳买价和最佳卖价
 * 支持多个交易对，每个交易对对应一个决策引擎
 * 
 * 功能：
 * - 解析WebSocket消息（支持组合流格式）
 * - 存储最新的市场数据
 * - 控制日志打印频率（从配置读取，默认5秒）
 * - 根据交易对路由到对应的决策引擎评估交易信号
 */
@Component
public class MarketDataHandler {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataHandler.class);
    private static final Logger priceLogger = LoggerFactory.getLogger("PRICE");
    
    /** 默认日志打印间隔（秒） */
    private static final long DEFAULT_LOG_INTERVAL_SECONDS = 5;
    
    @Autowired(required = false)
    private StrategyService strategyService;
    
    @Autowired(required = false)
    private StrategyEngine strategyEngine;
    
    /** JSON解析器 */
    private final Gson gson = new Gson();
    
    /** 存储最新的Ticker数据（key为symbol，如"USDCUSDT"） */
    private final Map<String, AtomicReference<TickerData>> latestTickers = new ConcurrentHashMap<>();
    
    /** 每个交易对的上次打印日志时间戳 */
    private final Map<String, AtomicLong> lastLogTimes = new ConcurrentHashMap<>();
//
//    /** 决策引擎映射（key为symbol，如"USDCUSDT"） */
//    private final Map<String, DecisionEngine> decisionEngines = new ConcurrentHashMap<>();
//
    /** 消息计数器 */
    private final AtomicLong messageCount = new AtomicLong(0);

    /**
     * 构造函数
     */
    public MarketDataHandler() {
        logger.info("MarketDataHandler initialized, ready to process market data for multiple symbols");
    }
    
    /**
     * 获取价格日志打印间隔（秒）
     * 从配置中读取，如果配置不存在则使用默认值5秒
     */
    private long getPriceLogIntervalSeconds(String symbol) {
        if (strategyService != null) {
            try {
                var config = strategyService.getStrategyConfig(symbol);
                if (config != null && config.getPriceLogIntervalSeconds() > 0) {
                    return config.getPriceLogIntervalSeconds();
                }
            } catch (Exception e) {
                logger.debug("Failed to get price log interval from config, using default", e);
            }
        }
        return DEFAULT_LOG_INTERVAL_SECONDS;
    }

    /**
     * 添加决策引擎
     * 
     * @param symbol 交易对标识，如 "USDCUSDT", "TUSDUSDT"
     */
    public void addDecisionEngine(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        latestTickers.put(symbol.toUpperCase(), new AtomicReference<>());
        lastLogTimes.put(symbol.toUpperCase(), new AtomicLong(0));
        logger.info("[{}] DecisionEngine attached to MarketDataHandler", symbol.toUpperCase());
    }
    


    /**
     * 处理WebSocket消息
     * 
     * 支持两种消息格式：
     * 1. 单流格式：直接是ticker数据
     * 2. 组合流格式：{"stream":"usdcusdt@ticker","data":{...}}
     * 
     * @param streamName Stream name, e.g. "fdusdusdt@ticker" 或组合流标识
     * @param message JSON格式的WebSocket消息
     */
    public void handleMessage(String streamName, String message) {
        try {
            messageCount.incrementAndGet();
            JsonObject json = gson.fromJson(message, JsonObject.class);
            
            // 检查是否是组合流格式（包含stream和data字段）
            if (json.has("stream") && json.has("data")) {
                String actualStream = json.get("stream").getAsString();
                JsonObject data = json.getAsJsonObject("data");
                handleTickerMessage(actualStream, data);
            } else if (streamName.contains("@ticker") || json.has("c")) {
                // 单流格式或直接是ticker数据
                handleTickerMessage(streamName, json);
            } else {
                logger.debug("Unknown stream type: {}", streamName);
            }
        } catch (Exception e) {
            logger.error("Failed to parse message from stream: {}", streamName, e);
        }
    }

    /**
     * 处理Ticker消息
     * 
     * 解析Ticker数据（价格快照），包括：
     * - 最佳买价/买量（best bid）
     * - 最佳卖价/卖量（best ask）
     * - 最新成交价
     * - 24小时成交量
     * 
     * 解析后根据频率限制打印日志，并调用对应的决策引擎
     * 
     * @param streamName 流名称，如 "usdcusdt@ticker"
     * @param json JSON数据对象
     */
    private void handleTickerMessage(String streamName, JsonObject json) {
        try {
            TickerData ticker = new TickerData();
            
            // 从stream名称提取symbol
            String symbol = extractSymbolFromStream(streamName);
            ticker.setSymbol(symbol);
            
            // 解析ticker数据（Binance API字段说明）
            // b: 最佳买价, B: 最佳买量
            // a: 最佳卖价, A: 最佳卖量
            // c: 最新成交价
            // v: 24小时成交量
            ticker.setBestBidPrice(new BigDecimal(json.get("b").getAsString()));
            ticker.setBestBidQty(new BigDecimal(json.get("B").getAsString()));
            ticker.setBestAskPrice(new BigDecimal(json.get("a").getAsString()));
            ticker.setBestAskQty(new BigDecimal(json.get("A").getAsString()));
            ticker.setLastPrice(new BigDecimal(json.get("c").getAsString()));
            ticker.setVolume(new BigDecimal(json.get("v").getAsString()));
            ticker.setReceiveTime(LocalDateTime.now());
            
            // 更新最新数据
            AtomicReference<TickerData> tickerRef = latestTickers.get(symbol);
            if (tickerRef != null) {
                tickerRef.set(ticker);
            } else {
                // 如果还没有注册该交易对，自动创建
                latestTickers.put(symbol, new AtomicReference<>(ticker));
                lastLogTimes.put(symbol, new AtomicLong(0));
            }
            
            // 根据配置的间隔打印价格日志
            long intervalSeconds = getPriceLogIntervalSeconds(symbol);
            long intervalMs = intervalSeconds * 1000;
            AtomicLong lastLogTime = lastLogTimes.get(symbol);
            long currentTime = System.currentTimeMillis();
            
            if (lastLogTime == null) {
                lastLogTime = new AtomicLong(0);
                lastLogTimes.put(symbol, lastLogTime);
            }
            
            // 检查是否到了打印时间
            if (currentTime - lastLogTime.get() >= intervalMs) {
                printPriceLog(ticker);
                lastLogTime.set(currentTime);
            }
            
            // 调用策略引擎处理行情数据（执行交易决策）
            if (strategyEngine != null) {
                try {
                    strategyEngine.onMarketData(ticker);
                } catch (Exception e) {
                    logger.error("Failed to process market data in StrategyEngine", e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to process ticker message from stream: {}", streamName, e);
        }
    }

    /**
     * 打印价格日志（增强版）
     * 显示：
     * 1. 当前行情（bid/ask/last/spread）
     * 2. 是否满足下单条件
     * 3. 不满足的具体原因
     */
    private void printPriceLog(TickerData ticker) {
        String symbol = ticker.getSymbol();
        BigDecimal bestBid = ticker.getBestBidPrice();
        BigDecimal bestAsk = ticker.getBestAskPrice();
        BigDecimal lastPrice = ticker.getLastPrice();
        BigDecimal spread = bestAsk.subtract(bestBid);
        
        // 格式化价格，保留6位小数
        String formattedBid = String.format("%.6f", bestBid.doubleValue());
        String formattedAsk = String.format("%.6f", bestAsk.doubleValue());
        String formattedLast = String.format("%.6f", lastPrice.doubleValue());
        String formattedSpread = String.format("%.6f", spread.doubleValue());
        
        // 分析是否满足下单条件
        String tradeStatus = analyzeTradeConditions(ticker);
        
        // 记录到价格日志
        priceLogger.info("[{}] | Bid: {} | Ask: {} | Last: {} | Spread: {} | {} | Msgs: {}",
                symbol, formattedBid, formattedAsk, formattedLast, formattedSpread, tradeStatus, messageCount.get());
        
        // 同时记录到主日志（INFO级别，让用户能看到系统正在运行）
        logger.info("[{}] 行情更新 - Bid: {}, Ask: {}, Last: {} | {} | 消息数: {}",
                symbol, formattedBid, formattedAsk, formattedLast, tradeStatus, messageCount.get());
    }
    
    /**
     * 分析交易条件
     * 返回一个简洁的状态字符串，说明当前是否可以下单以及原因
     */
    private String analyzeTradeConditions(TickerData ticker) {
        if (strategyService == null) {
            return "状态: 策略服务未就绪";
        }
        
        try {
            var config = strategyService.getStrategyConfig(ticker.getSymbol());
            if (config == null) {
                return "状态: 无配置";
            }
            
            if (!config.isEnabled()) {
                return "状态: 已禁用";
            }
            
            double lastPrice = ticker.getLastPrice().doubleValue();
            
            // 获取最高买入价
            double maxBuyPrice = config.getMaxBuyPrice();
            if (maxBuyPrice <= 0) {
                // 如果未设置，使用 referencePrice
                maxBuyPrice = config.getReferencePrice() > 0 ? 
                    config.getReferencePrice() : 1.0;
            }
            
            if (maxBuyPrice <= 0) {
                return "状态: 最高买入价未设置";
            }
            
            // 检查各个条件
            boolean priceCondition = lastPrice < maxBuyPrice;
            
            // 利润条件：简化为只要设置了最小利润即可
            boolean profitCondition = config.getMinProfitTick() >= 0.0001;
            
            // 暂时使用固定支撑比率（实际应该从深度数据计算）
            double supportRatio = 0.8;
            boolean supportCondition = supportRatio >= config.getMinSupportRatio();
            
            // 计算距离最高买入价还有多少空间
            double priceGap = (maxBuyPrice - lastPrice) / maxBuyPrice * 100;
            
            // 返回状态字符串
            if (priceCondition && profitCondition && supportCondition) {
                return String.format("✓ 可下单 (距上限: %.4f%%)", priceGap);
            } else {
                StringBuilder sb = new StringBuilder("✗ 暂不下单: ");
                if (!priceCondition) {
                    sb.append(String.format("价格过高(%.6f>=%.6f) ", lastPrice, maxBuyPrice));
                }
                if (!profitCondition) {
                    sb.append(String.format("利润设置过低(%.6f<0.0001) ", config.getMinProfitTick()));
                }
                if (!supportCondition) {
                    sb.append(String.format("支撑不足(%.2f<%.2f) ", supportRatio, config.getMinSupportRatio()));
                }
                return sb.toString().trim();
            }
            
        } catch (Exception e) {
            logger.debug("Failed to analyze trade conditions", e);
            return "状态: 分析失败";
        }
    }

    /**
     * 从流名称中提取交易对符号
     * 
     * 流名称格式示例：
     * - "usdcusdt@ticker" -> "USDCUSDT"
     * - "tusdusdt@ticker" -> "TUSDUSDT"
     * 
     * @param streamName 流名称
     * @return 交易对符号（大写）
     */
    private String extractSymbolFromStream(String streamName) {
        // streamName格式: usdcusdt@ticker
        String[] parts = streamName.split("@");
        if (parts.length > 0) {
            return parts[0].toUpperCase();
        }
        return streamName.toUpperCase();
    }

    /**
     * 获取指定交易对的最新Ticker数据
     * 
     * @param symbol 交易对标识，如 "USDCUSDT", "TUSDUSDT"
     * @return 最新的Ticker数据，如果还未接收到数据则返回null
     */
    public TickerData getLatestTicker(String symbol) {
        AtomicReference<TickerData> tickerRef = latestTickers.get(symbol.toUpperCase());
        return tickerRef != null ? tickerRef.get() : null;
    }
    
    /**
     * 获取FDUSDUSDT最新Ticker数据（向后兼容，已废弃）
     * @deprecated 使用 getLatestTicker(String symbol) 代替
     */
    @Deprecated
    public TickerData getLatestTickerFDUSD() {
        return getLatestTicker("FDUSDUSDT");
    }

    /**
     * 获取接收到的消息总数
     */
    public long getMessageCount() {
        return messageCount.get();
    }
}
