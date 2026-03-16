# NOTIONAL 错误修复说明

## 修复日期
2026-01-18

## 问题描述

应用运行时出现币安API错误：
```
Filter failure: NOTIONAL
{"code":-1013,"msg":"Filter failure: NOTIONAL"}
```

**原因**：订单的名义价值（价格 × 数量）不满足币安的最小要求（minNotional）。

## 币安交易规则说明

币安对每个交易对都有多个过滤器（Filter）来限制订单参数：

1. **LOT_SIZE** - 数量限制
   - `minQty`: 最小下单数量
   - `maxQty`: 最大下单数量
   - `stepSize`: 数量步进（必须是stepSize的整数倍）

2. **PRICE_FILTER** - 价格限制
   - `minPrice`: 最小价格
   - `maxPrice`: 最大价格
   - `tickSize`: 价格步进

3. **NOTIONAL** - 订单金额限制
   - `minNotional`: 最小订单金额（价格 × 数量）
   - 对于USDC/USDT交易对，通常要求 minNotional = 5-10 USDT

## 解决方案

### 1. 添加 NOTIONAL 过滤器支持

**修改文件**: `src/main/java/com/zq/api/BinanceApiService.java`

#### 1.1 在 SymbolFilter 类中添加 minNotional 字段

```java
@lombok.Data
public static class SymbolFilter {
    private String symbol;
    
    // LOT_SIZE
    private double minQty;
    private double maxQty;
    private double stepSize;
    
    // PRICE_FILTER
    private double minPrice;
    private double maxPrice;
    private double tickSize;
    
    // NOTIONAL (新增)
    private double minNotional;
}
```

#### 1.2 解析 NOTIONAL 过滤器

在 `getSymbolFilter()` 方法中添加：

```java
} else if ("NOTIONAL".equals(filterType) || "MIN_NOTIONAL".equals(filterType)) {
    // NOTIONAL filter - 订单名义价值要求
    if (filterObj.has("minNotional")) {
        filter.setMinNotional(filterObj.get("minNotional").getAsDouble());
    }
}
```

#### 1.3 添加 adjustQuantityAndNotional() 方法

新增方法，同时验证 LOT_SIZE 和 NOTIONAL：

```java
/**
 * 根据交易规则调整数量，同时验证 NOTIONAL 要求
 * 
 * @param symbol 交易对
 * @param quantity 原始数量
 * @param price 订单价格
 * @return 调整后的数量
 */
public double adjustQuantityAndNotional(String symbol, double quantity, double price) 
        throws IOException, InterruptedException {
    SymbolFilter filter = getSymbolFilter(symbol);
    
    // 先按 LOT_SIZE 调整
    double adjusted = adjustQuantity(symbol, quantity);
    
    // 验证 NOTIONAL（订单名义价值）
    double minNotional = filter.getMinNotional();
    if (minNotional > 0) {
        double notional = adjusted * price;
        if (notional < minNotional) {
            // 订单金额不足，需要增加数量
            double requiredQty = minNotional / price;
            
            // 按 stepSize 向上舍入到最小满足金额
            double stepSize = filter.getStepSize();
            adjusted = Math.ceil(requiredQty / stepSize) * stepSize;
            
            // 处理精度
            int decimalPlaces = getDecimalPlaces(stepSize);
            adjusted = roundToDecimalPlaces(adjusted, decimalPlaces);
            
            log.info("Adjusted quantity to meet minNotional: {} -> {} (price={}, minNotional={}, notional={})", 
                quantity, adjusted, price, minNotional, adjusted * price);
        }
    }
    
    return adjusted;
}
```

### 2. 更新 StrategyEngine 使用新方法

**修改文件**: `src/main/java/com/zq/strategy/StrategyEngine.java`

在以下三个地方使用 `adjustQuantityAndNotional()` 替代 `adjustQuantity()`：

#### 2.1 买单下单

```java
private void executeBuy(StrategyConfig config, TickerData ticker, double orderAmount) {
    // ...
    double buyPrice = ticker.getBestBidPrice().doubleValue();
    double quantity = orderAmount / buyPrice;
    
    // 根据交易规则调整数量，同时验证 NOTIONAL 要求
    quantity = apiService.adjustQuantityAndNotional(symbol, quantity, buyPrice);
    
    var result = apiService.placeLimitOrder(symbol, "BUY", quantity, buyPrice);
    // ...
}
```

#### 2.2 卖单下单

```java
private void executeSell(StrategyConfig config, double quantity, double sellPrice, String relatedBuyOrderId) {
    // ...
    // 根据交易规则调整数量，同时验证 NOTIONAL 要求
    quantity = apiService.adjustQuantityAndNotional(symbol, quantity, sellPrice);
    
    var result = apiService.placeLimitOrder(symbol, "SELL", quantity, sellPrice);
    // ...
}
```

#### 2.3 超时市价平仓

```java
// 获取当前价格用于验证 NOTIONAL
double currentPrice = apiService.getCurrentPrice(order.getSymbol());

// 根据交易规则调整数量，同时验证 NOTIONAL 要求
double adjustedQty = apiService.adjustQuantityAndNotional(
    order.getSymbol(), order.getQuantity(), currentPrice);

// 市价平仓
var marketSell = apiService.placeMarketOrder(order.getSymbol(), "SELL", adjustedQty);
```

### 3. 调整配置参数

**目的**：确保单次买入金额在10-12U，远高于minNotional要求

执行数据库更新：
```bash
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").strategy_config.updateOne(
    {_id: "USDCUSDT_TESTNET"}, 
    {$set: {maxBuyAmountUsdt: 12.0}}
  )'
```

**更新结果**：
- 测试网：`maxBuyAmountUsdt` = 15U → 12U
- 生产环境：保持 15U（可选调整）

## 工作原理

### 调整流程

1. **首次请求时获取交易规则**
   - 调用币安 `/api/v3/exchangeInfo` API
   - 解析 LOT_SIZE、PRICE_FILTER、NOTIONAL 过滤器
   - 缓存规则，避免重复请求

2. **下单前自动调整**
   - 按 LOT_SIZE 的 stepSize 调整数量
   - 验证订单金额（quantity × price）是否满足 minNotional
   - 如果不满足，向上舍入数量直到满足要求

3. **处理精度问题**
   - 根据 stepSize 确定小数位数
   - 使用 `roundToDecimalPlaces()` 处理浮点精度

### 示例

假设 USDCUSDT 的规则：
- `stepSize` = 1.0（数量必须是整数）
- `minNotional` = 10.0（订单金额至少10 USDT）

**场景1：订单金额不足**
```
原始计算：
- orderAmount = 8 USDT
- buyPrice = 1.0001
- quantity = 8 / 1.0001 = 7.999 → 向下舍入为 7.0

验证 NOTIONAL：
- notional = 7.0 × 1.0001 = 7.0007 < 10.0 ❌

自动调整：
- requiredQty = 10.0 / 1.0001 = 9.999
- 向上舍入：Math.ceil(9.999 / 1.0) × 1.0 = 10.0 ✓
- 最终数量：10.0
- 最终金额：10.0 × 1.0001 = 10.001 > 10.0 ✓
```

**场景2：订单金额充足**
```
原始计算：
- orderAmount = 12 USDT
- buyPrice = 1.0001
- quantity = 12 / 1.0001 = 11.998 → 向下舍入为 11.0

验证 NOTIONAL：
- notional = 11.0 × 1.0001 = 11.0011 > 10.0 ✓

无需调整，直接下单
```

## 测试验证

### 1. 重新编译
```bash
cd /Users/bao/java/zhuanqian
./gradlew clean bootJar
```

### 2. 重启应用
```bash
cd run
./run.sh
```

### 3. 观察日志
```bash
tail -f logs/application.log
```

**期望看到的日志**：
```
Loaded symbol filter for USDCUSDT: minQty=1.0, stepSize=1.0, minNotional=10.0
Adjusted quantity to meet minNotional: 7.5 -> 10.0 (price=1.0001, minNotional=10.0, notional=10.001)
[USDCUSDT] BUY ORDER PLACED - orderId=12345, price=1.0001, qty=10.0, amount=10.00U
```

## 注意事项

### ⚠️ 重要提醒

1. **订单金额可能增加**
   - 为满足 minNotional，系统会自动增加数量
   - 实际下单金额可能略大于配置的 maxBuyAmountUsdt
   - 建议配置金额留有余量（如配置12U，实际可能用到13-14U）

2. **余额检查**
   - 确保账户有足够余额
   - 系统会自动调整数量，但不会检查余额是否充足
   - 余额不足会导致下单失败

3. **交易规则缓存**
   - 交易规则在应用启动后永久缓存
   - 币安调整规则后需要重启应用
   - 通常规则不会频繁变化

4. **多个过滤器同时生效**
   - LOT_SIZE、PRICE_FILTER、NOTIONAL 会同时验证
   - 必须同时满足所有过滤器要求
   - 调整顺序：先 LOT_SIZE，后 NOTIONAL

## 相关文档

- `LOT_SIZE_FIX.md` - LOT_SIZE 错误修复说明
- `IMPLEMENTATION_SUMMARY.md` - 完整实施总结
- 币安API文档：https://binance-docs.github.io/apidocs/spot/en/#filters

## 文件清单

### 修改的文件
1. ✅ `src/main/java/com/zq/api/BinanceApiService.java`
   - 添加 minNotional 字段
   - 添加 NOTIONAL 过滤器解析
   - 添加 `adjustQuantityAndNotional()` 方法

2. ✅ `src/main/java/com/zq/strategy/StrategyEngine.java`
   - 买单使用 `adjustQuantityAndNotional()`
   - 卖单使用 `adjustQuantityAndNotional()`
   - 超时平仓使用 `adjustQuantityAndNotional()`

### 新增的文件
1. ✅ `doc/db/update-buyamount.js` - 配置更新脚本
2. ✅ `NOTIONAL_FIX.md` - 本文档

## 总结

本次修复完成了对币安 NOTIONAL 过滤器的支持，确保所有订单都满足最小金额要求。

**核心改进**：
1. ✅ 支持 NOTIONAL 过滤器解析和验证
2. ✅ 自动调整数量满足 minNotional 要求
3. ✅ 同时处理 LOT_SIZE 和 NOTIONAL 限制
4. ✅ 调整配置参数，单次买入12U
5. ✅ 详细的日志记录和错误处理

**下一步**：
1. 重启应用验证修复效果
2. 监控日志确认订单成功
3. 观察实际交易金额是否符合预期

---

**问题反馈**：如有任何问题，请参考日志或联系开发团队。

