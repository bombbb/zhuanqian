# WebSocket 启动问题修复说明

## 问题描述

您遇到了三个问题：

### 1. basePrice 显示为 1.0 而不是 1.0005
**原因**：日志使用了 `toString()` 默认格式化，导致 double 类型的精度显示不完整。

**实际情况**：
- 数据库中的值：`basePrice: 1.0005` ✅
- 日志显示：`basePrice=1.0` ❌（精度丢失）

### 2. 类型是否正确
**确认**：所有价格字段都是 `double` 类型，这是正确的。

```java
private double minProfitTick;
private double basePrice;
private double tradeAmountUsdt;
// ... 等等
```

### 3. 没有每隔5秒打印价格，也没有下单
**根本原因**：WebSocket 连接没有启动！

应用启动后只是初始化了配置，但 `BinanceWebSocketConfig.createWebSocketConnection()` 方法从未被调用。

## 修复内容

### 1. 自动启动 WebSocket 连接

**文件**：`src/main/java/com/zq/config/BinanceWebSocketConfig.java`

**修改**：
- 添加了 `@Autowired` 注入 `StrategyService` 和 `MarketDataHandler`
- 在 `@PostConstruct` 方法中自动启动 WebSocket 连接
- 延迟 2 秒启动，确保所有 Bean 都已初始化
- 检查配置是否启用（`enabled=true`）

```java
@PostConstruct
public void startWebSocket() {
    log.info("BinanceWebSocketConfig initialized");
    
    // 延迟启动WebSocket，确保所有Bean都已初始化
    new Thread(() -> {
        try {
            Thread.sleep(2000); // 等待2秒
            
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
```

### 2. 改进日志输出精度

**文件**：`src/main/java/com/zq/strategy/StrategyService.java`

**修改**：
- 使用 `String.format()` 格式化 double 值
- 价格字段显示 6 位小数（如 `1.000500`）
- 金额字段显示 2 位小数（如 `15.00`）

**新的日志输出示例**：
```
reload from DB = StrategyConfig(id=USDCUSDT_TESTNET, symbol=USDCUSDT, mode=TESTNET, 
  enabled=true, basePrice=1.000500, minProfitTick=0.000100, maxHoldSeconds=1800, 
  tradeAmountUsdt=15.00, maxTotalInvestUsdt=500.00, minSupportRatio=0.60, 
  supportRangeNear=0.002500, supportRangeMid=0.005000, supportRangeFar=0.010000, 
  priceLogIntervalSeconds=5)
```

## 验证修复

### 1. 重新启动应用

```bash
cd /Users/bao/java/zhuanqian
./gradlew bootRun
```

### 2. 观察启动日志

应该看到以下日志：

```
2026-01-18 XX:XX:XX [main] INFO  c.zq.config.BinanceWebSocketConfig - BinanceWebSocketConfig initialized
2026-01-18 XX:XX:XX [main] INFO  com.zq.strategy.StrategyService - reload from DB = StrategyConfig(id=USDCUSDT_TESTNET, symbol=USDCUSDT, mode=TESTNET, enabled=true, basePrice=1.000500, ...)
2026-01-18 XX:XX:XX [WebSocket-Starter] INFO  c.zq.config.BinanceWebSocketConfig - Starting WebSocket connection for enabled strategy: USDCUSDT_TESTNET
2026-01-18 XX:XX:XX [WebSocket-Starter] INFO  c.zq.config.BinanceWebSocketConfig - Using default WebSocket URL: wss://stream.binance.com:9443
2026-01-18 XX:XX:XX [WebSocket-Starter] INFO  c.zq.config.BinanceWebSocketConfig - Connecting to Binance WebSocket: wss://stream.binance.com:9443/ws/usdcusdt@ticker
2026-01-18 XX:XX:XX [WebSocket-Starter] INFO  c.zq.api.BinanceWebSocketClient - WebSocket connection established successfully: usdcusdt@ticker
```

### 3. 观察价格日志（每5秒一次）

```bash
tail -f logs/application.log | grep "Price update"
```

应该看到：

```
2026-01-18 XX:XX:XX [WebSocketWorker] INFO  com.zq.api.MarketDataHandler - [USDCUSDT] Price update - Bid: 1.000100, Ask: 1.000200, Last: 1.000150 | Total messages: 123
```

### 4. 检查是否下单

如果价格满足条件（`lastPrice <= basePrice` 且利润空间足够），应该看到下单日志：

```bash
tail -f logs/trade.log
```

## 下单条件检查

使用价格检查工具分析为什么没有下单：

```bash
./check-price.sh
```

输出示例：

```
========================================
正式网价格信息:
  Symbol: USDCUSDT
  Bid (买价): 1.000100
  Ask (卖价): 1.000200
  Mid (中间价): 1.000150
  Spread (价差): 0.000100
========================================
当前配置:
  basePrice: 1.0005
  minProfitTick: 0.0001
  lastPrice: 1.000150
========================================
下单条件分析:
  1. 价格条件 (lastPrice <= basePrice): true (lastPrice=1.000150, basePrice=1.0005)
  2. 利润空间条件 (profitSpace >= minProfitTick): true (profitSpace=0.000500, minProfitTick=0.0001)
  3. 预期卖出价: 1.0006
========================================
```

## 常见问题

### Q1: 为什么还是没有打印价格？
**A**: 检查以下几点：
1. 确认应用已重启
2. 确认配置 `enabled=true`
3. 检查 WebSocket 连接是否成功（查看启动日志）
4. 检查网络连接是否正常

### Q2: 为什么有价格但不下单？
**A**: 使用 `./check-price.sh` 检查下单条件：
- 价格条件：`lastPrice <= basePrice`
- 利润空间：`profitSpace >= minProfitTick`
- 资金充足：`availableFunds >= orderAmount`

### Q3: 如何调整 basePrice？
**A**: 有两种方法：

方法1：使用脚本自动更新
```bash
./check-price.sh update
```

方法2：手动更新数据库
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
db.strategy_config.updateOne(
  { _id: 'USDCUSDT_TESTNET' },
  { \$set: { basePrice: 1.0002 } }
)"
```

更新后需要重启应用或清理缓存。

### Q4: 如何查看 WebSocket 连接状态？
**A**: 查看应用日志：
```bash
tail -f logs/application.log | grep -E "WebSocket|connection"
```

## 技术细节

### WebSocket 启动流程

1. Spring Boot 启动，初始化所有 Bean
2. `BinanceWebSocketConfig` 的 `@PostConstruct` 方法被调用
3. 延迟 2 秒后，在新线程中启动 WebSocket
4. 从数据库加载配置（`enabled=true`）
5. 创建 WebSocket 连接到 `wss://stream.binance.com:9443/ws/usdcusdt@ticker`
6. 连接成功后，开始接收行情数据
7. `MarketDataHandler` 处理行情数据
8. 每 5 秒打印一次价格（可配置）
9. 满足条件时，`StrategyEngine` 执行下单

### 价格日志配置

默认间隔：5 秒（在 `StrategyConfig` 中定义）

```java
private long priceLogIntervalSeconds = 5;
```

可以通过数据库修改：

```javascript
db.strategy_config.updateOne(
  { _id: 'USDCUSDT_TESTNET' },
  { $set: { priceLogIntervalSeconds: 10 } }  // 改为10秒
)
```

## 总结

修复后，应用将：

1. ✅ 自动启动 WebSocket 连接
2. ✅ 每 5 秒打印一次价格（可配置）
3. ✅ 日志显示完整的 basePrice 精度（如 1.000500）
4. ✅ 满足条件时自动下单

如果仍有问题，请检查：
- 数据库配置是否正确（`enabled=true`）
- 网络连接是否正常
- 应用日志中是否有错误信息

