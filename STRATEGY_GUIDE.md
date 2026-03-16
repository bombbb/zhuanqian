# USDC稳定币套利交易策略指南

> 最后更新：2026-01-18

## 目录

- [概述](#概述)
- [核心策略](#核心策略)
- [动态价格调整](#动态价格调整)
- [配置参数说明](#配置参数说明)
- [快速开始](#快速开始)
- [监控和调优](#监控和调优)
- [故障排查](#故障排查)

---

## 概述

本系统是一个基于深度分析和均值回归的USDC/USDT稳定币套利交易系统，主要特点：

- ✅ **智能趋势分析**：基于历史价格和深度数据预测走势
- ✅ **动态价格调整**：根据市场趋势自动调整买入价格阈值
- ✅ **动态买入金额**：价格偏离越大，买入金额越大
- ✅ **自动参数优化**：根据趋势调整利润要求、支撑比率等参数
- ✅ **实时监控**：每5秒显示行情和下单判断
- ✅ **风险控制**：多层次风险控制机制

---

## 核心策略

### 1. 买入条件

系统会在同时满足以下条件时执行买入：

```
✓ 条件1: lastPrice < maxBuyPrice
   说明：价格低于最高买入价，出现买入机会

✓ 条件2: supportRatio >= minSupportRatio
   说明：有足够的买盘支撑，不会继续大跌

✓ 条件3: minProfitTick >= 0.0001
   说明：利润要求合理（>= 0.01%）

✓ 条件4: availableFunds >= calculatedAmount
   说明：有足够的可用资金
```

### 2. 动态买入金额

系统不再使用固定金额，而是根据价格偏离程度动态计算：

```java
// 计算价格偏离程度
deviation = (referencePrice - lastPrice) / referencePrice

// 计算买入比例（偏离0.1%时满额买入）
buyRatio = min(deviation / 0.001, 1.0)
buyRatio = max(buyRatio, 0.3)  // 最小也要买入30%

// 计算买入金额
amount = maxBuyAmountUsdt * buyRatio * random(0.9~1.1)
```

**示例（maxBuyAmountUsdt=50）：**

| 当前价格 | 偏离程度 | 买入金额 |
|----------|----------|----------|
| 0.99990 | 0.01% | 45-55 USDT |
| 0.99995 | 0.005% | 22.5-27.5 USDT |
| 0.99998 | 0.002% | 13.5-16.5 USDT |

### 3. 卖出策略

- 买入成交后，立即挂出限价卖单（价格 = 买入价 + minProfitTick）
- 如果超过 maxHoldSeconds 未成交，自动撤单并市价平仓

---

## 动态价格调整

这是系统的核心优化功能，能够根据市场趋势自动调整策略参数。

### 工作原理

```
每隔 trendAnalysisIntervalSeconds（默认1小时）：
  1. 分析最近N天的价格统计数据
  2. 分析深度数据和支撑比率
  3. 计算价格趋势和置信度
  4. 根据趋势调整 maxBuyPrice（最高买入价）
  5. 同步调整其他相关参数
```

### 趋势判断

系统会判断三种趋势：

**1. 上涨趋势 (RISING)**
- 近3天均价 > 近7天均价，且差距 > 0.02%
- 调整策略：提高 maxBuyPrice（不那么保守，价格稍高也可以买）
- 同时提高 minProfitTick（追求更高利润）

**2. 下跌趋势 (FALLING)**
- 近3天均价 < 近7天均价，且差距 > 0.02%
- 调整策略：降低 maxBuyPrice（更保守，等更低价格才买）
- 同时降低 minProfitTick（降低利润要求，增加成交机会）
- 支撑比率低时进一步降低

**3. 震荡趋势 (CONSOLIDATING)**
- 近期价格变化小于 0.02%
- 调整策略：保持或微调 maxBuyPrice 至当前市场价附近

### 调整幅度

- 最大调整幅度：0.05% * 置信度
- 只有置信度 >= minConfidenceForAdjustment 时才执行调整
- 调整受当前市场价限制（最多偏离市场价 ±0.1%）

### 相关参数同步调整

| 参数 | 上涨趋势 | 下跌趋势 | 震荡趋势 |
|------|----------|----------|----------|
| maxBuyPrice | ↑ 提高 | ↓ 降低 | → 微调 |
| minProfitTick | ↑ 提高10% | ↓ 降低10% | → 不变 |
| minSupportRatio | → 根据历史平均调整 | → 根据历史平均调整 | → 根据历史平均调整 |
| maxHoldSeconds | → 波动大时缩短20% | → 波动大时缩短20% | → 波动大时缩短20% |

---

## 配置参数说明

### 核心交易参数

| 参数 | 类型 | 说明 | 推荐值（测试网） | 推荐值（正式网） |
|------|------|------|------------------|------------------|
| **referencePrice** | double | 参考价格（锚定价，仅作参考） | 1.0 | 1.0 |
| **maxBuyPrice** | double | 最高买入价（真正的买入阈值） | 1.0005 | 1.0005 |
| **maxBuyAmountUsdt** | double | 单次最大买入金额（USDT） | 15.0 | 50.0 |
| **minProfitTick** | double | 最小利润空间（价格差） | 0.0001 | 0.0001 |
| **maxHoldSeconds** | int | 最大持仓时间（秒） | 1800 | 1800 |
| **maxTotalInvestUsdt** | double | 最大总投入金额限制 | 500.0 | 1000.0 |

### 深度分析参数

| 参数 | 类型 | 说明 | 推荐值 |
|------|------|------|--------|
| **minSupportRatio** | double | 最小支撑比率（0-1） | 0.6 |
| **supportRangeNear** | double | 近端支撑范围 | 0.0025 |
| **supportRangeMid** | double | 中端支撑范围 | 0.005 |
| **supportRangeFar** | double | 远端支撑范围 | 0.01 |

### 趋势分析参数（新增）

| 参数 | 类型 | 说明 | 推荐值 |
|------|------|------|--------|
| **enableDynamicPriceAdjustment** | boolean | 是否启用动态价格调整 | true |
| **trendAnalysisIntervalSeconds** | long | 趋势分析间隔（秒） | 3600 |
| **trendAnalysisLookbackDays** | int | 趋势分析回看天数 | 7 |
| **minConfidenceForAdjustment** | double | 动态调价的最小置信度 | 0.6 |

### 日志配置

| 参数 | 类型 | 说明 | 推荐值 |
|------|------|------|--------|
| **priceLogIntervalSeconds** | long | 价格日志打印间隔（秒） | 5 |

---

## 快速开始

### 1. 启动MongoDB

```bash
cd run
docker-compose up -d
```

### 2. 初始化数据库配置

```bash
# 基础配置
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

应该看到新增的字段：
```javascript
{
  referencePrice: 1,
  maxBuyPrice: 1.0005,
  maxBuyAmountUsdt: 15,
  enableDynamicPriceAdjustment: true,
  trendAnalysisIntervalSeconds: 3600,
  trendAnalysisLookbackDays: 7,
  minConfidenceForAdjustment: 0.6,
  // ... 其他字段
}
```

### 4. 启动应用

```bash
./gradlew bootRun
```

或使用后台运行：

```bash
cd run
./run.sh
```

### 5. 监控日志

```bash
# 主日志（包含行情和趋势分析）
tail -f logs/application.log

# 交易日志
tail -f logs/trade.log

# 价格日志
tail -f logs/price.log
```

### 6. 观察运行

**正常行情日志（每5秒）：**
```
[USDCUSDT] 行情更新 - Bid: 1.000100, Ask: 1.000200, Last: 1.000150 | ✗ 暂不下单: 价格过高(1.000150>=1.000500) | 消息数: 1234
```

**满足下单条件时：**
```
[USDCUSDT] 行情更新 - Bid: 0.999950, Ask: 1.000050, Last: 1.000000 | ✓ 可下单 (距上限: 0.0500%) | 消息数: 1235

✓ Buy signal detected: lastPrice=0.999900, maxBuyPrice=1.000500, refPrice=1.000000, deviation=0.000600, supportRatio=0.80, minProfit=0.000100

Buy amount calculated: deviation=0.0100%, buyRatio=100.00%, maxBuyAmount=15.00, calculatedAmount=14.73

[USDCUSDT] BUY ORDER PLACED - orderId=12345, price=0.999900, qty=14.75, amount=14.73U, mode=TESTNET
```

**趋势分析日志（每小时）：**
```
开始执行趋势分析和动态调价...

Trend analysis: trend=FALLING, confidence=0.75, avgPrice3d=0.999850, avgPrice7d=1.000010, volatility=0.0012%, support=0.68, reason=近3天均价低于7天均价 -0.0160%, 且买盘支撑弱

Price adjusted: configId=USDCUSDT_TESTNET, trend=FALLING, confidence=0.75, oldPrice=1.000000, newPrice=0.999950, change=-0.0050%, reason=近3天均价低于7天均价; 降低价格阈值，等待更低价格买入

Adjusted minProfitTick: 0.000100 -> 0.000090 (下跌趋势，降低利润要求)

趋势分析完成: trend=FALLING, confidence=0.75, priceChange=-0.0050%, reason=...
```

---

## 监控和调优

### 查看统计数据

**价格统计（价差分布）：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.spread_stats.find({symbol: 'USDCUSDT'}).sort({date: -1, count: -1}).limit(10)"
```

**深度统计：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.depth_stats.find({symbol: 'USDCUSDT'}).sort({date: -1, count: -1}).limit(10)"
```

**交易统计（胜率、盈亏）：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.trade_stats.find({symbol: 'USDCUSDT'}).sort({date: -1}).limit(5)"
```

### 手动调整参数

**提高价格阈值（增加交易频率）：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\$set: {referencePrice: 1.0002}})"
```

**调整趋势分析频率（改为30分钟一次）：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\$set: {trendAnalysisIntervalSeconds: 1800}})"
```

**暂时禁用动态调价：**
```bash
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\$set: {enableDynamicPriceAdjustment: false}})"
```

### 性能指标

监控这些关键指标：

1. **交易频率**：每天的交易次数
2. **胜率**：盈利交易 / 总交易
3. **平均持仓时间**：是否能快速成交
4. **总盈亏**：累计收益
5. **价格调整频率**：动态调价的触发次数

---

## 故障排查

### 问题1：一直显示"价格过高"，没有交易

**原因**：USDC/USDT价格通常在1.0附近或略高，只有低于 maxBuyPrice 时才买入。

**解决方案**：
1. 查看当前市场价格
2. 适当提高 maxBuyPrice（如1.001或1.002）
3. 降低 minProfitTick

### 问题2：趋势分析没有执行

**检查步骤**：
1. 确认 `enableDynamicPriceAdjustment: true`
2. 查看日志是否有"开始执行趋势分析"
3. 检查数据库中是否有统计数据（spread_stats, depth_stats）
4. 确认已运行足够长时间积累数据

**解决方案**：
```bash
# 查看是否有统计数据
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.spread_stats.countDocuments({symbol: 'USDCUSDT'})"

# 如果没有数据，需要等待系统运行一段时间收集数据
```

### 问题3：调整幅度太小或太频繁

**调整置信度阈值**：
```bash
# 提高阈值，减少调整频率（只在非常确定时调整）
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\$set: {minConfidenceForAdjustment: 0.8}})"
```

### 问题4：买入金额不符合预期

**原因**：买入金额是动态计算的，受多个因素影响。

**查看计算日志**：
```bash
grep "Buy amount calculated" logs/application.log
```

会显示详细的计算过程：
```
Buy amount calculated: deviation=0.0050%, buyRatio=50.00%, maxBuyAmount=15.00, calculatedAmount=7.35
```

### 问题5：配置更新后没有生效

**原因**：配置可能被缓存。

**解决方案**：重启应用
```bash
# 停止应用（Ctrl+C）
# 重新启动
./gradlew bootRun
```

---

## 配置建议

### 保守策略（适合测试和初期）

```javascript
{
  referencePrice: 1.0,                          // 参考价格（锚定价）
  maxBuyPrice: 1.0003,                          // 只在价格低于1.0003时买入
  maxBuyAmountUsdt: 15.0,                       // 单次最大15 USDT
  minProfitTick: 0.0001,                        // 最小利润0.01%
  maxTotalInvestUsdt: 500.0,                    // 总投入500 USDT
  maxHoldSeconds: 1800,                         // 最长持仓30分钟
  enableDynamicPriceAdjustment: true,           // 启用动态调价
  trendAnalysisIntervalSeconds: 3600,           // 每小时分析一次
  minConfidenceForAdjustment: 0.7               // 较高的置信度要求
}
```

### 积极策略（适合有经验后）

```javascript
{
  referencePrice: 1.0,                          // 参考价格（锚定价）
  maxBuyPrice: 1.001,                           // 价格稍高也可以买（更频繁交易）
  maxBuyAmountUsdt: 30.0,                       // 单次最大30 USDT
  minProfitTick: 0.00005,                       // 降低利润要求（更容易成交）
  maxTotalInvestUsdt: 500.0,                    // 总投入500 USDT
  maxHoldSeconds: 1800,                         // 最长持仓30分钟
  enableDynamicPriceAdjustment: true,
  trendAnalysisIntervalSeconds: 1800,           // 每30分钟分析一次（更频繁）
  minConfidenceForAdjustment: 0.6               // 较低的置信度要求（更灵活）
}
```

### 正式网配置（谨慎使用）

```javascript
{
  referencePrice: 1.0,                          // 参考价格（锚定价）
  maxBuyPrice: 1.0005,                          // 最高买入价
  maxBuyAmountUsdt: 50.0,                       // 更大的单次金额
  minProfitTick: 0.0001,
  maxTotalInvestUsdt: 1000.0,                   // 更大的总投入
  maxHoldSeconds: 1800,
  enableDynamicPriceAdjustment: true,
  trendAnalysisIntervalSeconds: 3600,
  minConfidenceForAdjustment: 0.7               // 更谨慎
}
```

---

## 技术架构

### 核心模块

1. **TrendAnalyzer** - 趋势分析器
   - 分析历史价格统计（SpreadStats）
   - 分析深度统计（DepthStats）
   - 计算价格趋势和置信度

2. **DynamicPriceAdjuster** - 动态价格调整器
   - 根据趋势调整 maxBuyPrice（最高买入价）
   - 同步调整相关参数
   - 更新数据库配置

3. **StrategyEngine** - 策略引擎
   - 集成趋势分析定时任务
   - 执行交易决策
   - 管理订单生命周期

### 数据流

```
市场行情 -> StatsService -> 统计表(spread_stats, depth_stats)
                                  ↓
                            TrendAnalyzer（每小时）
                                  ↓
                          分析趋势和置信度
                                  ↓
                         DynamicPriceAdjuster
                                  ↓
                         更新 maxBuyPrice
                         调整相关参数
                                  ↓
                            StrategyEngine
                                  ↓
                            执行交易决策
```

---

## 更新日志

### 2026-01-18 - 趋势分析和动态调价

- ✅ 新增 TrendAnalyzer 趋势分析服务
- ✅ 新增 DynamicPriceAdjuster 动态价格调整服务
- ✅ 新增趋势分析相关配置参数
- ✅ 集成定时趋势分析任务
- ✅ 实现自动参数优化（minProfitTick、minSupportRatio、maxHoldSeconds）

### 之前的更新

- 动态买入金额计算
- referencePrice 和 maxBuyAmountUsdt 参数重构
- 增强日志输出
- WebSocket 连接优化

---

## 相关文档

- [README_CN.md](./README_CN.md) - 项目总体说明
- [TEST_GUIDE.md](./TEST_GUIDE.md) - 测试指南
- [doc/Strategy.md](./doc/Strategy.md) - 策略原理详解
- [doc/data.md](./doc/data.md) - 历史数据分析

---

**祝交易顺利！** 🚀

如有问题，请查看日志或联系开发团队。

