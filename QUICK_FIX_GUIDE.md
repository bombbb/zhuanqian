# 快速修复指南

## 常见币安API错误及解决方案

### 1. Filter failure: LOT_SIZE
**错误信息**：
```
{"code":-1013,"msg":"Filter failure: LOT_SIZE"}
```

**原因**：下单数量不符合币安的 LOT_SIZE 规则（如 stepSize=1.0，必须是整数）

**解决方案**：✅ 已修复
- 自动从币安API获取交易规则
- 下单前按 stepSize 自动调整数量
- 详情：`LOT_SIZE_FIX.md`

---

### 2. Filter failure: NOTIONAL
**错误信息**：
```
{"code":-1013,"msg":"Filter failure: NOTIONAL"}
```

**原因**：订单金额（价格 × 数量）低于最小要求（通常5-10 USDT）

**解决方案**：✅ 已修复
- 自动验证订单金额是否满足 minNotional
- 如果不满足，自动向上调整数量
- 详情：`NOTIONAL_FIX.md`

---

### 3. Filter failure: MAX_NUM_ORDERS
**错误信息**：
```
{"code":-2010,"msg":"Filter failure: MAX_NUM_ORDERS"}
```

**原因**：挂单数量超过币安限制（200个）

**解决方案**：✅ 已修复
- 下单前自动检查挂单数量
- 超过150个时自动清理最旧的挂单
- 将挂单数量控制在100个左右
- 详情：`MAX_NUM_ORDERS_FIX.md`

---

## 部署新版本

### 1. 编译项目
```bash
cd /Users/bao/java/zhuanqian
./gradlew clean bootJar
```

### 2. 停止旧版本
```bash
cd run
# 如果应用正在运行，先停止
kill $(cat app.pid) 2>/dev/null || true
```

### 3. 启动新版本
```bash
./run.sh
```

### 4. 查看日志
```bash
# 实时查看应用日志
tail -f logs/application.log

# 查看交易日志
tail -f logs/trade.log

# 查看价格日志
tail -f logs/price.log
```

---

## 验证修复

### LOT_SIZE 修复验证
查看日志中是否有：
```
Loaded symbol filter for USDCUSDT: minQty=1.0, stepSize=1.0
Adjusted quantity: 4.16 -> 4.0 (stepSize=1.0)
```

### NOTIONAL 修复验证
查看日志中是否有：
```
Loaded symbol filter for USDCUSDT: minQty=1.0, stepSize=1.0, minNotional=10.0
Adjusted quantity to meet minNotional: 7.5 -> 10.0 (price=1.0001, minNotional=10.0)
```

### MAX_NUM_ORDERS 修复验证
查看日志中是否有：
```
Current open orders count: symbol=USDCUSDT, count=155
⚠ Open orders count (155) exceeds threshold (150), cleaning up...
Batch cancel completed: symbol=USDCUSDT, success=50, failed=0, total=50
✓ Cleaned up 50 old orders, target reached
```

---

## 故障排查

### 问题：编译失败
**可能原因**：
- Java环境未配置
- Gradle依赖下载失败

**解决方法**：
```bash
# 检查Java版本（需要Java 21+）
java -version

# 清理并重新下载依赖
./gradlew clean --refresh-dependencies
./gradlew bootJar
```

### 问题：应用启动失败
**可能原因**：
- MongoDB未启动
- 端口被占用
- 配置文件错误

**解决方法**：
```bash
# 检查MongoDB
docker ps | grep mongo

# 如果未启动，启动MongoDB
cd run
docker-compose up -d

# 检查端口占用
lsof -i :8080

# 查看启动日志
tail -100 logs/application.log
```

### 问题：仍然出现API错误
**可能原因**：
- 使用了旧版本的jar包
- 币安API规则变更

**解决方法**：
```bash
# 确认使用的jar包版本
ls -lh build/libs/

# 重新编译
./gradlew clean bootJar

# 完全重启
cd run
kill $(cat app.pid) 2>/dev/null || true
./run.sh

# 查看详细错误日志
grep "API request failed" logs/application.log
```

---

## 监控建议

### 1. 挂单数量监控
```bash
# 查看挂单数量变化
grep "Current open orders count" logs/application.log | tail -20

# 查看清理事件
grep "Cleaned up.*old orders" logs/application.log
```

### 2. 交易成功率监控
```bash
# 查看成功的交易
grep "TRADE COMPLETED" logs/trade.log | wc -l

# 查看失败的交易
grep "Failed to" logs/application.log | tail -20
```

### 3. 错误监控
```bash
# 查看所有API错误
grep "API request failed" logs/application.log | tail -20

# 查看特定错误
grep "Filter failure" logs/application.log | tail -20
```

---

## 配置调整建议

### 如果频繁触发挂单清理
调整 `StrategyEngine.java` 中的阈值：
```java
private static final int MAX_OPEN_ORDERS_THRESHOLD = 180;  // 提高阈值
private static final int TARGET_OPEN_ORDERS = 150;         // 提高目标
```

### 如果订单金额经常不满足 NOTIONAL
调整配置中的 `maxBuyAmountUsdt`：
```javascript
// 在 MongoDB 中更新
db.strategy_configs.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { maxBuyAmountUsdt: 15.0 } }  // 提高到15U或更高
)
```

### 如果想降低下单频率
调整配置中的买入条件：
```javascript
// 提高最小支撑比率
db.strategy_configs.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { minSupportRatio: 0.85 } }  // 从0.8提高到0.85
)
```

---

## 相关文档

- **MAX_NUM_ORDERS_FIX.md** - MAX_NUM_ORDERS 错误详细修复说明
- **NOTIONAL_FIX.md** - NOTIONAL 错误详细修复说明
- **LOT_SIZE_FIX.md** - LOT_SIZE 错误详细修复说明
- **IMPLEMENTATION_SUMMARY.md** - 完整实施总结
- **EXECUTION_SUMMARY.md** - 执行总结报告

---

## 联系支持

如果以上方法都无法解决问题，请：
1. 收集完整的错误日志（`logs/application.log`）
2. 记录错误发生的时间和场景
3. 检查币安API状态：https://www.binance.com/en/support/announcement
4. 联系开发团队

---

**最后更新**：2026-01-18

