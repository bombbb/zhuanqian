# 策略参数重构总结

> 重构日期：2026-01-18

## 重构目标

简化和优化交易策略参数，使逻辑更清晰易懂：

1. **删除 basePrice**（废弃字段，已无用）
2. **referencePrice 保留**（仅作为锚定价参考，如 1.0）
3. **新增 maxBuyPrice**（真正起作用的最高买入价）
4. **简化利润逻辑**（只要 >= minProfitTick 即可）

## 核心变化

### 1. 参数定义变化

#### 旧逻辑
```
basePrice: 1.0005          // 已废弃，混淆
referencePrice: 1.0        // 既是参考又是阈值
tradeAmountUsdt: 15.0      // 已废弃
```

#### 新逻辑
```
referencePrice: 1.0        // 纯参考价格（锚定价），用于计算偏离度
maxBuyPrice: 1.0005        // 真正的买入价格上限
maxBuyAmountUsdt: 15.0     // 单次最大买入金额
```

### 2. 买入条件变化

#### 旧逻辑
```java
// 条件1: 价格低于 referencePrice
if (lastPrice >= referencePrice) return false;

// 条件2: 复杂的利润空间计算
double expectedSellPrice = referencePrice + minProfitTick;
double profitSpace = expectedSellPrice - ask;
if (profitSpace < minProfitTick) return false;
```

#### 新逻辑
```java
// 条件1: 价格低于 maxBuyPrice
if (lastPrice >= maxBuyPrice) return false;

// 条件2: 简化的利润检查
if (minProfitTick < 0.0001) return false;
```

### 3. 动态调价变化

#### 旧逻辑
- 动态调整 `referencePrice`
- referencePrice 既是参考又是阈值，容易混淆

#### 新逻辑
- 动态调整 `maxBuyPrice`
- referencePrice 保持为 1.0（锚定价），不参与下单判断
- maxBuyPrice 才是真正的买入阈值

## 修改文件清单

### Java 代码
1. ✅ `src/main/java/com/zq/strategy/StrategyConfig.java`
   - 删除 `basePrice` 和 `tradeAmountUsdt`
   - 新增 `maxBuyPrice` 字段
   - 调整注释说明

2. ✅ `src/main/java/com/zq/strategy/StrategyEngine.java`
   - 修改 `shouldBuy()` 方法，使用 `maxBuyPrice` 判断
   - 简化利润条件逻辑
   - 删除所有兼容旧配置的代码

3. ✅ `src/main/java/com/zq/api/MarketDataHandler.java`
   - 修改 `analyzeTradeConditions()` 方法
   - 使用 `maxBuyPrice` 进行判断
   - 显示"距上限"而非"偏离"

4. ✅ `src/main/java/com/zq/tools/PriceChecker.java`
   - 完全重写，移除兼容代码
   - 显示 `maxBuyPrice` 和 `referencePrice`
   - 更新配置建议

5. ✅ `src/main/java/com/zq/strategy/DynamicPriceAdjuster.java`
   - 修改 `calculateNewReferencePrice()` 为 `calculateNewMaxBuyPrice()`
   - 修改 `updateReferencePrice()` 为 `updateMaxBuyPrice()`
   - 调整所有相关逻辑

### 数据库脚本
6. ✅ `doc/db/init-strategy-config.js`
   - 删除 `basePrice` 和 `tradeAmountUsdt`
   - 新增 `maxBuyPrice: 1.0005`
   - 更新注释说明

7. ✅ `doc/db/update-trend-config.js`
   - 保持趋势分析配置不变
   - 简化脚本内容

### 文档
8. ✅ `STRATEGY_GUIDE.md`
   - 全面更新所有 `referencePrice` 相关说明
   - 改为 `maxBuyPrice` 说明
   - 更新示例配置
   - 更新日志示例

## 配置示例

### 测试网配置
```javascript
{
  _id: "USDCUSDT_TESTNET",
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  
  // 核心参数
  referencePrice: 1.0,           // 参考价格（锚定价）
  maxBuyPrice: 1.0005,           // 最高买入价
  maxBuyAmountUsdt: 15.0,        // 单次最大买入金额
  minProfitTick: 0.0001,         // 最小利润 0.01%
  maxHoldSeconds: 1800,          // 最大持仓30分钟
  maxTotalInvestUsdt: 500.0,     // 最大总投入
  minSupportRatio: 0.6,          // 最小支撑比率
  
  // 趋势分析
  enableDynamicPriceAdjustment: true,
  trendAnalysisIntervalSeconds: 3600,
  trendAnalysisLookbackDays: 7,
  minConfidenceForAdjustment: 0.6
}
```

### 正式网配置
```javascript
{
  _id: "USDCUSDT_PRODUCTION",
  symbol: "USDCUSDT",
  mode: "PRODUCTION",
  enabled: false,
  
  // 核心参数（更大的金额）
  referencePrice: 1.0,
  maxBuyPrice: 1.0005,
  maxBuyAmountUsdt: 50.0,
  minProfitTick: 0.0001,
  maxHoldSeconds: 1800,
  maxTotalInvestUsdt: 1000.0,
  minSupportRatio: 0.6,
  
  // 趋势分析
  enableDynamicPriceAdjustment: true,
  trendAnalysisIntervalSeconds: 3600,
  trendAnalysisLookbackDays: 7,
  minConfidenceForAdjustment: 0.7
}
```

## 升级步骤

### 1. 备份数据库
```bash
docker exec trading-mongodb mongodump \
  --authenticationDatabase admin \
  -u admin -p admin123 \
  --db strategy_db \
  --out /backup
```

### 2. 更新数据库配置
```bash
# 重新初始化配置（会删除旧配置）
docker exec -i trading-mongodb mongosh -u admin -p admin123 \
  --authenticationDatabase admin strategy_db < doc/db/init-strategy-config.js

# 添加趋势分析配置
docker exec -i trading-mongodb mongosh -u admin -p admin123 \
  --authenticationDatabase admin strategy_db < doc/db/update-trend-config.js
```

### 3. 验证配置
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.findOne({_id: 'USDCUSDT_TESTNET'})"
```

应该看到：
- ✅ `referencePrice: 1`
- ✅ `maxBuyPrice: 1.0005`
- ✅ `maxBuyAmountUsdt: 15`
- ❌ 没有 `basePrice`
- ❌ 没有 `tradeAmountUsdt`

### 4. 重新编译和启动
```bash
# 编译
./gradlew clean build

# 启动
./gradlew bootRun
```

## 预期效果

### 日志变化

#### 旧日志
```
[USDCUSDT] | ✗ 暂不下单: 价格过高(1.000200>=1.000000) 利润不足(-0.000100<0.000100)
```

#### 新日志
```
[USDCUSDT] | ✗ 暂不下单: 价格过高(1.000200>=1.000500)
[USDCUSDT] | ✓ 可下单 (距上限: 0.0300%)
```

### 下单逻辑

#### 场景1：价格 1.000200
- referencePrice = 1.0（参考）
- maxBuyPrice = 1.0005（阈值）
- 判断：1.000200 < 1.0005 ✅ **可以买入**

#### 场景2：价格 1.000600
- referencePrice = 1.0（参考）
- maxBuyPrice = 1.0005（阈值）
- 判断：1.000600 >= 1.0005 ❌ **不买入**

## 常见问题

### Q1: 为什么保留 referencePrice？
A: 用于计算价格偏离度，在动态买入金额计算中使用。保持为 1.0 作为 USDC 的锚定价。

### Q2: maxBuyPrice 应该设置多少？
A: 
- 保守：1.0003（只在价格较低时买入）
- 中等：1.0005（推荐，平衡频率和利润）
- 积极：1.001（增加交易频率）

### Q3: 如何调整交易频率？
A: 提高 `maxBuyPrice` 即可。例如从 1.0005 改为 1.001。

### Q4: 动态调价会调整哪个参数？
A: 现在只调整 `maxBuyPrice`，`referencePrice` 保持为 1.0 不变。

## 测试建议

1. **价格检查工具测试**
```bash
./gradlew bootRun --args='--spring.profiles.active=test'
```

2. **观察日志**
- 查看是否正确显示 `maxBuyPrice`
- 确认下单条件判断正确
- 验证动态调价是否调整 `maxBuyPrice`

3. **模拟交易**
- 在测试网运行一段时间
- 观察交易频率是否符合预期
- 检查利润是否达到 minProfitTick

## 回滚方案

如果需要回滚：

1. 恢复数据库备份
2. 切换到之前的代码版本
3. 重新启动应用

```bash
# 恢复数据库
docker exec trading-mongodb mongorestore \
  --authenticationDatabase admin \
  -u admin -p admin123 \
  --db strategy_db \
  /backup/strategy_db
```

## 总结

这次重构：
- ✅ 删除了混淆的 `basePrice`
- ✅ 明确了 `referencePrice` 的作用（仅参考）
- ✅ 新增了清晰的 `maxBuyPrice`（真正阈值）
- ✅ 简化了利润判断逻辑
- ✅ 更新了所有相关文档

逻辑更清晰，更易于理解和维护！

