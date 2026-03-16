# 交易测试修复说明

## 问题总结

用户运行 `TradingOperationsTest` 时遇到以下问题：

1. **PRICE_FILTER 错误** - 下单价格精度不符合币安要求
2. **MAX_NUM_ORDERS 错误** - 历史挂单过多，超过币安限制
3. **行情没有打印** - 测试失败导致后续代码未执行
4. **想用 Java 直接运行** - 不想通过 JUnit 运行

## 已修复的问题

### 1. 价格精度问题（PRICE_FILTER）

**原因**：测试代码计算价格时（如 `currentPrice - 0.0010`）没有按照币安的 `tickSize` 规则调整精度。

**解决方案**：
- 在 `BinanceApiService` 中添加了 `adjustPrice()` 方法
- 自动根据交易规则调整价格精度
- 所有下单前都会调用该方法

```java
// 修改前
double buyPrice = currentPrice - 0.0010;
OrderResult order = apiService.placeLimitOrder(symbol, "BUY", 10.0, buyPrice);

// 修改后
double buyPrice = apiService.adjustPrice(symbol, currentPrice - 0.0010);
double buyQty = apiService.adjustQuantityAndNotional(symbol, 10.0, buyPrice);
OrderResult order = apiService.placeLimitOrder(symbol, "BUY", buyQty, buyPrice);
```

### 2. 挂单过多问题（MAX_NUM_ORDERS）

**原因**：历史测试留下很多未撤销的挂单，超过币安的200个限制。

**解决方案**：
- 在测试初始化时（`setup()` 方法）自动清理历史挂单
- 确保每次测试开始前挂单数量为0

```java
// 6. 清理历史挂单（避免 MAX_NUM_ORDERS 错误）
try {
    List<BinanceOrder> openOrders = apiService.getOpenOrders(testSymbol);
    if (!openOrders.isEmpty()) {
        log.info("发现 {} 个历史挂单，准备清理...", openOrders.size());
        // ... 清理逻辑
    }
} catch (Exception e) {
    log.warn("清理历史挂单失败: {}", e.getMessage());
}
```

### 3. 代码改进

**新增功能**：
- ✅ `BinanceApiService.adjustPrice()` - 调整价格精度
- ✅ 测试初始化时自动清理历史挂单
- ✅ 所有下单操作都调整价格和数量

**修改的文件**：
1. `src/main/java/com/zq/api/BinanceApiService.java`
   - 添加 `adjustPrice()` 方法

2. `src/test/java/com/zq/TradingOperationsTest.java`
   - `setup()` 方法：添加历史挂单清理
   - `test03_PlaceOrders()`: 调整价格和数量
   - `test04_QueryOpenOrders()`: 调整价格和数量
   - `test05_CancelOrder()`: 调整价格和数量
   - `test06_BatchCancelOrders()`: 调整价格和数量
   - `test07_CancelAllOrders()`: 调整价格和数量
   - `test09_CompleteTrading()`: 调整价格和数量

## 如何运行测试

### 方式1: 使用简单脚本（推荐✨）

```bash
# 运行单个测试（行情查询 - 不会失败）
./run-test-simple.sh

# 或者运行完整测试套件
./gradlew test --tests TradingOperationsTest
```

### 方式2: 使用 JUnit（原方式）

```bash
# 运行所有测试
./gradlew test --tests TradingOperationsTest

# 运行单个测试
./gradlew test --tests TradingOperationsTest.test01_MarketData
./gradlew test --tests TradingOperationsTest.test02_AccountBalance
./gradlew test --tests TradingOperationsTest.test03_PlaceOrders
# ... 等等
```

### 方式3: 使用 Java main 方法（交互式菜单）

```bash
# 方式3a: 使用直接运行脚本
./run-test-direct.sh

# 方式3b: 手动运行
./gradlew clean build -x test
java -cp "build/classes/java/main:build/classes/java/test:~/.gradle/caches/modules-2/files-2.1/*/*/*/*/*.jar" \
  com.zq.TradingOperationsTest
```

**注意**：方式3会启动交互式菜单，可以选择运行哪个测试。

## 测试清单

| 测试编号 | 测试名称 | 说明 | 是否会下单 |
|---------|---------|------|----------|
| 测试1 | test01_MarketData | 行情查询 | ❌ 否 |
| 测试2 | test02_AccountBalance | 账户余额 | ❌ 否 |
| 测试3 | test03_PlaceOrders | 下单操作 | ✅ 是 |
| 测试4 | test04_QueryOpenOrders | 挂单查询 | ✅ 是 |
| 测试5 | test05_CancelOrder | 撤单操作 | ✅ 是 |
| 测试6 | test06_BatchCancelOrders | 批量撤单 | ✅ 是 |
| 测试7 | test07_CancelAllOrders | 全部撤单 | ✅ 是 |
| 测试8 | test08_ConfigurationLoading | 配置读取 | ❌ 否 |
| 测试9 | test09_CompleteTrading | 综合测试 | ✅ 是 |
| 测试10 | test10_QueryPosition | 持仓查询 | ❌ 否 |
| 测试11 | test11_QueryHistoricalOrders | 历史订单 | ❌ 否 |
| 测试12 | test12_ProfitLossStatistics | 盈亏统计 | ❌ 否 |

**安全的测试**（不会下单，可以随便运行）：
- 测试1、2、8、10、11、12

**会下单的测试**（会创建真实订单）：
- 测试3、4、5、6、7、9

## 快速验证

如果只想验证修复是否有效，运行安全的测试：

```bash
# 1. 行情查询（验证API连接）
./gradlew test --tests TradingOperationsTest.test01_MarketData

# 2. 账户余额（验证认证）
./gradlew test --tests TradingOperationsTest.test02_AccountBalance

# 3. 配置读取（验证数据库配置）
./gradlew test --tests TradingOperationsTest.test08_ConfigurationLoading
```

如果这3个测试都通过，说明：
- ✅ API 连接正常
- ✅ 认证配置正确
- ✅ 数据库配置正确
- ✅ 可以进行下单测试

## 查看日志

```bash
# 实时查看应用日志
tail -f logs/application.log

# 查看交易日志
tail -f logs/trade.log

# 查看测试输出（在终端直接显示）
```

## 故障排查

### 问题1: 仍然出现 PRICE_FILTER 错误

**原因**：可能币安的交易规则发生变化

**解决方案**：
```bash
# 清空缓存并重新获取交易规则
./gradlew clean build -x test
```

### 问题2: 仍然出现 MAX_NUM_ORDERS 错误

**原因**：历史挂单太多

**解决方案**：
```bash
# 手动清理所有挂单
./gradlew test --tests TradingOperationsTest.test07_CancelAllOrders
```

### 问题3: 找不到配置

**原因**：数据库配置不存在

**解决方案**：
```bash
# 初始化数据库配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin \
  < doc/db/init-strategy-config.js
```

### 问题4: API认证失败

**原因**：API Key 配置错误

**解决方案**：
1. 检查数据库中的 API 配置
2. 确保使用正确的测试网或生产网配置
3. 验证 API Key 和 Secret Key 没有过期

## 总结

修复后的测试系统：
- ✅ 自动调整价格和数量精度
- ✅ 自动清理历史挂单
- ✅ 支持多种运行方式
- ✅ 提供详细的日志输出
- ✅ 包含安全和危险测试的区分

**推荐运行顺序**：
1. 先运行安全测试（1、2、8）验证基础配置
2. 运行持仓和订单查询（10、11、12）了解当前状态
3. 谨慎运行下单测试（3-7、9）

**注意事项**：
- ⚠️ 测试会创建真实订单（虽然价格设置为不会成交）
- ⚠️ 测试结束时会自动清理创建的订单
- ⚠️ 建议使用测试网环境进行测试
- ⚠️ 确保账户有足够余额（至少10-20 USDT）

