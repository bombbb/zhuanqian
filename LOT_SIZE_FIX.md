# LOT_SIZE 错误修复总结

## 问题描述
应用在下单时出现 Binance API 错误：
```
Filter failure: LOT_SIZE
```

**原因**：下单的数量（quantity）不符合币安交易对的 LOT_SIZE 规则。

LOT_SIZE 规则要求：
1. `quantity >= minQty`（最小数量）
2. `quantity <= maxQty`（最大数量）
3. `quantity` 必须是 `stepSize` 的倍数

例如：计算出的数量是 `4.16`，但如果 stepSize 是 `1.0`，则只能下 `4.0` 或 `5.0`。

---

## 解决方案

### 1. 在 `BinanceApiService.java` 中添加交易规则支持

**添加的内容**：

#### 1.1 新增 API 端点
```java
private static final String EXCHANGE_INFO_ENDPOINT = "/api/v3/exchangeInfo";
```

#### 1.2 添加交易规则缓存
```java
// 交易规则缓存（symbol -> SymbolFilter）
private final Map<String, SymbolFilter> symbolFilters = new java.util.concurrent.ConcurrentHashMap<>();
```

#### 1.3 添加 SymbolFilter 类
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
}
```

#### 1.4 添加获取交易规则的方法
```java
public SymbolFilter getSymbolFilter(String symbol) throws IOException, InterruptedException
```
- 从币安 API 获取交易对的规则
- 解析 LOT_SIZE 和 PRICE_FILTER
- 缓存结果，避免重复请求

#### 1.5 添加数量调整方法
```java
public double adjustQuantity(String symbol, double quantity) throws IOException, InterruptedException
```
- 检查是否小于 `minQty`，如果是则使用 `minQty`
- 检查是否大于 `maxQty`，如果是则使用 `maxQty`
- 按 `stepSize` 向下舍入：`Math.floor(quantity / stepSize) * stepSize`
- 处理精度问题，保留足够的小数位

**舍入逻辑示例**：
- 如果 stepSize = 1.0，quantity = 4.16 → 调整为 4.0
- 如果 stepSize = 0.1，quantity = 4.16 → 调整为 4.1
- 如果 stepSize = 0.01，quantity = 4.165 → 调整为 4.16

---

### 2. 在 `StrategyEngine.java` 中调用数量调整

#### 2.1 买入订单
在 `executeBuy()` 方法中，计算数量后立即调整：
```java
double quantity = orderAmount / buyPrice;

// 根据交易规则调整数量
quantity = apiService.adjustQuantity(symbol, quantity);

// 下限价买单
var result = apiService.placeLimitOrder(symbol, "BUY", quantity, buyPrice);
```

#### 2.2 卖出订单
在 `executeSell()` 方法中，下单前调整数量：
```java
// 根据交易规则调整数量
quantity = apiService.adjustQuantity(symbol, quantity);

// 下限价卖单
var result = apiService.placeLimitOrder(symbol, "SELL", quantity, sellPrice);
```

#### 2.3 市价平仓
在 `checkAndCancelExpiredOrders()` 方法中，市价平仓前调整数量：
```java
// 根据交易规则调整数量
double adjustedQty = apiService.adjustQuantity(order.getSymbol(), order.getQuantity());

// 市价平仓
var marketSell = apiService.placeMarketOrder(order.getSymbol(), "SELL", adjustedQty);
```

---

## 修改的文件

1. ✅ `src/main/java/com/zq/api/BinanceApiService.java`
   - 添加 `EXCHANGE_INFO_ENDPOINT`
   - 添加 `symbolFilters` 缓存
   - 添加 `SymbolFilter` 内部类
   - 添加 `getSymbolFilter()` 方法
   - 添加 `adjustQuantity()` 方法
   - 添加辅助方法：`getDecimalPlaces()`, `roundToDecimalPlaces()`

2. ✅ `src/main/java/com/zq/strategy/StrategyEngine.java`
   - 在 `executeBuy()` 中调用 `adjustQuantity()`
   - 在 `executeSell()` 中调用 `adjustQuantity()`
   - 在 `checkAndCancelExpiredOrders()` 中调用 `adjustQuantity()`

---

## 工作原理

### 首次调用
1. 调用 `adjustQuantity("USDCUSDT", 4.16)`
2. 检查缓存，没有找到
3. 调用 Binance API：`GET /api/v3/exchangeInfo?symbol=USDCUSDT`
4. 解析响应，提取 LOT_SIZE 规则（例如：minQty=1.0, stepSize=1.0）
5. 缓存规则到 `symbolFilters`
6. 根据规则调整数量：4.16 → 4.0
7. 返回调整后的数量

### 后续调用
1. 调用 `adjustQuantity("USDCUSDT", 5.89)`
2. 从缓存中直接获取规则
3. 根据规则调整数量：5.89 → 5.0
4. 返回调整后的数量

---

## 测试建议

### 1. 单元测试
可以创建测试类验证 `adjustQuantity()` 方法：
```java
@Test
public void testAdjustQuantity() throws Exception {
    BinanceApiService api = new BinanceApiService(...);
    
    // 测试不同的数量
    double adjusted1 = api.adjustQuantity("USDCUSDT", 4.16);
    double adjusted2 = api.adjustQuantity("USDCUSDT", 0.5);
    double adjusted3 = api.adjustQuantity("USDCUSDT", 10000.0);
    
    // 验证结果
    // adjusted1 应该是 stepSize 的倍数
    // adjusted2 应该 >= minQty
    // adjusted3 应该 <= maxQty
}
```

### 2. 集成测试
启动应用，观察日志：
```bash
cd run && ./run.sh
```

观察日志中的调整信息：
```
Loaded symbol filter for USDCUSDT: minQty=1.0, stepSize=1.0
Adjusted quantity: 4.16 -> 4.0 (stepSize=1.0)
```

### 3. 真实下单测试
等待买入信号触发，检查是否还有 LOT_SIZE 错误：
- ✅ 如果没有错误，说明修复成功
- ❌ 如果还有错误，检查日志中的调整后数量

---

## 注意事项

### ⚠️ 数量调整可能影响交易金额

由于数量会向下舍入，实际交易金额可能小于预期：

**示例**：
- 计划买入金额：4.16 USDT（quantity = 4.16 USDC）
- 调整后数量：4.0 USDC
- 实际买入金额：4.0 USDT（比预期少 0.16 USDT）

这是正常的，因为必须符合交易规则。

### ⚠️ 最小数量限制

如果计算的数量太小（小于 minQty），会自动调整为 minQty：

**示例**：
- 计算数量：0.5 USDC
- minQty = 1.0 USDC
- 调整后数量：1.0 USDC（可能超出预期金额）

建议在策略配置中设置合理的 `maxBuyAmountUsdt`，确保计算出的数量总是 >= minQty。

### ⚠️ 缓存刷新

交易规则缓存是永久的（直到应用重启）。如果币安更改了交易规则，需要重启应用以获取最新规则。

如果需要手动刷新缓存，可以添加清空缓存的方法：
```java
public void clearSymbolFilterCache(String symbol) {
    symbolFilters.remove(symbol);
}
```

---

## USDCUSDT 交易规则参考

**注意**：以下是参考值，实际规则请以币安 API 返回为准。

### 主网（Production）
- **LOT_SIZE**
  - minQty: 1.0
  - maxQty: 9000000.0
  - stepSize: 1.0
- **PRICE_FILTER**
  - minPrice: 0.0001
  - maxPrice: 10.0000
  - tickSize: 0.0001

### 测试网（Testnet）
- **LOT_SIZE**
  - minQty: 1.0
  - maxQty: 90000.0
  - stepSize: 1.0
- **PRICE_FILTER**
  - minPrice: 0.0001
  - maxPrice: 10.0000
  - tickSize: 0.0001

**结论**：对于 USDCUSDT，数量必须是整数（1, 2, 3...），不能有小数。

---

## 验证清单

- [x] 添加 `EXCHANGE_INFO_ENDPOINT`
- [x] 添加 `symbolFilters` 缓存
- [x] 添加 `SymbolFilter` 类
- [x] 添加 `getSymbolFilter()` 方法
- [x] 添加 `adjustQuantity()` 方法
- [x] 在 `executeBuy()` 中调用 `adjustQuantity()`
- [x] 在 `executeSell()` 中调用 `adjustQuantity()`
- [x] 在 `checkAndCancelExpiredOrders()` 中调用 `adjustQuantity()`
- [ ] 编译验证（需要Java环境）
- [ ] 启动应用测试
- [ ] 观察日志确认调整正常
- [ ] 等待真实下单验证

---

## 总结

**问题根源**：下单数量 4.16 不符合 USDCUSDT 的 LOT_SIZE 规则（stepSize=1.0，必须是整数）

**解决方案**：
1. 从币安 API 获取交易规则
2. 在下单前自动调整数量，确保符合规则
3. 对所有下单场景（限价买、限价卖、市价卖）都应用调整

**优点**：
- ✅ 自动适配不同交易对的规则
- ✅ 缓存规则，避免频繁API调用
- ✅ 覆盖所有下单场景
- ✅ 详细的日志输出，便于调试

**下一步**：
1. 重新构建应用：`./gradlew clean bootJar`
2. 启动应用：`cd run && ./run.sh`
3. 观察日志，验证修复效果

---

**修复完成时间**：2026-01-18
**修复人员**：AI Assistant

