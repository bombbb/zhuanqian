# 趋势分析和动态调价功能更新总结

## 更新日期
2026-01-18

## 概述

本次更新添加了智能趋势分析和动态价格调整功能，使系统能够根据历史数据自动预测市场走势并调整交易策略。

---

## 主要功能

### 1. 趋势分析器 (TrendAnalyzer)

**功能：**
- 分析最近N天的价格统计数据（spread_stats）
- 分析深度统计数据（depth_stats）
- 计算加权平均价格、价格波动率、平均支撑比率
- 判断市场趋势：上涨(RISING)、下跌(FALLING)、震荡(CONSOLIDATING)
- 计算趋势置信度（0-1）

**文件：** `src/main/java/com/zq/strategy/TrendAnalyzer.java`

### 2. 动态价格调整器 (DynamicPriceAdjuster)

**功能：**
- 根据趋势分析结果动态调整 referencePrice（价格阈值）
- 上涨趋势：提高价格阈值（不那么保守）
- 下跌趋势：降低价格阈值（更保守，等更低价格）
- 震荡趋势：微调至当前市场价
- 同步调整相关参数（minProfitTick、minSupportRatio、maxHoldSeconds）

**文件：** `src/main/java/com/zq/strategy/DynamicPriceAdjuster.java`

### 3. 集成到策略引擎

**功能：**
- 在 StrategyEngine 中启动趋势分析定时任务
- 每隔 trendAnalysisIntervalSeconds 执行一次分析
- 只有置信度 >= minConfidenceForAdjustment 时才执行调整
- 自动记录调整日志

**文件：** `src/main/java/com/zq/strategy/StrategyEngine.java`

---

## 新增配置参数

在 `StrategyConfig` 中新增以下参数：

| 参数名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| enableDynamicPriceAdjustment | boolean | true | 是否启用动态价格调整 |
| trendAnalysisIntervalSeconds | long | 3600 | 趋势分析间隔（秒） |
| trendAnalysisLookbackDays | int | 7 | 趋势分析回看天数 |
| minConfidenceForAdjustment | double | 0.6 | 动态调价的最小置信度阈值 |

---

## 调整逻辑说明

### 价格阈值调整

```
上涨趋势：
  - referencePrice ↑ 提高最多 0.05% * confidence
  - 但不超过当前市场价 + 0.1%
  
下跌趋势：
  - referencePrice ↓ 降低最多 0.05% * confidence
  - 但不低于当前市场价 - 0.1%
  
震荡趋势：
  - 微调至接近当前市场价
  - 如果偏离 > 0.02%，调整30%的差距
```

### 相关参数调整

**minProfitTick（最小利润）：**
- 上涨趋势且置信度 > 0.7：提高10%（追求更高利润）
- 下跌趋势且置信度 > 0.7：降低10%（降低利润要求，增加成交机会）

**minSupportRatio（最小支撑比率）：**
- 如果历史平均支撑比率明显不同（差距 > 0.1），调整为平均值的90%

**maxHoldSeconds（最大持仓时间）：**
- 如果价格波动率 > 0.1%，缩短20%（快速止盈/止损）

---

## 使用方法

### 1. 更新数据库配置

```bash
docker exec -i trading-mongodb mongosh -u admin -p admin123 \
  --authenticationDatabase admin strategy_db < doc/db/update-trend-config.js
```

### 2. 启动应用

```bash
./gradlew bootRun
```

### 3. 观察日志

**趋势分析执行日志：**
```
开始执行趋势分析和动态调价...
Trend analysis: trend=FALLING, confidence=0.75, avgPrice3d=0.999850, avgPrice7d=1.000010, ...
Price adjusted: configId=USDCUSDT_TESTNET, trend=FALLING, confidence=0.75, oldPrice=1.000000, newPrice=0.999950, change=-0.0050%
Adjusted minProfitTick: 0.000100 -> 0.000090 (下跌趋势，降低利润要求)
趋势分析完成: trend=FALLING, confidence=0.75, priceChange=-0.0050%
```

### 4. 手动触发或调整

**调整分析频率（改为30分钟）：**
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

---

## 文件变更清单

### 新增文件
1. `src/main/java/com/zq/strategy/TrendAnalyzer.java` - 趋势分析器
2. `src/main/java/com/zq/strategy/DynamicPriceAdjuster.java` - 动态价格调整器
3. `doc/db/update-trend-config.js` - 数据库配置更新脚本
4. `STRATEGY_GUIDE.md` - 综合策略指南（整合所有文档）
5. `UPDATE_SUMMARY.md` - 本文件

### 修改文件
1. `src/main/java/com/zq/strategy/StrategyConfig.java` - 添加趋势分析配置参数
2. `src/main/java/com/zq/strategy/StrategyEngine.java` - 集成趋势分析定时任务

### 删除文件（已合并到 STRATEGY_GUIDE.md）
1. ~~`CHANGES_BASEPRICE_REFACTOR.md`~~
2. ~~`CHANGES_ORDER_AMOUNT.md`~~
3. ~~`CHANGES_SUMMARY.md`~~
4. ~~`NEW_STRATEGY_LOGIC.md`~~
5. ~~`QUICK_START_NEW_LOGIC.md`~~
6. ~~`PRICE_CHECK_GUIDE.md`~~

### 保留文件
- `README.md` / `README_CN.md` - 项目说明
- `TEST_GUIDE.md` - 测试指南
- `PRE_LAUNCH_CHECKLIST.md` - 上线前检查清单
- `WEBSOCKET_FIX.md` - WebSocket修复说明
- `doc/Strategy.md` - 策略原理
- `doc/data.md` - 数据分析
- `doc/execution-plan.md` - 执行计划

---

## 测试建议

### 1. 短期测试（1-3天）

- 设置 `trendAnalysisIntervalSeconds: 1800`（30分钟分析一次）
- 设置 `minConfidenceForAdjustment: 0.5`（较低阈值，更容易触发）
- 观察价格调整是否合理
- 检查日志输出是否正常

### 2. 中期测试（1-2周）

- 恢复默认配置（1小时分析一次）
- 设置 `minConfidenceForAdjustment: 0.6`
- 统计调整次数和效果
- 对比启用/禁用动态调价的收益差异

### 3. 长期运行

- 根据测试结果优化参数
- 监控系统稳定性
- 定期检查统计数据

---

## 注意事项

1. **数据积累期**：
   - 系统需要运行一段时间才能积累足够的统计数据
   - 建议至少运行3-7天后再评估趋势分析效果

2. **置信度设置**：
   - 置信度阈值越高，调整越谨慎但可能错过机会
   - 置信度阈值越低，调整越频繁但可能过度反应
   - 建议从0.6-0.7开始调整

3. **市场适应性**：
   - 稳定币市场波动较小，趋势可能不明显
   - 系统设计已针对小幅波动优化
   - 调整幅度限制在合理范围内

4. **手动干预**：
   - 可以随时禁用动态调价
   - 可以手动调整 referencePrice
   - 系统会在下次分析时基于新的配置继续运行

---

## 下一步优化方向

1. **机器学习模型**：
   - 使用更复杂的模型预测价格走势
   - 考虑更多维度的特征（成交量、外部市场等）

2. **多时间周期分析**：
   - 同时分析短期（1-3天）、中期（3-7天）、长期（7-30天）趋势
   - 综合多个时间周期做出更准确判断

3. **自适应参数调整**：
   - 根据历史交易效果自动优化参数
   - 实现闭环反馈优化

4. **风险评估**：
   - 增加市场风险评估指标
   - 在高风险时期自动降低投入金额

---

## 总结

✅ **已完成的功能**
- 趋势分析服务（基于历史统计数据）
- 动态价格调整服务（根据趋势自动调整）
- 相关参数同步优化
- 集成到策略引擎
- 完善的日志输出
- 数据库配置更新
- 文档整合和清理

✅ **系统优势**
- 自动适应市场变化
- 无需人工干预
- 多维度数据分析
- 灵活的配置选项
- 详细的日志记录

🎯 **适用场景**
- 稳定币套利交易
- 价格波动较小的市场
- 需要自动化运营的策略
- 中长期持续运行

---

**更新完成！现在可以启动应用并观察动态调价效果。** 🚀

如有问题，请查看 `STRATEGY_GUIDE.md` 获取详细使用指南。

