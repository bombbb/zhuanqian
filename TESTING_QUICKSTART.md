# 测试快速开始指南

## 🎯 目标
本指南帮助你快速验证价格变化场景下的策略行为。

---

## ✅ 已完成的工作

1. ✅ 数据库迁移完成 - `basePrice` 字段已删除
2. ✅ 配置正确设置 - `referencePrice` 和 `maxBuyPrice` 都已配置
3. ✅ 价格模拟测试通过 - 上涨和下跌场景都已验证
4. ✅ 测试框架就绪 - 可随时使用

---

## 🚀 快速开始

### 方式1: 使用测试脚本（推荐）

```bash
# 运行完整的价格模拟测试
./run-tests-with-auth.sh
```

**这个脚本会：**
- 模拟价格上涨：1.0002 -> 1.0003 -> 1.0005
- 模拟价格下跌：1.0005 -> 1.0003 -> 1.0002
- 验证配置更新
- 检查数据库状态
- 显示详细报告

### 方式2: 运行Java单元测试

```bash
# 运行所有价格模拟测试
./gradlew test --tests PriceSimulationTest

# 运行数据更新测试
./gradlew test --tests DataUpdateTest
```

### 方式3: 启动应用进行真实测试

```bash
# 1. 启动应用
cd run && ./run.sh

# 2. 在另一个终端监控日志
tail -f logs/application.log | grep -i 'cancel\|trend\|maxBuyPrice'

# 3. 在第三个终端触发价格变化
cd .. && ./run-tests-with-auth.sh
```

---

## 📊 测试场景

### 场景1: 价格上涨 🔼
```
起始: 1.0002
上涨: 1.0003
继续: 1.0005
```
**预期**: 不撤销买单，允许在稍高价格买入

### 场景2: 价格下跌 🔽
```
起始: 1.0005
下跌: 1.0003
继续: 1.0002
```
**预期**: 撤销所有买单，避免在不利价格成交

---

## 📖 详细文档

- **PRICE_SIMULATION_GUIDE.md** - 完整的测试指南和验证清单
- **EXECUTION_SUMMARY.md** - 执行总结和测试结果
- **IMPLEMENTATION_SUMMARY.md** - 技术实现细节

```bash
# 查看测试指南
cat PRICE_SIMULATION_GUIDE.md

# 查看执行总结
cat EXECUTION_SUMMARY.md
```

---

## 🔍 验证要点

### 配置验证
```bash
# 连接MongoDB查看配置
docker exec -i $(docker ps | grep mongo | awk '{print $1}') \
  mongosh "mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin" \
  --eval "db.strategy_config.findOne({ _id: 'USDCUSDT_TESTNET' })"
```

**应该看到：**
- ✅ `referencePrice: 1.0`
- ✅ `maxBuyPrice: 1.0002`（或其他值）
- ✅ 没有 `basePrice` 字段

### 日志验证

启动应用后查看日志：
```bash
# 监控配置更新
grep "maxBuyPrice" logs/application.log

# 监控撤单操作
grep -i "cancel" logs/application.log logs/trade.log

# 监控趋势分析
grep "Trend" logs/application.log
```

---

## ⚠️ 注意事项

1. **撤单功能**: 需要应用运行并由 `DynamicPriceAdjuster` 在趋势分析时触发
2. **趋势分析**: 需要足够的历史数据（深度统计、价差统计等）
3. **缓存更新**: 使用 `StrategyService.updateMaxBuyPrice()` 会自动清空缓存

---

## 🎓 核心概念

### referencePrice (参考价)
- 仅用于计算价格偏离度
- 对于稳定币USDC，通常设为 1.0
- **不参与下单判断**

### maxBuyPrice (最高买入价)
- 真正的买入价格阈值
- 只有当市场价格低于此值时才考虑买入
- **风控的关键参数**

### 动态调价逻辑
- **价格上涨**: 提高 `maxBuyPrice`，不撤单
- **价格下跌**: 降低 `maxBuyPrice`，撤销所有买单
- **价格震荡**: 微调 `maxBuyPrice`

---

## 🔧 故障排除

### MongoDB连接失败
```bash
# 检查容器
docker ps | grep mongo

# 重启容器
cd run && docker-compose restart mongodb
```

### 应用未响应价格变化
1. 确认应用正在运行
2. 检查 `DynamicPriceAdjuster` 是否启用
3. 检查日志中的错误信息
4. 验证是否有足够的历史数据

### 配置未更新
```bash
# 手动更新配置（带认证）
docker exec -i $(docker ps | grep mongo | awk '{print $1}') \
  mongosh "mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin" \
  --eval "
    db.strategy_config.updateOne(
      { _id: 'USDCUSDT_TESTNET' },
      { \$set: { maxBuyPrice: 1.0005 } }
    )
  "
```

---

## 📝 下一步

1. ✅ 运行测试脚本验证基本功能
2. ✅ 启动应用进行真实环境测试
3. ✅ 监控日志确认撤单和缓存清空
4. ✅ 根据实际运行情况调优参数

---

## 🆘 需要帮助？

- 查看 `PRICE_SIMULATION_GUIDE.md` 了解详细测试方法
- 查看 `EXECUTION_SUMMARY.md` 了解执行结果
- 查看 `IMPLEMENTATION_SUMMARY.md` 了解技术细节
- 检查 `logs/application.log` 和 `logs/trade.log`

---

**最后更新**: 2026-01-18  
**状态**: ✅ 所有测试已通过，可以开始真实环境验证

