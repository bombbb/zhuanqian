package com.zq.strategy;

import com.zq.api.BinanceApiService;
import com.zq.api.BinanceOrder;
import com.zq.api.TickerData;
import com.zq.order.Order;
import com.zq.order.OrderService;
import com.zq.position.PositionService;
import com.zq.stats.StatsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 策略引擎
 * 集成所有模块，实现完整的交易流程：
 * 1. 接收行情数据
 * 2. 计算深度支撑
 * 3. 判断买入信号
 * 4. 执行交易
 * 5. 监控订单
 * 6. 记录统计
 */
@Component
@Slf4j
public class StrategyEngine {

    @Resource
    private StrategyService strategyService;
    
    @Resource
    private BinanceApiService apiService;
    
    @Resource
    private OrderService orderService;
    
    @Resource
    private PositionService positionService;
    
    @Resource
    private StatsService statsService;
    
    @Resource
    private DepthAnalyzer depthAnalyzer;
    
    @Resource
    private DynamicPriceAdjuster priceAdjuster;
    
    @Resource
    private TrendAnalyzer trendAnalyzer;
    
    private final Random random = new Random();
    
    // 上次趋势分析时间
    private volatile LocalDateTime lastTrendAnalysisTime = null;
    
    // 最近的市场价格（从ticker更新）
    private volatile double lastMarketPrice = 1.0;
    
    // 挂单数量管理常量
    private static final int MAX_OPEN_ORDERS_THRESHOLD = 150;  // 触发清理的阈值（币安限制200）
    private static final int TARGET_OPEN_ORDERS = 100;         // 清理后的目标数量
    private static final int CLEANUP_BATCH_SIZE = 50;          // 每次清理的数量

    // 交易所资金检查缓存与冷却（防止频繁请求/反复余额不足）
    private static final long EXCHANGE_FUNDS_CACHE_MS = 5_000;
    private static final long INSUFFICIENT_BALANCE_COOLDOWN_MS = 30_000;

    private volatile long lastExchangeFundsCheckMs = 0L;
    private volatile double lastExchangeFreeUsdt = Double.NaN;
    private volatile double lastOpenBuyNotional = 0.0;
    private volatile long lastInsufficientBalanceMs = 0L;

    /**
     * 启动策略引擎
     */
    @PostConstruct
    public void start() {
        log.info("StrategyEngine initialized");

        // 启动时先撤销所有挂单，避免遗留订单影响当前策略
        Thread.startVirtualThread(this::cancelAllOpenOrdersOnStartup);
        
        // 启动趋势分析定时任务
        startTrendAnalysisTask();
    }

    /**
     * 启动时撤销所有挂单（交易所 + 本地状态）
     */
    private void cancelAllOpenOrdersOnStartup() {
        try {
            StrategyConfig config = strategyService.getStrategyConfig();
            if (config == null) {
                log.warn("Startup cancel skipped: StrategyConfig not found");
                return;
            }

            String symbol = config.getSymbol();
            StrategyConfig.Mode mode = config.getMode();

            log.warn("⚠ Startup canceling all open orders: symbol={}, mode={}", symbol, mode);

            // 1) 交易所侧撤单
            List<BinanceOrder> openOrders = apiService.getOpenOrders(symbol);
            if (openOrders.isEmpty()) {
                log.info("Startup: no open orders on exchange for symbol={}", symbol);
            } else {
                List<Long> orderIdsToCancel = openOrders.stream()
                    .map(BinanceOrder::getOrderId)
                    .filter(id -> id != null && id > 0)
                    .toList();

                int canceledCount = orderIdsToCancel.isEmpty()
                    ? 0
                    : apiService.cancelOrders(symbol, orderIdsToCancel);

                log.info("Startup: canceled {} orders on exchange (total open={})",
                    canceledCount, openOrders.size());
            }

            // 2) 仅同步本地中“在币安仍存在”的订单为已撤销
            if (!openOrders.isEmpty()) {
                List<Long> exchangeOrderIds = openOrders.stream()
                    .map(BinanceOrder::getOrderId)
                    .filter(id -> id != null && id > 0)
                    .toList();

                List<Order> localOpenOrders = orderService.getAllOrders(symbol, mode).stream()
                    .filter(order -> order.getStatus() == Order.OrderStatus.NEW ||
                                   order.getStatus() == Order.OrderStatus.SUBMITTED)
                    .filter(order -> order.getOrderId() != null && exchangeOrderIds.contains(order.getOrderId()))
                    .toList();

                for (Order order : localOpenOrders) {
                    orderService.markOrderCanceled(order.getId());
                }

                log.info("Startup: local orders synced & marked canceled, count={}", localOpenOrders.size());
            } else {
                log.info("Startup: no exchange orders, local status left unchanged");
            }

        } catch (Exception e) {
            log.error("Startup cancel failed", e);
        }
    }
    
    /**
     * 启动趋势分析定时任务
     */
    private void startTrendAnalysisTask() {
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    StrategyConfig config = strategyService.getStrategyConfig();
                    
                    if (config.isEnabled() && config.isEnableDynamicPriceAdjustment()) {
                        // 检查是否需要执行趋势分析
                        LocalDateTime now = LocalDateTime.now();
                        
                        if (lastTrendAnalysisTime == null || 
                            Duration.between(lastTrendAnalysisTime, now).getSeconds() >= config.getTrendAnalysisIntervalSeconds()) {
                            
                            log.info("开始执行趋势分析和动态调价...");
                            
                            // 获取当前市场价格（使用最近的ticker数据）
                            double currentMarketPrice = getCurrentMarketPrice(config.getSymbol());
                            
                            // 执行价格调整
                            DynamicPriceAdjuster.AdjustmentResult result = priceAdjuster.analyzeAndAdjustPrice(
                                config.getId(),
                                config.getTrendAnalysisLookbackDays(),
                                currentMarketPrice
                            );
                            
                            if (result != null && result.getConfidence() >= config.getMinConfidenceForAdjustment()) {
                                // 重新获取完整的趋势分析结果以调整相关参数
                                TrendAnalyzer.TrendAnalysis analysis = trendAnalyzer.analyzeTrend(
                                    config.getSymbol(),
                                    config.getTrendAnalysisLookbackDays()
                                );
                                
                                priceAdjuster.adjustRelatedParameters(config.getId(), analysis);
                                
                                log.info("趋势分析完成: trend={}, confidence={}, priceChange={}%, reason={}", 
                                    result.getTrend(),
                                    String.format("%.2f", result.getConfidence()),
                                    String.format("%.4f", result.getAdjustmentPercent()),
                                    result.getReason());
                            } else if (result != null) {
                                log.info("趋势分析完成但置信度不足，未调整价格: confidence={} < threshold={}", 
                                    String.format("%.2f", result.getConfidence()),
                                    String.format("%.2f", config.getMinConfidenceForAdjustment()));
                            }
                            
                            lastTrendAnalysisTime = now;
                        }
                    }
                    
                    // 每分钟检查一次
                    Thread.sleep(60_000);
                    
                } catch (InterruptedException e) {
                    log.info("Trend analysis task interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Error in trend analysis task", e);
                    try {
                        Thread.sleep(60_000); // 出错后等待1分钟再重试
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        });
        
        log.info("Trend analysis task started");
    }
    
    /**
     * 获取当前市场价格
     * 从最近的ticker数据获取
     */
    private double getCurrentMarketPrice(String symbol) {
        return lastMarketPrice;
    }

    private boolean isInsufficientBalanceCooldownActive() {
        long now = System.currentTimeMillis();
        return now - lastInsufficientBalanceMs < INSUFFICIENT_BALANCE_COOLDOWN_MS;
    }

    private void markInsufficientBalanceCooldown() {
        lastInsufficientBalanceMs = System.currentTimeMillis();
        // 触发下一次强制刷新资金快照
        lastExchangeFundsCheckMs = 0L;
    }

    /**
     * 计算有效可用资金：同时考虑交易所可用余额与配置的最大投入限制
     */
    private EffectiveFunds calculateEffectiveAvailableFunds(StrategyConfig config, String symbol) {
        double configAvailable = positionService.getAvailableFunds(symbol, config.getMode());
        if (configAvailable < 0) {
            configAvailable = 0;
        }

        ExchangeFundsSnapshot snapshot = getExchangeFundsSnapshot(symbol);
        if (!snapshot.isValid) {
            return EffectiveFunds.invalid(configAvailable);
        }

        // 配置侧可用资金需要扣除挂单占用的金额
        double configAvailableAfterOpen = Math.max(0.0, configAvailable - snapshot.openBuyNotional);
        double exchangeAvailable = Math.max(0.0, snapshot.freeUsdt); // free 已排除锁定资金

        double effectiveAvailable = Math.min(configAvailableAfterOpen, exchangeAvailable);
        return new EffectiveFunds(true, effectiveAvailable, configAvailableAfterOpen, exchangeAvailable, snapshot.openBuyNotional);
    }

    /**
     * 获取交易所资金快照（带缓存）
     */
    private ExchangeFundsSnapshot getExchangeFundsSnapshot(String symbol) {
        long now = System.currentTimeMillis();
        if (now - lastExchangeFundsCheckMs < EXCHANGE_FUNDS_CACHE_MS && !Double.isNaN(lastExchangeFreeUsdt)) {
            return new ExchangeFundsSnapshot(true, lastExchangeFreeUsdt, lastOpenBuyNotional);
        }

        try {
            var balance = apiService.getBalance("USDT");
            if (balance == null) {
                throw new IllegalStateException("USDT balance not found");
            }

            double freeUsdt = balance.getFree();
            double openBuyNotional = 0.0;
            List<BinanceOrder> openOrders = apiService.getOpenOrders(symbol);
            for (BinanceOrder order : openOrders) {
                if (!"BUY".equals(order.getSide())) {
                    continue;
                }
                double remainingQty = Math.max(0.0, order.getOrigQty() - order.getExecutedQty());
                openBuyNotional += remainingQty * order.getPrice();
            }

            lastExchangeFreeUsdt = freeUsdt;
            lastOpenBuyNotional = openBuyNotional;
            lastExchangeFundsCheckMs = now;

            return new ExchangeFundsSnapshot(true, freeUsdt, openBuyNotional);
        } catch (Exception e) {
            lastExchangeFundsCheckMs = now;
            log.warn("Failed to fetch exchange funds snapshot: {}", e.getMessage());
            return new ExchangeFundsSnapshot(false, Double.NaN, lastOpenBuyNotional);
        }
    }

    private boolean isInsufficientBalanceError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("insufficient balance") || msg.contains("\"code\":-2010")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 处理行情数据（由MarketDataHandler调用）
     */
    public void onMarketData(TickerData ticker) {
        try {
            // 1. 获取策略配置
            StrategyConfig config = strategyService.getStrategyConfig();
            if (!config.isEnabled()) {
                return;
            }
            
            String symbol = config.getSymbol();
            double lastPrice = ticker.getLastPrice().doubleValue();
            double bid = ticker.getBestBidPrice().doubleValue();
            double ask = ticker.getBestAskPrice().doubleValue();
            
            // 更新最近的市场价格
            lastMarketPrice = lastPrice;
            
            // 2. 异步记录价差统计
            statsService.recordSpread(symbol, bid, ask);
            
            // 3. 计算支撑比率
            // TODO: 深度数据需要从 WebSocket depth stream 获取，ticker 中没有 bids 数据
            // double supportRatio = depthAnalyzer.calcSupportRatio(
            //     ticker.getBids(),
            //     lastPrice,
            //     config.getSupportRangeNear()
            // );
            double supportRatio = 0.8; // 临时使用默认值
            
            // 4. 异步记录深度统计
            double totalVolume = ticker.getVolume().doubleValue();
            statsService.recordDepth(symbol, lastPrice, supportRatio, totalVolume);
            
            // 5. 判断是否满足买入条件
            if (shouldBuy(config, ticker, supportRatio)) {
                if (isInsufficientBalanceCooldownActive()) {
                    log.warn("✗ Skip buy: insufficient balance cooldown active ({}s)", 
                        INSUFFICIENT_BALANCE_COOLDOWN_MS / 1000);
                    return;
                }

                EffectiveFunds funds = calculateEffectiveAvailableFunds(config, symbol);
                if (!funds.isValid) {
                    log.warn("✗ Skip buy: exchange balance unavailable");
                    return;
                }
                if (funds.effectiveAvailable <= 0) {
                    log.warn("✗ Cannot buy: effectiveAvailable<=0, configAvailable={}, exchangeFree={}, openBuyNotional={}",
                        String.format("%.2f", funds.configAvailable),
                        String.format("%.2f", funds.exchangeFreeUsdt),
                        String.format("%.2f", funds.openBuyNotional));
                    return;
                }
                
                // 计算本次订单金额（新逻辑）：
                // 1. 根据价格偏离程度动态计算买入金额
                // 2. 价格偏离越多，买入金额越大（但不超过maxBuyAmountUsdt）
                double orderAmount = calculateBuyAmount(config, ticker, funds.effectiveAvailable);
                
                if (orderAmount > 0 && funds.effectiveAvailable >= orderAmount) {
                    executeBuy(config, ticker, orderAmount);
                } else {
                    log.warn("✗ Cannot buy: effectiveAvailable={}, calculatedAmount={}, configAvailable={}, exchangeFree={}, openBuyNotional={}",
                        String.format("%.2f", funds.effectiveAvailable),
                        String.format("%.2f", orderAmount),
                        String.format("%.2f", funds.configAvailable),
                        String.format("%.2f", funds.exchangeFreeUsdt),
                        String.format("%.2f", funds.openBuyNotional));
                }
            }
            
            // 6. 检查已持仓订单，是否需要撤单平仓
            checkAndCancelExpiredOrders(config);
            
        } catch (Exception e) {
            log.error("Error processing market data", e);
        }
    }
    
    /**
     * 判断是否应该买入
     * 新逻辑：
     * 1. 价格要低于最高买入价（maxBuyPrice）
     * 2. 支撑比率足够（说明有支撑，不会继续大跌）
     * 3. 利润空间足够（买入后能有足够利润，>= minProfitTick）
     * 4. 资金充足（有足够的可用资金）
     */
    private boolean shouldBuy(StrategyConfig config, TickerData ticker, double supportRatio) {
        double lastPrice = ticker.getLastPrice().doubleValue();
        
        // 获取最高买入价
        double maxBuyPrice = config.getMaxBuyPrice();
        if (maxBuyPrice <= 0) {
            // 如果未设置，使用 referencePrice
            maxBuyPrice = config.getReferencePrice() > 0 ? 
                config.getReferencePrice() : 1.0;
        }
        
        // 条件1: 价格要低于最高买入价
        if (lastPrice >= maxBuyPrice) {
            log.debug("Price too high: lastPrice={} >= maxBuyPrice={}", lastPrice, maxBuyPrice);
            return false;
        }
        
        // 条件2: 支撑比率足够
        if (supportRatio < config.getMinSupportRatio()) {
            log.debug("Support ratio too low: {} < {}", supportRatio, config.getMinSupportRatio());
            return false;
        }
        
        // 条件3: 当前价差至少覆盖最小利润要求
        double minProfit = config.getMinProfitTick();
        double spread = ticker.getBestAskPrice().doubleValue() - ticker.getBestBidPrice().doubleValue();
        if (spread + 1e-9 < minProfit) {
            log.debug("Insufficient spread: {} < {}", spread, minProfit);
            return false;
        }
        
        // 获取参考价格用于计算偏离度（仅用于日志）
        double refPrice = config.getReferencePrice() > 0 ? config.getReferencePrice() : 1.0;
        
        log.info("✓ Buy signal detected: lastPrice={}, maxBuyPrice={}, refPrice={}, deviation={}, supportRatio={}, minProfit={}", 
            String.format("%.6f", lastPrice), 
            String.format("%.6f", maxBuyPrice),
            String.format("%.6f", refPrice),
            String.format("%.6f", maxBuyPrice - lastPrice),
            String.format("%.2f", supportRatio),
            String.format("%.6f", minProfit));
        return true;
    }
    
    /**
     * 计算本次买入金额
     * 
     * 新逻辑：根据价格偏离程度动态计算买入金额
     * - 价格偏离越多，买入金额越大
     * - 但不超过 maxBuyAmountUsdt
     * - 也不超过可用资金
     * 
     * 计算公式：
     * deviation = (referencePrice - lastPrice) / referencePrice
     * buyRatio = min(deviation / 0.01, 1.0)  // 偏离1%时满额买入
     * amount = maxBuyAmountUsdt * buyRatio
     * 
     * @return 买入金额（USDT）
     */
    private double calculateBuyAmount(StrategyConfig config, TickerData ticker, double availableFunds) {
        double lastPrice = ticker.getLastPrice().doubleValue();
        double buyPrice = ticker.getBestBidPrice().doubleValue();

        // 获取最大买入金额
        double maxBuyAmount = config.getMaxBuyAmountUsdt();
        if (maxBuyAmount <= 0) {
            maxBuyAmount = 15.0; // 默认15 USDT
        }

        // 基础目标金额：10U左右（整数），带少量随机扰动
        int baseAmountInt = 10;
        int rangeDown = 1; // 10-1 = 9
        int rangeUp = 1;   // 10+1 = 11

        // 根据交易规则计算最小可成交金额（考虑 stepSize/minNotional）
        int minAmountInt = 1;
        try {
            var filter = apiService.getSymbolFilter(config.getSymbol());
            double minNotional = filter.getMinNotional();
            double stepSize = filter.getStepSize();
            if (minNotional > 0 && stepSize > 0) {
                double minQty = Math.ceil((minNotional / buyPrice) / stepSize) * stepSize;
                double minNotionalRequired = minQty * buyPrice;
                minAmountInt = (int) Math.ceil(minNotionalRequired);
            }
        } catch (Exception e) {
            log.debug("Failed to load symbol filter for minNotional calc: {}", e.getMessage());
        }

        int maxAmountInt = (int) Math.floor(Math.min(maxBuyAmount, availableFunds));
        if (maxAmountInt <= 0 || maxAmountInt < minAmountInt) {
            log.warn("Buy amount unavailable: availableFunds={}, maxBuyAmount={}, minAmountInt={}",
                String.format("%.2f", availableFunds),
                String.format("%.2f", maxBuyAmount),
                minAmountInt);
            return 0.0;
        }

        int targetBase = Math.max(baseAmountInt, minAmountInt);
        int lower = Math.max(minAmountInt, targetBase - rangeDown);
        int upper = Math.min(maxAmountInt, targetBase + rangeUp);
        if (upper < lower) {
            log.warn("Buy amount unavailable: lower>{} upper={}, minAmountInt={}, maxAmountInt={}",
                lower, upper, minAmountInt, maxAmountInt);
            return 0.0;
        }
        int amountInt = lower + random.nextInt(upper - lower + 1);

        double amount = Math.min(amountInt, maxBuyAmount);
        amount = Math.min(amount, availableFunds);

        // 记录日志
        log.info("Buy amount calculated: targetBase={}U, rangeDown={}, rangeUp={}, minAmountInt={}U, maxAmountInt={}U, finalAmount={}U",
            targetBase, rangeDown, rangeUp, minAmountInt, maxAmountInt, (int) Math.floor(amount));

        return amount;
    }
    
    /**
     * 执行买入
     * @param orderAmount 本次订单金额（USDT）
     */
    private void executeBuy(StrategyConfig config, TickerData ticker, double orderAmount) {
        try {
            String symbol = config.getSymbol();
            
            // 下单前检查挂单数量
            if (!checkAndCleanupOrders(symbol)) {
                log.warn("✗ Cannot place order: too many open orders after cleanup");
                return;
            }
            
            double buyPrice = ticker.getBestBidPrice().doubleValue();  // 使用当前买价下限价单
            double quantity = orderAmount / buyPrice;
            
            // 根据交易规则调整数量，同时验证 NOTIONAL 要求
            quantity = apiService.adjustQuantityAndNotional(symbol, quantity, buyPrice);
            
            // 严格控制最大下单金额（防止 NOTIONAL 调整后放大）
            double maxOrderAmount = Math.min(orderAmount, config.getMaxBuyAmountUsdt());
            double notional = quantity * buyPrice;
            if (notional > maxOrderAmount + 1e-9) {
                var filter = apiService.getSymbolFilter(symbol);
                double stepSize = filter.getStepSize();
                double capQty = Math.floor((maxOrderAmount / buyPrice) / stepSize) * stepSize;
                capQty = apiService.adjustQuantityDown(symbol, capQty);
                
                if (capQty <= 0 || capQty < filter.getMinQty()) {
                    log.warn("✗ Cannot place order: capQty below minQty (capQty={}, minQty={}, maxOrderAmount={})",
                        capQty, filter.getMinQty(), maxOrderAmount);
                    return;
                }
                
                double minNotional = filter.getMinNotional();
                if (minNotional > 0 && capQty * buyPrice < minNotional) {
                    log.warn("✗ Cannot place order: minNotional not met after cap (capQty={}, price={}, minNotional={})",
                        capQty, buyPrice, minNotional);
                    return;
                }
                
                quantity = capQty;
                notional = quantity * buyPrice;
            }
            
            // 下限价买单
            var result = apiService.placeLimitOrder(symbol, "BUY", quantity, buyPrice);
            
            // 记录订单
            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setSymbol(symbol);
            order.setMode(config.getMode());
            order.setSide("BUY");
            order.setPrice(buyPrice);
            order.setQuantity(quantity);
            order.setOrderId(result.getOrderId());
            order.setStatus(Order.OrderStatus.SUBMITTED);
            orderService.createOrder(order);
            
            // 使用TRADE logger记录下单日志
            org.slf4j.Logger tradeLogger = org.slf4j.LoggerFactory.getLogger("TRADE");
            tradeLogger.info("[{}] BUY ORDER PLACED - orderId={}, localId={}, price={}, qty={}, amount={}U, mode={}", 
                symbol, result.getOrderId(), order.getId(), buyPrice, quantity, 
                String.format("%.2f", orderAmount), config.getMode());
            
            // 启动监控线程，等待成交后挂卖单
            monitorOrderFill(order.getId(), config);
            
        } catch (Exception e) {
            if (isInsufficientBalanceError(e)) {
                markInsufficientBalanceCooldown();
                log.warn("Insufficient balance detected, enter cooldown {}s", 
                    INSUFFICIENT_BALANCE_COOLDOWN_MS / 1000);
            }
            log.error("Failed to execute buy order", e);
        }
    }
    
    /**
     * 监控订单成交
     */
    private void monitorOrderFill(String localOrderId, StrategyConfig config) {
        Thread.startVirtualThread(() -> {
            try {
                // 查询本地订单
                Order order = waitForOrder(localOrderId, 3000);
                if (order == null) {
                    log.warn("Order not found after waiting: localOrderId={}", localOrderId);
                    return;
                }
                
                // 轮询检查订单状态
                for (int i = 0; i < 60; i++) {  // 最多检查1分钟
                    Thread.sleep(1000);
                    
                    var apiOrder = apiService.getOrder(order.getSymbol(), order.getOrderId());
                    
                    if ("FILLED".equals(apiOrder.getStatus())) {
                        // 订单成交，记录成交信息
                        double executedPrice = apiOrder.getExecutedPrice();
                        double executedQty = apiOrder.getExecutedQty();
                        double slippage = executedPrice - order.getPrice();
                        
                        orderService.recordFill(localOrderId, executedPrice, executedQty, slippage);
                        positionService.updatePositionOnBuy(
                            order.getSymbol(), 
                            order.getMode(), 
                            executedQty, 
                            executedPrice
                        );
                        
                        // 立即挂卖单
                        double sellPrice = executedPrice + config.getMinProfitTick();
                        executeSell(config, executedQty, sellPrice, localOrderId);
                        
                        log.info("Buy order filled and sell order placed: buyOrderId={}", localOrderId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error monitoring order fill", e);
            }
        });
    }
    
    /**
     * 执行卖出
     */
    private void executeSell(StrategyConfig config, double quantity, double sellPrice, String relatedBuyOrderId) {
        try {
            String symbol = config.getSymbol();
            
            // 根据交易规则调整数量，同时验证 NOTIONAL 要求
            quantity = apiService.adjustQuantityAndNotional(symbol, quantity, sellPrice);
            
            // 下限价卖单
            var result = apiService.placeLimitOrder(symbol, "SELL", quantity, sellPrice);
            
            // 记录订单
            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setSymbol(symbol);
            order.setMode(config.getMode());
            order.setSide("SELL");
            order.setPrice(sellPrice);
            order.setQuantity(quantity);
            order.setOrderId(result.getOrderId());
            order.setStatus(Order.OrderStatus.SUBMITTED);
            orderService.createOrder(order);
            
            // 关联买卖单
            orderService.linkOrders(relatedBuyOrderId, order.getId());
            
            // 使用TRADE logger记录下单日志
            org.slf4j.Logger tradeLogger = org.slf4j.LoggerFactory.getLogger("TRADE");
            tradeLogger.info("[{}] SELL ORDER PLACED - orderId={}, localId={}, price={}, qty={}, mode={}, relatedBuyOrder={}", 
                symbol, result.getOrderId(), order.getId(), sellPrice, quantity, config.getMode(), relatedBuyOrderId);
            
            // 启动监控线程，等待卖单成交后记录交易统计
            monitorSellOrderFill(order.getId(), config);
            
        } catch (Exception e) {
            log.error("Failed to execute sell order", e);
        }
    }
    
    /**
     * 检查并撤销过期订单
     */
    private void checkAndCancelExpiredOrders(StrategyConfig config) {
        try {
            List<Order> openOrders = orderService.getOpenOrders(config.getSymbol());
            LocalDateTime now = LocalDateTime.now();
            
            for (Order order : openOrders) {
                if (order.getCreateTime() == null) {
                    log.warn("Order missing createTime, skipping expiry check: localId={}", order.getId());
                    continue;
                }
                
                if ("SELL".equals(order.getSide())) {
                    long holdSeconds = Duration.between(order.getCreateTime(), now).getSeconds();
                    
                    if (holdSeconds > config.getMaxHoldSeconds()) {
                        // 超时，撤单并市价平仓
                        org.slf4j.Logger tradeLogger = org.slf4j.LoggerFactory.getLogger("TRADE");
                        tradeLogger.warn("[{}] ORDER EXPIRED - orderId={}, localId={}, holdSeconds={}, canceling...", 
                            order.getSymbol(), order.getOrderId(), order.getId(), holdSeconds);
                        
                        if (order.getOrderId() != null) {
                            try {
                                apiService.cancelOrder(order.getSymbol(), order.getOrderId());
                            } catch (Exception e) {
                                log.warn("SELL cancel failed, marking expired: localId={}, err={}", order.getId(), e.getMessage());
                            }
                            orderService.markOrderExpired(order.getId());
                        } else {
                            log.warn("SELL order missing orderId, marking expired only: localId={}", order.getId());
                            orderService.markOrderExpired(order.getId());
                            continue;
                        }
                        
                        tradeLogger.info("[{}] ORDER CANCELED - orderId={}, localId={}", 
                            order.getSymbol(), order.getOrderId(), order.getId());
                        
                        // 获取当前价格用于验证 NOTIONAL
                        double currentPrice = apiService.getCurrentPrice(order.getSymbol());
                        
                        // 根据交易规则调整数量，同时验证 NOTIONAL 要求
                        double adjustedQty = apiService.adjustQuantityAndNotional(
                            order.getSymbol(), order.getQuantity(), currentPrice);
                        
                        // 市价平仓
                        var marketSell = apiService.placeMarketOrder(
                            order.getSymbol(), 
                            "SELL", 
                            adjustedQty
                        );
                        
                        // 计算实际成交价格
                        double executedSellPrice = marketSell.getExecutedQty() > 0 ? 
                            marketSell.getCummulativeQuoteQty() / marketSell.getExecutedQty() : 0;
                        
                        // 更新持仓
                        positionService.updatePositionOnSell(
                            order.getSymbol(),
                            order.getMode(),
                            marketSell.getExecutedQty(),
                            executedSellPrice
                        );
                        
                        // 记录交易统计（市价平仓）
                        recordTradeStats(order.getId(), executedSellPrice, marketSell.getExecutedQty(), now);
                        
                        tradeLogger.info("[{}] MARKET SELL EXECUTED - executedPrice={}, executedQty={}", 
                            order.getSymbol(), executedSellPrice, marketSell.getExecutedQty());
                    }
                } else if ("BUY".equals(order.getSide())) {
                    long holdSeconds = Duration.between(order.getCreateTime(), now).getSeconds();
                    int maxBuyOpenSeconds = config.getMaxBuyOpenSeconds();
                    if (maxBuyOpenSeconds > 0 && holdSeconds > maxBuyOpenSeconds) {
                        org.slf4j.Logger tradeLogger = org.slf4j.LoggerFactory.getLogger("TRADE");
                        tradeLogger.warn("[{}] BUY ORDER EXPIRED - orderId={}, localId={}, holdSeconds={}, canceling...", 
                            order.getSymbol(), order.getOrderId(), order.getId(), holdSeconds);
                        
                        if (order.getOrderId() != null) {
                            try {
                                apiService.cancelOrder(order.getSymbol(), order.getOrderId());
                            } catch (Exception e) {
                                log.warn("BUY cancel failed, marking canceled: localId={}, err={}", order.getId(), e.getMessage());
                            }
                            orderService.markOrderCanceled(order.getId());
                        } else {
                            log.warn("BUY order missing orderId, marking canceled only: localId={}", order.getId());
                            orderService.markOrderCanceled(order.getId());
                        }
                        
                        tradeLogger.info("[{}] BUY ORDER CANCELED - orderId={}, localId={}", 
                            order.getSymbol(), order.getOrderId(), order.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking expired orders", e);
        }
    }
    
    /**
     * 监控卖单成交
     * 当卖单成交后，记录交易统计
     */
    private void monitorSellOrderFill(String localSellOrderId, StrategyConfig config) {
        Thread.startVirtualThread(() -> {
            try {
                // 查询本地卖单
                Order sellOrder = waitForOrder(localSellOrderId, 3000);
                if (sellOrder == null) {
                    log.warn("Sell order not found after waiting: localSellOrderId={}", localSellOrderId);
                    return;
                }
                
                // 轮询检查订单状态
                for (int i = 0; i < 3600; i++) {  // 最多检查1小时（适配最大持仓时间30分钟）
                    Thread.sleep(1000);
                    
                    var apiOrder = apiService.getOrder(sellOrder.getSymbol(), sellOrder.getOrderId());
                    
                    if ("FILLED".equals(apiOrder.getStatus())) {
                        // 卖单成交，记录成交信息
                        double executedPrice = apiOrder.getExecutedPrice();
                        double executedQty = apiOrder.getExecutedQty();
                        double slippage = executedPrice - sellOrder.getPrice();
                        
                        orderService.recordFill(localSellOrderId, executedPrice, executedQty, slippage);
                        
                        // 更新持仓
                        positionService.updatePositionOnSell(
                            sellOrder.getSymbol(),
                            sellOrder.getMode(),
                            executedQty,
                            executedPrice
                        );
                        
                        // 记录交易统计
                        recordTradeStats(localSellOrderId, executedPrice, executedQty, LocalDateTime.now());
                        
                        log.info("Sell order filled and trade stats recorded: sellOrderId={}", localSellOrderId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error monitoring sell order fill", e);
            }
        });
    }
    
    /**
     * 等待订单写入可见（避免异步写库导致的读不到）
     */
    private Order waitForOrder(String orderId, int maxWaitMs) throws InterruptedException {
        int attempts = Math.max(1, maxWaitMs / 100);
        for (int i = 0; i < attempts; i++) {
            Order order = orderService.getOrderById(orderId);
            if (order != null) {
                return order;
            }
            Thread.sleep(100);
        }
        return null;
    }
    
    /**
     * 检查并清理挂单
     * 如果挂单数量超过阈值，清理最旧的挂单
     * 
     * @param symbol 交易对
     * @return true 如果可以继续下单，false 如果挂单数量仍然过多
     */
    private boolean checkAndCleanupOrders(String symbol) {
        try {
            // 从币安API查询实际挂单数量
            int openOrdersCount = apiService.checkOpenOrdersCount(symbol);
            
            // 如果未超过阈值，直接返回
            if (openOrdersCount < MAX_OPEN_ORDERS_THRESHOLD) {
                return true;
            }
            
            log.warn("⚠ Open orders count ({}) exceeds threshold ({}), cleaning up...", 
                openOrdersCount, MAX_OPEN_ORDERS_THRESHOLD);
            
            // 计算需要清理的数量
            int cleanupCount = Math.min(CLEANUP_BATCH_SIZE, openOrdersCount - TARGET_OPEN_ORDERS);
            
            // 从本地数据库获取最旧的挂单
            List<Order> oldestOrders = orderService.getOldestOpenOrders(symbol, cleanupCount);
            
            if (oldestOrders.isEmpty()) {
                log.warn("No old orders found in local database, syncing with Binance...");
                // 如果本地没有数据，从币安API获取
                List<BinanceOrder> binanceOrders = apiService.getOpenOrders(symbol);
                if (!binanceOrders.isEmpty()) {
                    // 取前 cleanupCount 个订单撤销
                    List<Long> orderIdsToCancel = binanceOrders.stream()
                        .limit(cleanupCount)
                        .map(BinanceOrder::getOrderId)
                        .toList();
                    
                    int canceledCount = apiService.cancelOrders(symbol, orderIdsToCancel);
                    log.info("Cleaned up {} orders from Binance API", canceledCount);
                    
                    return canceledCount > 0;
                }
                return false;
            }
            
            // 批量撤销订单
            List<Long> orderIdsToCancel = oldestOrders.stream()
                .filter(order -> order.getOrderId() != null && order.getOrderId() > 0)
                .map(Order::getOrderId)
                .toList();
            
            if (orderIdsToCancel.isEmpty()) {
                log.warn("No valid order IDs to cancel");
                return false;
            }
            
            int canceledCount = apiService.cancelOrders(symbol, orderIdsToCancel);
            
            // 更新本地订单状态
            for (Order order : oldestOrders) {
                if (order.getOrderId() != null && order.getOrderId() > 0) {
                    orderService.markOrderCanceled(order.getId());
                }
            }
            
            log.info("✓ Cleaned up {} old orders, target reached", canceledCount);
            
            // 检查清理后的数量
            int remainingCount = apiService.checkOpenOrdersCount(symbol);
            return remainingCount < MAX_OPEN_ORDERS_THRESHOLD;
            
        } catch (Exception e) {
            log.error("Error checking/cleaning up orders", e);
            return false;
        }
    }
    
    /**
     * 记录交易统计
     * 根据卖单信息计算PnL、持仓时间等并记录
     * 
     * @param sellOrderId 卖单ID
     * @param sellPrice 卖出价格
     * @param sellQty 卖出数量
     * @param sellTime 卖出时间
     */
    private void recordTradeStats(String sellOrderId, double sellPrice, double sellQty, LocalDateTime sellTime) {
        Thread.startVirtualThread(() -> {
            try {
                // 查询卖单
                Order sellOrder = orderService.getOrderById(sellOrderId);
                if (sellOrder == null) {
                    log.warn("Sell order not found: sellOrderId={}", sellOrderId);
                    return;
                }
                
                // 查询关联的买单
                String buyOrderId = sellOrder.getRelatedOrderId();
                if (buyOrderId == null) {
                    log.warn("Buy order not found for sell order: sellOrderId={}", sellOrderId);
                    return;
                }
                
                Order buyOrder = orderService.getOrderById(buyOrderId);
                if (buyOrder == null || buyOrder.getExecutedPrice() == null || buyOrder.getFillTime() == null) {
                    log.warn("Buy order data incomplete: buyOrderId={}", buyOrderId);
                    return;
                }
                
                // 计算盈亏（PnL）
                double buyPrice = buyOrder.getExecutedPrice();
                double pnl = (sellPrice - buyPrice) * sellQty;
                
                // 计算持仓时间（秒）
                int holdSeconds = (int) Duration.between(buyOrder.getFillTime(), sellTime).getSeconds();
                
                // 计算滑点（使用买单的滑点，卖单滑点对盈亏影响较小）
                double slippage = buyOrder.getSlippage() != null ? buyOrder.getSlippage() : 0.0;
                
                // 记录交易统计
                statsService.recordTrade(sellOrder.getSymbol(), pnl, holdSeconds, slippage);
                
                org.slf4j.Logger tradeLogger = org.slf4j.LoggerFactory.getLogger("TRADE");
                tradeLogger.info("[{}] TRADE COMPLETED - buyPrice={}, sellPrice={}, qty={}, pnl={} USDT, holdSeconds={}s", 
                    sellOrder.getSymbol(), 
                    String.format("%.4f", buyPrice), 
                    String.format("%.4f", sellPrice), 
                    String.format("%.2f", sellQty),
                    String.format("%.4f", pnl), 
                    holdSeconds);
            } catch (Exception e) {
                log.error("Failed to record trade stats: sellOrderId={}", sellOrderId, e);
            }
        });
    }

    private static final class ExchangeFundsSnapshot {
        private final boolean isValid;
        private final double freeUsdt;
        private final double openBuyNotional;

        private ExchangeFundsSnapshot(boolean isValid, double freeUsdt, double openBuyNotional) {
            this.isValid = isValid;
            this.freeUsdt = freeUsdt;
            this.openBuyNotional = openBuyNotional;
        }
    }

    private static final class EffectiveFunds {
        private final boolean isValid;
        private final double effectiveAvailable;
        private final double configAvailable;
        private final double exchangeFreeUsdt;
        private final double openBuyNotional;

        private EffectiveFunds(boolean isValid, double effectiveAvailable, double configAvailable,
                               double exchangeFreeUsdt, double openBuyNotional) {
            this.isValid = isValid;
            this.effectiveAvailable = effectiveAvailable;
            this.configAvailable = configAvailable;
            this.exchangeFreeUsdt = exchangeFreeUsdt;
            this.openBuyNotional = openBuyNotional;
        }

        private static EffectiveFunds invalid(double configAvailable) {
            return new EffectiveFunds(false, 0.0, configAvailable, Double.NaN, 0.0);
        }
    }
}
