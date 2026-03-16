# MAX_NUM_ORDERS 错误修复

## 问题描述

应用运行时出现币安API错误：
```
java.lang.RuntimeException: API request failed: 400 - {"code":-2010,"msg":"Filter failure: MAX_NUM_ORDERS"}
```

**原因**：账户中某个交易对的挂单数量超过了币安的限制（通常是200个）。

---

## 解决方案

### 核心策略
1. **预防性检查**：下单前检查挂单数量
2. **自动清理**：当挂单数量超过阈值（150个）时，自动撤销最旧的挂单
3. **目标管理**：将挂单数量控制在100个以内，留有充足余量

### 修改的文件

#### 1. BinanceApiService.java
添加挂单管理相关方法：

**新增方法**：
```java
// 批量撤销订单
public int cancelOrders(String symbol, List<Long> orderIds)

// 检查挂单数量
public int checkOpenOrdersCount(String symbol)
```

**功能说明**：
- `cancelOrders()`: 批量撤销订单，返回成功撤销的数量
- `checkOpenOrdersCount()`: 从币安API查询当前挂单数量

#### 2. OrderService.java
添加订单查询和统计方法：

**新增方法**：
```java
// 查询最旧的挂单（按创建时间排序）
public List<Order> getOldestOpenOrders(String symbol, int limit)

// 查询挂单数量
public long countOpenOrders(String symbol)
```

**功能说明**：
- `getOldestOpenOrders()`: 获取最旧的N个挂单，用于清理
- `countOpenOrders()`: 统计本地数据库中的挂单数量

#### 3. StrategyEngine.java
添加挂单检查和清理逻辑：

**新增常量**：
```java
private static final int MAX_OPEN_ORDERS_THRESHOLD = 150;  // 触发清理的阈值
private static final int TARGET_OPEN_ORDERS = 100;         // 清理后的目标数量
private static final int CLEANUP_BATCH_SIZE = 50;          // 每次清理的数量
```

**新增方法**：
```java
// 检查并清理挂单
private boolean checkAndCleanupOrders(String symbol)
```

**修改点**：
- 在 `executeBuy()` 方法中，下单前调用 `checkAndCleanupOrders()`
- 如果挂单数量超过阈值，自动清理最旧的挂单

---

## 工作原理

### 1. 下单前检查
每次执行买入操作前，都会检查当前挂单数量：
```java
if (!checkAndCleanupOrders(symbol)) {
    log.warn("✗ Cannot place order: too many open orders after cleanup");
    return;
}
```

### 2. 自动清理流程
当挂单数量超过150个时：
1. 计算需要清理的数量：`min(50, 当前数量 - 100)`
2. 从本地数据库查询最旧的N个挂单
3. 批量调用币安API撤销这些订单
4. 更新本地订单状态为 `CANCELED`
5. 验证清理后的数量是否满足要求

### 3. 数据同步
- **优先使用本地数据库**：查询最旧的挂单
- **兜底使用币安API**：如果本地没有数据，从币安API获取挂单列表
- **双向更新**：撤单后同时更新币安和本地数据库

---

## 配置参数说明

| 参数 | 值 | 说明 |
|-----|-----|------|
| `MAX_OPEN_ORDERS_THRESHOLD` | 150 | 触发清理的阈值，低于币安限制（200）留有余量 |
| `TARGET_OPEN_ORDERS` | 100 | 清理后的目标数量，确保有足够空间继续下单 |
| `CLEANUP_BATCH_SIZE` | 50 | 每次清理的最大数量，避免一次性撤销过多 |

**为什么设置150而不是200？**
- 币安的限制是200个挂单
- 设置150作为阈值，留有50个的安全余量
- 避免在清理过程中继续下单导致超限

**为什么清理到100？**
- 清理后留有充足空间（100个空位）
- 避免频繁触发清理逻辑
- 平衡挂单数量和清理频率

---

## 测试验证

### 1. 编译项目
```bash
cd /Users/bao/java/zhuanqian
./gradlew clean bootJar
```

### 2. 启动应用
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
Current open orders count: symbol=USDCUSDT, count=155
⚠ Open orders count (155) exceeds threshold (150), cleaning up...
Canceled order: symbol=USDCUSDT, orderId=12345678
Batch cancel completed: symbol=USDCUSDT, success=50, failed=0, total=50
✓ Cleaned up 50 old orders, target reached
Current open orders count: symbol=USDCUSDT, count=105
```

### 4. 模拟测试（可选）
如果想测试清理逻辑，可以临时降低阈值：
```java
// 在 StrategyEngine.java 中临时修改
private static final int MAX_OPEN_ORDERS_THRESHOLD = 10;  // 临时降低到10
```

---

## 注意事项

### ⚠️ 重要提醒

1. **清理会撤销最旧的订单**
   - 按创建时间排序，最旧的订单会被优先撤销
   - 这些订单可能是盈利的卖单，撤销后需要市价平仓
   - 建议监控清理日志，评估对盈利的影响

2. **清理过程可能需要时间**
   - 批量撤单是逐个调用API，不是原子操作
   - 如果有50个订单需要撤销，可能需要几秒钟
   - 清理期间会阻止新订单创建

3. **本地数据库同步**
   - 清理依赖本地数据库的订单记录
   - 如果本地数据不准确，可能无法正确清理
   - 建议定期检查本地订单数据与币安API的一致性

4. **网络错误处理**
   - 撤单失败会记录日志但不会中断流程
   - 失败的订单会计入 `failCount`
   - 如果大量撤单失败，可能需要手动介入

5. **阈值调整建议**
   - 如果经常触发清理，可以考虑提高 `TARGET_OPEN_ORDERS`
   - 如果想更积极地控制挂单数量，可以降低 `MAX_OPEN_ORDERS_THRESHOLD`
   - 建议根据实际运行情况调整

---

## 监控建议

### 1. 挂单数量监控
定期检查挂单数量：
```bash
# 从日志中查看
grep "Current open orders count" logs/application.log | tail -20

# 或者直接查询币安API（需要认证）
# 参考 BinanceApiService.checkOpenOrdersCount()
```

### 2. 清理事件监控
监控清理事件的频率和效果：
```bash
# 查看清理日志
grep "Cleaned up.*old orders" logs/application.log

# 查看撤单失败
grep "Failed to cancel order" logs/application.log
```

### 3. 订单同步检查
定期对比本地数据库和币安API的订单数量：
```javascript
// MongoDB 查询
db.orders.count({
  symbol: "USDCUSDT",
  status: { $in: ["NEW", "SUBMITTED"] }
})

// 对比币安API返回的数量
// 如果差异较大，可能需要同步数据
```

---

## 优化建议

### 1. 定期清理任务（未实现）
可以考虑添加定时任务，定期清理过期订单：
```java
@Scheduled(fixedRate = 3600000) // 每小时执行一次
public void scheduledCleanup() {
    // 清理超过1小时的挂单
    // 或者清理盈利空间不足的挂单
}
```

### 2. 智能清理策略（未实现）
当前清理策略是"最旧优先"，可以考虑更智能的策略：
- 优先清理盈利空间最小的卖单
- 优先清理价格偏离较大的买单
- 保留接近成交的订单

### 3. 挂单数量预警（未实现）
添加挂单数量预警机制：
```java
if (openOrdersCount > 120) {
    log.warn("⚠ Open orders approaching threshold: {}/150", openOrdersCount);
    // 可以发送通知或调整策略
}
```

---

## 故障排查

### 问题1：清理后仍然超限
**可能原因**：
- 本地数据库与币安API不同步
- 清理过程中有新订单创建
- 撤单失败

**解决方法**：
1. 检查日志中的 `failCount`
2. 手动查询币安API确认挂单数量
3. 如果需要，手动撤销部分订单

### 问题2：频繁触发清理
**可能原因**：
- 下单频率过高
- `TARGET_OPEN_ORDERS` 设置过低

**解决方法**：
1. 降低下单频率（调整策略参数）
2. 提高 `TARGET_OPEN_ORDERS` 到120或更高
3. 检查是否有订单成交缓慢的问题

### 问题3：清理了盈利订单
**可能原因**：
- 清理策略是"最旧优先"，不考虑盈利情况

**解决方法**：
1. 实现智能清理策略（见优化建议）
2. 降低 `maxHoldSeconds`，让订单更快成交或平仓
3. 提高 `minProfitTick`，减少低利润订单

---

## 相关文档

- **LOT_SIZE_FIX.md** - LOT_SIZE 错误修复
- **NOTIONAL_FIX.md** - NOTIONAL 错误修复
- **IMPLEMENTATION_SUMMARY.md** - 完整实施总结
- **EXECUTION_SUMMARY.md** - 执行总结报告

---

## 总结

本次修复实现了自动化的挂单数量管理机制，有效防止 `MAX_NUM_ORDERS` 错误：

**核心改进**：
1. ✅ 下单前自动检查挂单数量
2. ✅ 超过阈值时自动清理最旧的挂单
3. ✅ 批量撤单提高效率
4. ✅ 本地数据库和币安API双向同步
5. ✅ 详细的日志记录和错误处理

**下一步**：
1. 编译并部署新版本
2. 监控清理日志
3. 根据实际情况调整阈值参数
4. 考虑实现智能清理策略

---

**问题反馈**：如有任何问题，请参考日志文件或联系开发团队。

