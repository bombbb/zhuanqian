# 统计系统快速参考

## 核心问题解答

### 1. minProfitTick 配置
- **当前值**: 0.0001 (0.01%)
- **合理性**: 基于历史数据，USDC 价格主要在 0.9995-1.0005 区间，0.0001 可以捕捉大部分交易机会

### 2. DepthStats 表（深度统计）
**会有多条数据**，按以下维度分组：
- `priceRangeBucket`: 价格区间（如 "1.0000-1.0005"）
- `supportRatioBucket`: 支撑比率区间（如 "0.8-0.9"）
- `date`: 日期

**示例**：
```javascript
// 价格 1.0002, 支撑比率 0.85, 出现 661 次
{
  _id: "USDCUSDT_2026-01-17_1.0000-1.0005_0.8-0.9",
  priceRangeBucket: "1.0000-1.0005",
  supportRatioBucket: "0.8-0.9",
  count: 661
}

// 价格 1.0007, 支撑比率 0.85, 出现 123 次（不同价格区间）
{
  _id: "USDCUSDT_2026-01-17_1.0005-1.0010_0.8-0.9",
  priceRangeBucket: "1.0005-1.0010",
  supportRatioBucket: "0.8-0.9",
  count: 123
}
```

### 3. Position 表（持仓）
- 每个 (symbol + mode) 组合只有一条记录
- 记录：持仓数量、平均买入价格、总投入、未实现盈亏
- 买入时创建/更新，卖出时减少

### 4. SpreadStats 表（价差统计）
- 记录买价（bid）和卖价（ask）的组合
- 用于分析价差分布，优化 minProfitTick
- 相同价差会累加 count

### 5. TradeStats 表（交易统计）
**已完善**，卖单成交时自动记录：
- 盈亏（PnL）= (卖价 - 买价) × 数量
- 持仓时间 = 卖单成交时间 - 买单成交时间
- 胜率、平均持仓时间等统计指标

---

## 新增功能

### ConfigChangeLog（配置变更日志）
自动记录所有配置变更：
- 变更来源：手动/自动（趋势/价差/深度/交易统计触发）
- 变更前后的值
- 变更原因和时间

### StatsAnalysisService（统计分析服务）
基于统计数据自动优化配置：

1. **价差分析** → 优化 `minProfitTick`
2. **深度分析** → 优化 `minSupportRatio`
3. **交易分析** → 优化 `maxHoldSeconds`

**使用方法**：
```java
// 自动分析最近7天数据并应用配置变更
statsAnalysisService.autoAnalyzeAndApply("USDCUSDT_TESTNET", 7);
```

---

## 快速测试

### 1. 初始化数据库
```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### 2. 运行测试
```bash
# 运行统计系统测试脚本
./test-stats-system.sh

# 或手动运行测试
./gradlew test --tests StatsAnalysisServiceTest
./gradlew test --tests StatsIntegrationTest
```

### 3. 查看数据
```bash
# 连接数据库
mongosh mongodb://localhost:27017/strategy_db

# 查看配置变更日志
db.config_change_logs.find().sort({changeTime: -1}).limit(5)

# 查看深度统计（按价格区间分组）
db.depth_stats.find().sort({count: -1}).limit(10)

# 查看价差统计
db.spread_stats.find().sort({count: -1}).limit(10)

# 查看交易统计
db.trade_stats.find().sort({date: -1})
```

---

## 文件清单

### 新增文件
- `src/main/java/com/zq/config/ConfigChangeLog.java` - 配置变更日志实体
- `src/main/java/com/zq/stats/StatsAnalysisService.java` - 统计分析服务
- `src/test/java/com/zq/stats/StatsAnalysisServiceTest.java` - 单元测试
- `src/test/java/com/zq/integration/StatsIntegrationTest.java` - 集成测试
- `test-stats-system.sh` - 测试脚本
- `STATS_IMPROVEMENT_SUMMARY.md` - 详细总结
- `STATS_QUICK_REFERENCE.md` - 本文档

### 修改文件
- `src/main/java/com/zq/strategy/StrategyService.java` - 添加配置变更记录
- `src/main/java/com/zq/strategy/StrategyEngine.java` - 添加交易统计记录
- `doc/db/init-strategy-config.js` - 添加新索引

---

## 数据库集合说明

| 集合名 | 用途 | 是否有多条数据 | 分组维度 |
|--------|------|----------------|----------|
| strategy_config | 策略配置 | 少量（每个symbol+mode一条） | symbol + mode |
| orders | 订单记录 | 很多 | - |
| positions | 当前持仓 | 少量（每个symbol+mode一条） | symbol + mode |
| spread_stats | 价差统计 | 很多 | symbol + date + bidPrice + askPrice |
| depth_stats | 深度统计 | **很多** | symbol + date + **priceRangeBucket** + supportRatioBucket |
| trade_stats | 交易统计 | 中等 | symbol + date |
| config_change_logs | 配置变更日志 | 中等 | - |

---

## 重点说明

### DepthStats 的多条数据
**是的，DepthStats 会有很多条数据！**

- 不同的 `priceRangeBucket` 会产生不同的记录
- 不同的 `supportRatioBucket` 会产生不同的记录
- 每天都会产生新的记录
- 相同组合的数据会累加 `count`

**例子**：同一天的数据
```
1.0000-1.0005 + 0.6-0.7 : count=100
1.0000-1.0005 + 0.7-0.8 : count=50
1.0005-1.0010 + 0.6-0.7 : count=80
1.0005-1.0010 + 0.7-0.8 : count=40
...
```

### Position 的作用
- **非常有用**！记录当前持仓状态
- 如果有持仓，至少有一条记录
- 如果没有持仓，quantity = 0 或表为空

### TradeStats 的记录时机
- **卖单成交时**自动记录
- 包括限价卖单成交和市价平仓
- 不需要手动调用

---

详细说明请参考 `STATS_IMPROVEMENT_SUMMARY.md`

