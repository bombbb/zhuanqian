# USDC稳定币套利交易系统 - 执行计划

## 项目理解

**核心目标**：在USDC/USDT价格偏离1.0时，利用深度支撑判断和均值回归特性，通过限价单获取1-2个tick的确定性利润。

**关键策略**：
- 当价格低于basePrice且深度支撑充足时买入
- 买入后立即挂出卖单，等待价格回归
- 如果超过maxHoldSeconds仍未成交，撤单平仓
- 使用测试网交易，正式网行情

**数据分析结果**（基于历史数据）：
- 2024-2025年USDC价格非常稳定
- 主要成交区间：0.9995-1.0010
- 最活跃区间：0.9998-1.0005（约62%的成交量）
- 2025年 <0.9995 的成交占比为0%

---

## 零、MongoDB初始化配置（新增）

### 0.1 合理参数分析

根据历史数据分析，推荐以下策略参数：

**basePrice（基准价格）**: **1.0000**
- 理由：2025年数据显示，价格主要分布在0.9995-1.0010
- 1.0000是中间值，既是锚定价格，也是历史高频成交区

**minProfitTick（最小利润空间）**: **0.0001** (1个tick)
- 理由：USDC波动小，1个tick（0.01%）已经是合理利润
- 300 USDT * 0.01% = 0.03 USDT，每单收益虽小但确定性高

**maxHoldSeconds（最大持仓时间）**: **1800** (30分钟)
- 理由：稳定币均值回归较快，通常10-30分钟内会回归
- 30分钟可覆盖大部分正常波动周期

**tradeAmountUsdt（单笔交易金额）**: **100** USDT
- 理由：初始资金300 USDT，单笔100允许最多3个并发持仓
- 既保证资金利用率，又控制风险

**minSupportRatio（最小支撑比率）**: **0.6** (60%)
- 理由：当买盘深度60%集中在当前价附近，说明下跌空间有限
- 这是强支撑信号，回归概率高

**supportRangeNear（近端支撑范围）**: **0.0025** (25个tick)
- 理由：在USDC这种稳定币上，0.0025的范围足够捕捉关键支撑
- 对应价格约0.9975-1.0025

**basePrice动态调整逻辑**（未来优化）:
- 可以根据最近1小时的lastPrice均值动态调整
- 当前先用固定值1.0000

### 0.2 MongoDB初始化脚本

```javascript
// 切换到数据库
use strategy_db;

// 插入USDC策略配置
db.strategy_config.insertOne({
  _id: "USDCUSDT_TESTNET",
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  
  // 核心策略参数（基于历史数据分析）
  minProfitTick: 0.0001,        // 最小利润1个tick
  maxHoldSeconds: 1800,         // 最大持仓30分钟
  tradeAmountUsdt: 100.0,       // 单笔100 USDT
  minSupportRatio: 0.6,         // 最小支撑比60%
  basePrice: 1.0000,            // 基准价格（2025年中位数）
  
  // 深度范围配置
  supportRangeNear: 0.0025,     // 近端支撑范围
  supportRangeMid: 0.005,       // 中端支撑范围（预留）
  supportRangeFar: 0.01,        // 远端支撑范围（预留）
  
  // 测试网API配置（从usdt项目提取）
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k",
  testnetSecretKey: "HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V",
  
  // 正式网配置（暂不使用）
  productionApiUrl: "https://api.binance.com",
  productionApiKey: "",
  productionSecretKey: ""
});

// 创建索引
db.strategy_config.createIndex({ "symbol": 1, "enabled": 1 });

// 验证插入
db.strategy_config.findOne({ symbol: "USDCUSDT" });
```

### 0.3 创建统计表索引

```javascript
// 订单表索引
db.orders.createIndex({ "symbol": 1, "status": 1, "createTime": -1 });
db.orders.createIndex({ "orderId": 1 });
db.orders.createIndex({ "relatedOrderId": 1 });

// 持仓表索引
db.positions.createIndex({ "symbol": 1, "mode": 1 });

// 统计表索引
db.spread_stats.createIndex({ "symbol": 1, "date": 1, "bidPrice": 1, "askPrice": 1 }, { unique: true });
db.depth_stats.createIndex({ "symbol": 1, "date": 1, "priceRange": 1, "supportRatioRange": 1 }, { unique: true });
db.trade_stats.createIndex({ "symbol": 1, "date": 1 }, { unique: true });
```

---

## 一、Domain模型设计（TDD起点）

### 1.1 订单实体（Order）

**文件**: `src/main/java/com/zq/order/Order.java`

```java
@Data
@Document(collection = "orders")
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    @Id
    private String id;
    
    private String symbol;          // USDCUSDT
    private Mode mode;              // TESTNET/PRODUCTION
    private String side;            // BUY/SELL
    private OrderStatus status;     // NEW/FILLED/CANCELED/EXPIRED
    
    private Double price;           // 下单价格
    private Double quantity;        // 数量
    private Double executedPrice;   // 实际成交价格
    private Double executedQty;     // 实际成交数量
    private Double slippage;        // 滑点
    
    private Long orderId;           // 币安订单ID
    private String clientOrderId;   // 客户端订单ID
    
    private LocalDateTime createTime;
    private LocalDateTime fillTime;
    private LocalDateTime cancelTime;
    
    private String relatedOrderId;  // 关联订单ID（买单关联卖单）
    
    public enum OrderStatus {
        NEW,           // 新建
        SUBMITTED,     // 已提交到交易所
        FILLED,        // 已成交
        CANCELED,      // 已撤销
        EXPIRED        // 已过期
    }
    
    public enum Mode {
        SIMULATION,    // 模拟模式
        TESTNET,       // 测试网
        PRODUCTION     // 正式网
    }
}
```

### 1.2 持仓实体（Position）

**文件**: `src/main/java/com/zq/position/Position.java`

```java
@Data
@Document(collection = "positions")
@AllArgsConstructor
@NoArgsConstructor
public class Position {
    @Id
    private String id;
    
    private String symbol;              // USDCUSDT
    private Order.Mode mode;            // TESTNET/PRODUCTION
    
    private Double quantity;            // 持仓数量（USDC）
    private Double avgBuyPrice;         // 平均买入价格
    private Double currentPrice;        // 当前价格
    private Double unrealizedPnl;       // 未实现盈亏
    private Double totalInvestedUsdt;   // 总投入USDT金额
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 辅助方法
    public double getPositionValue() {
        return quantity * currentPrice;
    }
    
    public double getUnrealizedPnlPercent() {
        return totalInvestedUsdt > 0 ? (unrealizedPnl / totalInvestedUsdt) * 100 : 0;
    }
}
```

### 1.3 统计表实体

**价差统计表**（SpreadStats）:

```java
@Data
@Document(collection = "spread_stats")
@AllArgsConstructor
@NoArgsConstructor
public class SpreadStats {
    @Id
    private String id;
    
    private String symbol;
    private LocalDate date;            // 统计日期
    private Double bidPrice;           // 买价（4位小数）
    private Double askPrice;           // 卖价（4位小数）
    private Integer count;             // 出现次数
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 生成复合ID: USDCUSDT_2025-01-18_1.0005_1.0006
    public static String generateId(String symbol, LocalDate date, double bid, double ask) {
        return String.format("%s_%s_%.4f_%.4f", symbol, date, bid, ask);
    }
}
```

**深度区间统计表**（DepthStats）:

```java
@Data
@Document(collection = "depth_stats")
@AllArgsConstructor
@NoArgsConstructor
public class DepthStats {
    @Id
    private String id;
    
    private String symbol;
    private LocalDate date;
    private String priceRangeBucket;      // 价格区间桶 "0.9995-1.0000"
    private String supportRatioBucket;    // 支撑比率桶 "0.6-0.7"
    private Integer count;                // 出现次数
    private Double avgVolume;             // 平均深度量（累计求平均）
    
    private LocalDateTime updateTime;
    
    // 生成区间桶
    public static String generatePriceRangeBucket(double price) {
        // 按0.0005区间分桶
        double bucket = Math.floor(price / 0.0005) * 0.0005;
        return String.format("%.4f-%.4f", bucket, bucket + 0.0005);
    }
    
    public static String generateSupportRatioBucket(double ratio) {
        // 按0.1区间分桶
        double bucket = Math.floor(ratio / 0.1) * 0.1;
        return String.format("%.1f-%.1f", bucket, bucket + 0.1);
    }
}
```

**交易统计表**（TradeStats）:

```java
@Data
@Document(collection = "trade_stats")
@AllArgsConstructor
@NoArgsConstructor
public class TradeStats {
    @Id
    private String id;  // USDCUSDT_2025-01-18
    
    private String symbol;
    private LocalDate date;
    
    private Integer totalTrades;       // 总交易次数（完整买卖对）
    private Integer profitTrades;      // 盈利次数
    private Integer lossTrades;        // 亏损次数
    
    private Double totalPnl;           // 总盈亏（USDT）
    private Double totalPnlPercent;    // 总盈亏百分比
    
    private Double avgHoldSeconds;     // 平均持仓时间（秒）
    private Double avgSlippage;        // 平均滑点
    
    private Double maxProfit;          // 最大单笔盈利
    private Double maxLoss;            // 最大单笔亏损
    
    private LocalDateTime updateTime;
}
```

---

## 二、核心功能模块（按TDD顺序）

### 2.1 币安API交易模块（优先级最高）

**文件**: `src/main/java/com/zq/api/BinanceApiService.java`

参考 `/Users/bao/java/usdt/src/main/java/com/usdt/account/BinanceAccount.java`，实现：
- `getBalance(asset)` - 查询余额
- `placeLimitOrder(symbol, side, qty, price)` - 下限价单
- `placeMarketOrder(symbol, side, qty)` - 下市价单
- `cancelOrder(symbol, orderId)` - 撤单
- `getOrder(symbol, orderId)` - 查询订单状态
- `getOpenOrders(symbol)` - 查询挂单

**测试用例**:
```java
@Test
void testGetBalance() {
    // 测试网查询USDT余额
    Balance balance = apiService.getBalance("USDT");
    assertNotNull(balance);
    assertTrue(balance.getFree() >= 0);
}

@Test
void testPlaceLimitOrder() {
    // 下限价买单
    OrderResult result = apiService.placeLimitOrder(
        "USDCUSDT", "BUY", 100.0, 0.9995
    );
    assertNotNull(result.getOrderId());
    assertEquals("NEW", result.getStatus());
}

@Test
void testCancelOrder() {
    // 先下单，再撤单
    OrderResult order = apiService.placeLimitOrder(...);
    OrderResult cancel = apiService.cancelOrder("USDCUSDT", order.getOrderId());
    assertEquals("CANCELED", cancel.getStatus());
}
```

### 2.2 订单管理服务

**文件**: `src/main/java/com/zq/order/OrderService.java`

使用Java 21虚拟线程 + MongoTemplate：

```java
@Service
@Slf4j
public class OrderService {
    @Resource
    private MongoTemplate mongoTemplate;
    
    public Order createOrder(Order order) {
        order.setCreateTime(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.NEW);
        
        // 使用虚拟线程异步插入
        Thread.startVirtualThread(() -> {
            mongoTemplate.insert(order);
            log.info("Order created: {}", order.getId());
        });
        
        return order;
    }
    
    public void updateOrderStatus(String orderId, Order.OrderStatus status) {
        Thread.startVirtualThread(() -> {
            Query query = query(where("id").is(orderId));
            Update update = new Update()
                .set("status", status)
                .set("updateTime", LocalDateTime.now());
            
            mongoTemplate.updateFirst(query, update, Order.class);
        });
    }
    
    public void recordFill(String orderId, double executedPrice, double executedQty, double slippage) {
        Thread.startVirtualThread(() -> {
            Query query = query(where("id").is(orderId));
            Update update = new Update()
                .set("status", Order.OrderStatus.FILLED)
                .set("executedPrice", executedPrice)
                .set("executedQty", executedQty)
                .set("slippage", slippage)
                .set("fillTime", LocalDateTime.now());
            
            mongoTemplate.updateFirst(query, update, Order.class);
            log.info("Order filled: orderId={}, price={}, qty={}", orderId, executedPrice, executedQty);
        });
    }
    
    public List<Order> getOpenOrders(String symbol) {
        Query query = query(
            where("symbol").is(symbol)
            .and("status").in(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED)
        );
        return mongoTemplate.find(query, Order.class);
    }
    
    public void linkOrders(String buyOrderId, String sellOrderId) {
        Thread.startVirtualThread(() -> {
            Query query = query(where("id").is(buyOrderId));
            Update update = new Update().set("relatedOrderId", sellOrderId);
            mongoTemplate.updateFirst(query, update, Order.class);
            
            query = query(where("id").is(sellOrderId));
            update = new Update().set("relatedOrderId", buyOrderId);
            mongoTemplate.updateFirst(query, update, Order.class);
        });
    }
}
```

**测试用例**:
```java
@Test
void testCreateAndQueryOrder() {
    Order order = new Order();
    order.setSymbol("USDCUSDT");
    order.setSide("BUY");
    order.setPrice(1.0000);
    
    orderService.createOrder(order);
    
    // 等待虚拟线程完成
    Thread.sleep(100);
    
    List<Order> orders = orderService.getOpenOrders("USDCUSDT");
    assertTrue(orders.size() > 0);
}
```

### 2.3 持仓管理服务

**文件**: `src/main/java/com/zq/position/PositionService.java`

```java
@Service
@Slf4j
public class PositionService {
    @Resource
    private MongoTemplate mongoTemplate;
    
    private final double initialUsdt = 300.0;  // 从配置读取
    
    public void updatePositionOnBuy(String symbol, Order.Mode mode, double buyQty, double buyPrice) {
        Thread.startVirtualThread(() -> {
            Query query = query(where("symbol").is(symbol).and("mode").is(mode));
            Position position = mongoTemplate.findOne(query, Position.class);
            
            if (position == null) {
                position = new Position();
                position.setSymbol(symbol);
                position.setMode(mode);
                position.setQuantity(buyQty);
                position.setAvgBuyPrice(buyPrice);
                position.setTotalInvestedUsdt(buyQty * buyPrice);
                position.setCreateTime(LocalDateTime.now());
                mongoTemplate.insert(position);
            } else {
                double newTotalQty = position.getQuantity() + buyQty;
                double newTotalInvested = position.getTotalInvestedUsdt() + (buyQty * buyPrice);
                double newAvgPrice = newTotalInvested / newTotalQty;
                
                Update update = new Update()
                    .set("quantity", newTotalQty)
                    .set("avgBuyPrice", newAvgPrice)
                    .set("totalInvestedUsdt", newTotalInvested)
                    .set("updateTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Position.class);
            }
        });
    }
    
    public void updatePositionOnSell(String symbol, Order.Mode mode, double sellQty, double sellPrice) {
        Thread.startVirtualThread(() -> {
            Query query = query(where("symbol").is(symbol).and("mode").is(mode));
            Position position = mongoTemplate.findOne(query, Position.class);
            
            if (position != null) {
                double newQty = Math.max(0, position.getQuantity() - sellQty);
                double soldInvested = (sellQty / position.getQuantity()) * position.getTotalInvestedUsdt();
                double newInvested = Math.max(0, position.getTotalInvestedUsdt() - soldInvested);
                
                Update update = new Update()
                    .set("quantity", newQty)
                    .set("totalInvestedUsdt", newInvested)
                    .set("updateTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Position.class);
            }
        });
    }
    
    public double calculateUnrealizedPnl(String symbol, Order.Mode mode, double currentPrice) {
        Query query = query(where("symbol").is(symbol).and("mode").is(mode));
        Position position = mongoTemplate.findOne(query, Position.class);
        
        if (position == null || position.getQuantity() == 0) {
            return 0;
        }
        
        double currentValue = position.getQuantity() * currentPrice;
        return currentValue - position.getTotalInvestedUsdt();
    }
    
    public double getAvailableFunds(String symbol, Order.Mode mode) {
        Query query = query(where("symbol").is(symbol).and("mode").is(mode));
        Position position = mongoTemplate.findOne(query, Position.class);
        
        if (position == null) {
            return initialUsdt;
        }
        
        return initialUsdt - position.getTotalInvestedUsdt();
    }
}
```

### 2.4 统计服务（虚拟线程异步）

**文件**: `src/main/java/com/zq/stats/StatsService.java`

```java
@Service
@Slf4j
public class StatsService {
    @Resource
    private MongoTemplate mongoTemplate;
    
    public void recordSpread(String symbol, double bid, double ask) {
        Thread.startVirtualThread(() -> {
            LocalDate today = LocalDate.now();
            String id = SpreadStats.generateId(symbol, today, bid, ask);
            
            Query query = query(where("_id").is(id));
            SpreadStats stats = mongoTemplate.findOne(query, SpreadStats.class);
            
            if (stats == null) {
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
            } else {
                Update update = new Update()
                    .inc("count", 1)
                    .set("updateTime", LocalDateTime.now());
                mongoTemplate.updateFirst(query, update, SpreadStats.class);
            }
        });
    }
    
    public void recordDepth(String symbol, double price, double supportRatio, double volume) {
        Thread.startVirtualThread(() -> {
            LocalDate today = LocalDate.now();
            String priceRangeBucket = DepthStats.generatePriceRangeBucket(price);
            String supportRatioBucket = DepthStats.generateSupportRatioBucket(supportRatio);
            String id = String.format("%s_%s_%s_%s", symbol, today, priceRangeBucket, supportRatioBucket);
            
            Query query = query(where("_id").is(id));
            DepthStats stats = mongoTemplate.findOne(query, DepthStats.class);
            
            if (stats == null) {
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
            } else {
                // 增量计算平均值
                double newAvgVolume = (stats.getAvgVolume() * stats.getCount() + volume) / (stats.getCount() + 1);
                Update update = new Update()
                    .inc("count", 1)
                    .set("avgVolume", newAvgVolume)
                    .set("updateTime", LocalDateTime.now());
                mongoTemplate.updateFirst(query, update, DepthStats.class);
            }
        });
    }
    
    public void recordTrade(String symbol, double pnl, int holdSeconds, double slippage) {
        Thread.startVirtualThread(() -> {
            LocalDate today = LocalDate.now();
            String id = symbol + "_" + today;
            
            Query query = query(where("_id").is(id));
            TradeStats stats = mongoTemplate.findOne(query, TradeStats.class);
            
            boolean isProfit = pnl > 0;
            
            if (stats == null) {
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
            } else {
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
                
                if (isProfit && pnl > stats.getMaxProfit()) {
                    update.set("maxProfit", pnl);
                }
                if (!isProfit && pnl < stats.getMaxLoss()) {
                    update.set("maxLoss", pnl);
                }
                
                mongoTemplate.updateFirst(query, update, TradeStats.class);
            }
        });
    }
}
```

### 2.5 策略引擎完善

**文件**: `src/main/java/com/zq/strategy/StrategyEngine.java`

集成所有模块，实现完整交易流程：

```java
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
    
    public void onMarketData(TickerData ticker) {
        try {
            // 1. 获取策略配置
            StrategyConfig config = strategyService.getStrategyConfig();
            if (!config.isEnabled()) {
                return;
            }
            
            String symbol = config.getSymbol();
            double lastPrice = ticker.getLastPrice();
            double bid = ticker.getBid();
            double ask = ticker.getAsk();
            
            // 2. 异步记录价差统计
            statsService.recordSpread(symbol, bid, ask);
            
            // 3. 计算支撑比率
            double supportRatio = depthAnalyzer.calcSupportRatio(
                ticker.getBids(),
                lastPrice,
                config.getSupportRangeNear()
            );
            
            // 4. 异步记录深度统计
            double totalVolume = ticker.getBids().stream()
                .mapToDouble(b -> b[1])
                .sum();
            statsService.recordDepth(symbol, lastPrice, supportRatio, totalVolume);
            
            // 5. 判断是否满足买入条件
            if (shouldBuy(config, ticker, supportRatio)) {
                double availableFunds = positionService.getAvailableFunds(symbol, config.getMode());
                
                if (availableFunds >= config.getTradeAmountUsdt()) {
                    executeBuy(config, ticker);
                } else {
                    log.warn("Insufficient funds: available={}, required={}", 
                        availableFunds, config.getTradeAmountUsdt());
                }
            }
            
            // 6. 检查已持仓订单，是否需要撤单平仓
            checkAndCancelExpiredOrders(config);
            
        } catch (Exception e) {
            log.error("Error processing market data", e);
        }
    }
    
    private boolean shouldBuy(StrategyConfig config, TickerData ticker, double supportRatio) {
        double lastPrice = ticker.getLastPrice();
        double bid = ticker.getBid();
        
        // 条件1: 价格低于或等于basePrice
        if (lastPrice > config.getBasePrice()) {
            return false;
        }
        
        // 条件2: 支撑比率足够
        if (supportRatio < config.getMinSupportRatio()) {
            log.debug("Support ratio too low: {}", supportRatio);
            return false;
        }
        
        // 条件3: 预期利润空间足够
        double expectedSellPrice = config.getBasePrice() + config.getMinProfitTick();
        double profitSpace = expectedSellPrice - bid;
        if (profitSpace < config.getMinProfitTick()) {
            log.debug("Insufficient profit space: {}", profitSpace);
            return false;
        }
        
        log.info("Buy signal: lastPrice={}, basePrice={}, supportRatio={}, profitSpace={}", 
            lastPrice, config.getBasePrice(), supportRatio, profitSpace);
        return true;
    }
    
    private void executeBuy(StrategyConfig config, TickerData ticker) {
        try {
            String symbol = config.getSymbol();
            double buyPrice = ticker.getBid();  // 使用当前买价下限价单
            double quantity = config.getTradeAmountUsdt() / buyPrice;
            
            // 下限价买单
            OrderResult result = apiService.placeLimitOrder(symbol, "BUY", quantity, buyPrice);
            
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
            
            log.info("Buy order placed: orderId={}, price={}, qty={}", 
                result.getOrderId(), buyPrice, quantity);
            
            // 启动监控线程，等待成交后挂卖单
            monitorOrderFill(order.getId(), config);
            
        } catch (Exception e) {
            log.error("Failed to execute buy order", e);
        }
    }
    
    private void monitorOrderFill(String localOrderId, StrategyConfig config) {
        Thread.startVirtualThread(() -> {
            try {
                // 查询本地订单
                Order order = orderService.getOrderById(localOrderId);
                if (order == null) return;
                
                // 轮询检查订单状态
                for (int i = 0; i < 60; i++) {  // 最多检查1分钟
                    Thread.sleep(1000);
                    
                    com.zq.api.Order apiOrder = apiService.getOrder(order.getSymbol(), order.getOrderId());
                    
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
    
    private void executeSell(StrategyConfig config, double quantity, double sellPrice, String relatedBuyOrderId) {
        try {
            String symbol = config.getSymbol();
            
            // 下限价卖单
            OrderResult result = apiService.placeLimitOrder(symbol, "SELL", quantity, sellPrice);
            
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
            
            log.info("Sell order placed: orderId={}, price={}, qty={}", 
                result.getOrderId(), sellPrice, quantity);
            
        } catch (Exception e) {
            log.error("Failed to execute sell order", e);
        }
    }
    
    private void checkAndCancelExpiredOrders(StrategyConfig config) {
        try {
            List<Order> openOrders = orderService.getOpenOrders(config.getSymbol());
            LocalDateTime now = LocalDateTime.now();
            
            for (Order order : openOrders) {
                if ("SELL".equals(order.getSide())) {
                    long holdSeconds = Duration.between(order.getCreateTime(), now).getSeconds();
                    
                    if (holdSeconds > config.getMaxHoldSeconds()) {
                        // 超时，撤单并市价平仓
                        log.warn("Order expired, canceling and closing position: orderId={}, holdSeconds={}", 
                            order.getOrderId(), holdSeconds);
                        
                        apiService.cancelOrder(order.getSymbol(), order.getOrderId());
                        orderService.updateOrderStatus(order.getId(), Order.OrderStatus.EXPIRED);
                        
                        // 市价平仓
                        OrderResult marketSell = apiService.placeMarketOrder(
                            order.getSymbol(), 
                            "SELL", 
                            order.getQuantity()
                        );
                        
                        // 记录统计
                        // TODO: 计算实际盈亏并记录
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking expired orders", e);
        }
    }
}
```

### 2.6 参数自动调整逻辑（先写不接入）

**文件**: `src/main/java/com/zq/strategy/ParameterOptimizer.java`

```java
@Service
@Slf4j
public class ParameterOptimizer {
    @Resource
    private MongoTemplate mongoTemplate;
    
    public StrategyConfig suggestParameters(String symbol, LocalDate startDate, LocalDate endDate) {
        // 1. 分析价差分布，建议minProfitTick
        double suggestedMinProfitTick = analyzeSpreadAndSuggestProfit(symbol, startDate, endDate);
        
        // 2. 分析深度分布，建议minSupportRatio
        double suggestedMinSupportRatio = analyzeDepthAndSuggestSupport(symbol, startDate, endDate);
        
        // 3. 分析交易结果，建议maxHoldSeconds
        int suggestedMaxHoldSeconds = analyzeTradesAndSuggestHoldTime(symbol, startDate, endDate);
        
        log.info("Parameter suggestions for {}: minProfitTick={}, minSupportRatio={}, maxHoldSeconds={}", 
            symbol, suggestedMinProfitTick, suggestedMinSupportRatio, suggestedMaxHoldSeconds);
        
        // 返回建议配置（不自动应用）
        StrategyConfig suggested = new StrategyConfig();
        suggested.setMinProfitTick(suggestedMinProfitTick);
        suggested.setMinSupportRatio(suggestedMinSupportRatio);
        suggested.setMaxHoldSeconds(suggestedMaxHoldSeconds);
        
        return suggested;
    }
    
    private double analyzeSpreadAndSuggestProfit(String symbol, LocalDate start, LocalDate end) {
        Query query = query(
            where("symbol").is(symbol)
            .and("date").gte(start).lte(end)
        );
        
        List<SpreadStats> stats = mongoTemplate.find(query, SpreadStats.class);
        
        // 计算价差分布的P75（75%的价差都小于这个值）
        List<Double> spreads = stats.stream()
            .map(s -> s.getAskPrice() - s.getBidPrice())
            .sorted()
            .toList();
        
        if (spreads.isEmpty()) {
            return 0.0001;  // 默认值
        }
        
        int p75Index = (int) (spreads.size() * 0.75);
        double p75Spread = spreads.get(p75Index);
        
        // 建议的minProfitTick = P75价差的50%（保守）
        return Math.max(0.0001, p75Spread * 0.5);
    }
    
    private double analyzeDepthAndSuggestSupport(String symbol, LocalDate start, LocalDate end) {
        Query query = query(
            where("symbol").is(symbol)
            .and("date").gte(start).lte(end)
        );
        
        List<DepthStats> stats = mongoTemplate.find(query, DepthStats.class);
        
        // 找出count最多的supportRatioBucket
        Map<String, Integer> bucketCounts = stats.stream()
            .collect(Collectors.groupingBy(
                DepthStats::getSupportRatioBucket,
                Collectors.summingInt(DepthStats::getCount)
            ));
        
        String mostCommonBucket = bucketCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("0.6-0.7");
        
        // 从bucket中提取下界作为建议值
        double lowerBound = Double.parseDouble(mostCommonBucket.split("-")[0]);
        return lowerBound;
    }
    
    private int analyzeTradesAndSuggestHoldTime(String symbol, LocalDate start, LocalDate end) {
        Query query = query(
            where("symbol").is(symbol)
            .and("date").gte(start).lte(end)
        );
        
        List<TradeStats> stats = mongoTemplate.find(query, TradeStats.class);
        
        // 计算平均持仓时间
        double avgHoldSeconds = stats.stream()
            .mapToDouble(TradeStats::getAvgHoldSeconds)
            .average()
            .orElse(1800.0);
        
        // 建议值 = 平均持仓时间 * 1.5（留有余地）
        return (int) Math.min(3600, avgHoldSeconds * 1.5);
    }
}
```

---

## 三、开发顺序（TDD驱动）

1. ✅ **创建MongoDB初始化脚本** - 插入策略配置和API key
2. ✅ **创建Domain实体类** - Order, Position, Stats实体
3. 🔨 **BinanceApiService测试 + 实现** - 连接测试网，验证API可用性
4. 🔨 **OrderService测试 + 实现** - 订单CRUD，虚拟线程
5. 🔨 **PositionService测试 + 实现** - 持仓管理，资金计算
6. 🔨 **StatsService测试 + 实现** - 统计数据聚合
7. 🔨 **StrategyEngine完善** - 集成所有模块
8. 🔨 **集成测试** - 完整交易流程测试
9. 🔨 **ParameterOptimizer实现** - 参数优化逻辑（不接入）

---

## 四、验收标准

✅ MongoDB中已插入合理的策略配置（basePrice=1.0000, minProfitTick=0.0001等）
✅ 所有单元测试通过（TDD）
✅ 能成功连接测试网并查询余额
✅ 能根据策略配置自动下单
✅ 订单状态正确记录到MongoDB
✅ 统计表正确聚合数据（无重复明细）
✅ 参数优化逻辑能基于统计给出建议
✅ 集成测试：模拟行情 → 下单 → 平仓 → 记录统计

---

## 五、下一步行动

1. **立即执行**：创建MongoDB初始化脚本（`doc/db/init-mongo.js`）
2. **立即执行**：手动连接MongoDB，执行初始化脚本
3. **立即执行**：验证配置是否正确插入
4. 然后按TDD顺序逐步开发各模块

