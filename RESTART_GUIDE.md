# 应用重启指南 - NOTIONAL 修复后

## 修复完成
✅ 已修复 `Filter failure: NOTIONAL` 错误
✅ 已调整配置参数：单次买入 12 USDT
✅ 已重新编译应用

## 快速重启

### 1. 停止当前应用
```bash
cd /Users/bao/java/zhuanqian/run

# 查看当前运行的进程
cat app.pid

# 停止应用
kill $(cat app.pid)

# 或者强制停止
pkill -f zhuanqian
```

### 2. 启动新版本
```bash
cd /Users/bao/java/zhuanqian/run
./run.sh
```

### 3. 查看日志
```bash
# 实时查看应用日志
tail -f /Users/bao/java/zhuanqian/logs/application.log

# 查看交易日志
tail -f /Users/bao/java/zhuanqian/logs/trade.log
```

## 期望看到的日志

### 启动时
```
Loaded symbol filter for USDCUSDT: minQty=1.0, stepSize=1.0, minNotional=10.0
WebSocket connected successfully
Strategy engine started for USDCUSDT
```

### 下单时
```
Adjusted quantity to meet minNotional: 7.5 -> 10.0 (price=1.0001, minNotional=10.0, notional=10.001)
[USDCUSDT] BUY ORDER PLACED - orderId=12345, localId=xxx, price=1.0001, qty=10.0, amount=10.00U, mode=TESTNET
```

### 如果看到错误
```
# 不应该再看到这些错误：
❌ Filter failure: LOT_SIZE
❌ Filter failure: NOTIONAL
```

## 验证修复效果

### 1. 检查配置
```bash
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").strategy_config.findOne(
    {_id: "USDCUSDT_TESTNET"}, 
    {maxBuyAmountUsdt: 1, maxTotalInvestUsdt: 1}
  )'
```

**期望输出**：
```json
{
  "_id": "USDCUSDT_TESTNET",
  "maxBuyAmountUsdt": 12,
  "maxTotalInvestUsdt": 500
}
```

### 2. 监控订单
```bash
# 查看最近的订单
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").orders.find(
    {mode: "TESTNET"}, 
    {symbol: 1, side: 1, price: 1, quantity: 1, status: 1, createTime: 1}
  ).sort({createTime: -1}).limit(5)'
```

### 3. 检查余额
```bash
# 查看日志中的余额信息
grep -i "balance" /Users/bao/java/zhuanqian/logs/application.log | tail -10
```

## 故障排查

### 问题1：仍然出现 NOTIONAL 错误
**原因**：可能使用了旧版本的jar包

**解决**：
```bash
# 确认jar包时间
ls -lh /Users/bao/java/zhuanqian/build/libs/zhuanqian-1.0-SNAPSHOT.jar

# 如果时间不是最新的，重新编译
cd /Users/bao/java/zhuanqian
./gradlew clean bootJar
```

### 问题2：订单金额仍然太小
**原因**：配置未更新或缓存未清空

**解决**：
```bash
# 重新检查配置
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").strategy_config.findOne({_id: "USDCUSDT_TESTNET"})'

# 如果配置不对，重新更新
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").strategy_config.updateOne(
    {_id: "USDCUSDT_TESTNET"}, 
    {$set: {maxBuyAmountUsdt: 12.0}}
  )'

# 重启应用
cd /Users/bao/java/zhuanqian/run
kill $(cat app.pid)
./run.sh
```

### 问题3：余额不足
**原因**：测试网账户余额不够

**解决**：
- 访问币安测试网水龙头获取测试币
- 或者降低 maxBuyAmountUsdt 配置

### 问题4：WebSocket 连接失败
**原因**：网络问题或币安服务异常

**解决**：
```bash
# 检查网络连接
curl -I https://testnet.binance.vision/api/v3/ping

# 检查WebSocket
curl -I https://stream.binance.com:9443

# 查看详细错误日志
grep -i "websocket\|error" /Users/bao/java/zhuanqian/logs/application.log | tail -20
```

## 监控要点

### 关键指标
1. **订单成功率** - 应该接近100%
2. **订单金额** - 应该在10-12U左右
3. **NOTIONAL错误** - 应该为0
4. **LOT_SIZE错误** - 应该为0

### 监控命令
```bash
# 实时监控交易日志
tail -f /Users/bao/java/zhuanqian/logs/trade.log

# 统计错误
grep -i "error\|failed" /Users/bao/java/zhuanqian/logs/application.log | tail -20

# 统计订单
docker exec -i $(docker ps -qf "name=mongo") mongosh -u admin -p admin123 \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("strategy_db").orders.countDocuments({
    mode: "TESTNET",
    createTime: {$gte: new Date(Date.now() - 3600000)}
  })'
```

## 相关文档

- `NOTIONAL_FIX.md` - NOTIONAL 错误修复详细说明
- `LOT_SIZE_FIX.md` - LOT_SIZE 错误修复说明
- `IMPLEMENTATION_SUMMARY.md` - 完整实施总结
- `QUICK_START.md` - 快速启动指南

## 下一步

1. ✅ 重启应用
2. ✅ 观察日志确认修复生效
3. ✅ 监控订单执行情况
4. 📊 收集运行数据，评估策略效果
5. 🔧 根据实际情况微调参数

---

**修复完成时间**：2026-01-18 04:36
**编译版本**：zhuanqian-1.0-SNAPSHOT.jar (20M)
**配置版本**：maxBuyAmountUsdt = 12 USDT

