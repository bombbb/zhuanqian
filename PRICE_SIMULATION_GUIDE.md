# 价格模拟测试指南

## 概述
本文档描述如何测试价格变化场景下的策略行为，包括：
- 价格上涨时的行为（不撤单）
- 价格下跌时的行为（自动撤单）
- 缓存清空机制
- 配置更新流程

---

## 测试方式

### 方式1: 单元测试（推荐）

运行Java单元测试，测试配置更新和缓存机制：

```bash
# 运行所有价格模拟测试
./gradlew test --tests PriceSimulationTest

# 运行特定测试
./gradlew test --tests PriceSimulationTest.testPriceRising
./gradlew test --tests PriceSimulationTest.testPriceFalling
./gradlew test --tests PriceSimulationTest.testCacheEviction
```

**测试内容**:
- ✅ 价格上涨场景 (1.0002 -> 1.0003 -> 1.0005)
- ✅ 价格下跌场景 (1.0005 -> 1.0003 -> 1.0002)
- ✅ 价格震荡场景 (1.0002 -> 1.0004 -> 1.0003)
- ✅ 缓存清空机制
- ✅ 持仓创建和更新

### 方式2: Shell脚本测试

使用MongoDB直接操作配置，模拟价格变化：

```bash
# 执行价格模拟测试脚本
./test-price-simulation.sh
```

**测试流程**:
1. 检查初始配置
2. 模拟价格上涨 (1.0002 -> 1.0003 -> 1.0005)
3. 模拟价格下跌 (1.0005 -> 1.0003 -> 1.0002)
4. 模拟价格震荡
5. 检查最终状态

### 方式3: 运行时测试（真实环境）

在应用运行时通过API或MongoDB更新配置：

```bash
# 通过MongoDB更新maxBuyPrice
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.strategy_config.updateOne(
    { _id: 'USDCUSDT_TESTNET' },
    { \$set: { maxBuyPrice: 1.0006 } }
  )
"

# 然后观察应用日志
tail -f logs/application.log | grep -i "cancel\|maxBuyPrice\|cache"
```

---

## 测试场景详解

### 场景1: 价格上涨 🔼

**模拟过程**:
```
1.0002 (初始) -> 1.0003 (上涨) -> 1.0005 (继续上涨)
```

**预期行为**:
- ✅ `maxBuyPrice` 成功更新
- ✅ 缓存自动清空
- ✅ **不撤销买单**（允许在稍高价格买入）
- ✅ 新买单使用更新后的价格

**验证点**:
```bash
# 检查配置是否更新
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.strategy_config.findOne({ _id: 'USDCUSDT_TESTNET' }).maxBuyPrice
"

# 检查买单是否仍然存在
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.orders.count({ 
    symbol: 'USDCUSDT', 
    side: 'BUY', 
    status: { \$in: ['NEW', 'SUBMITTED'] }
  })
"
```

### 场景2: 价格下跌 🔽

**模拟过程**:
```
1.0005 (初始) -> 1.0003 (下跌) -> 1.0002 (继续下跌)
```

**预期行为**:
- ✅ `maxBuyPrice` 成功更新
- ✅ 缓存自动清空
- ⚠️ **撤销所有买单**（避免在不利价格成交）
- ✅ 新买单使用更保守的价格

**验证点**:
```bash
# 检查应用日志，确认撤单操作
grep -i "cancel.*order\|falling.*trend" logs/application.log

# 检查交易日志
grep -i "cancel" logs/trade.log

# 检查买单是否被撤销
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.orders.find({ 
    symbol: 'USDCUSDT', 
    status: 'CANCELED',
    updatedAt: { \$gte: new Date(Date.now() - 60000) }
  }).count()
"
```

**重要**: 
- 撤单操作由 `DynamicPriceAdjuster` 在趋势分析时执行
- 需要启动应用并有真实的价格数据触发趋势分析
- 单元测试只验证配置更新和缓存清空

### 场景3: 价格震荡 ↕️

**模拟过程**:
```
1.0002 -> 1.0004 (上涨) -> 1.0003 (回落) -> 1.0004 (再上涨)
```

**预期行为**:
- ✅ `maxBuyPrice` 跟随市场微调
- ✅ 上涨时不撤单
- ⚠️ 下跌时撤单
- ✅ 保持策略灵活性

---

## 关键代码逻辑

### StrategyService.updateMaxBuyPrice()

```java
// 更新maxBuyPrice并自动清空缓存
public void updateMaxBuyPrice(String configId, double newMaxBuyPrice) {
    Update update = new Update().set("maxBuyPrice", newMaxBuyPrice);
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(configId)),
        update,
        StrategyConfig.class
    );
    
    // 自动清空缓存
    StrategyConfig config = mongoTemplate.findById(configId, StrategyConfig.class);
    if (config != null) {
        String cacheKey = config.getSymbol() + "_" + config.getMode();
        cacheService.evict(cacheKey);
    }
}
```

### DynamicPriceAdjuster.adjustPrice()

```java
// 趋势分析和价格调整
private void adjustPrice(TrendResult trend, StrategyConfig config) {
    double currentMaxBuyPrice = config.getMaxBuyPrice();
    double newMaxBuyPrice = calculateNewMaxBuyPrice(trend);
    
    if (trend.getTrend() == Trend.FALLING && newMaxBuyPrice < currentMaxBuyPrice) {
        // 价格下跌：撤销所有买单
        cancelAllBuyOrders(config.getSymbol(), config.getMode());
    }
    
    // 更新配置（自动清空缓存）
    strategyService.updateMaxBuyPrice(config.getId(), newMaxBuyPrice);
}
```

### DynamicPriceAdjuster.cancelAllBuyOrders()

```java
// 撤销所有买单
private void cancelAllBuyOrders(String symbol, StrategyConfig.Mode mode) {
    List<Order> buyOrders = orderRepository.findBySymbolAndSideAndStatusIn(
        symbol, 
        Order.Side.BUY, 
        List.of(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED)
    );
    
    for (Order order : buyOrders) {
        try {
            // 调用币安API撤单
            binanceService.cancelOrder(symbol, order.getClientOrderId());
            
            // 更新本地订单状态
            order.setStatus(Order.OrderStatus.CANCELED);
            orderRepository.save(order);
            
            log.info("已撤销买单: {}", order.getClientOrderId());
        } catch (Exception e) {
            log.error("撤单失败: {}", order.getClientOrderId(), e);
        }
    }
}
```

---

## 验证清单

### 配置更新验证
- [ ] maxBuyPrice 成功更新到数据库
- [ ] 缓存已清空
- [ ] 重新加载配置读取到最新值
- [ ] 日志记录配置更新事件

### 价格上涨验证
- [ ] maxBuyPrice 提高到新价格
- [ ] 现有买单保持不变（不撤单）
- [ ] 新买单使用更新后的价格
- [ ] 日志显示趋势为 RISING

### 价格下跌验证
- [ ] maxBuyPrice 降低到新价格
- [ ] 所有买单被撤销（状态变为 CANCELED）
- [ ] 日志显示撤单操作
- [ ] 币安API调用成功
- [ ] 日志显示趋势为 FALLING

### 缓存验证
- [ ] updateMaxBuyPrice() 后缓存清空
- [ ] updateConfig() 后缓存清空
- [ ] clearCache() 手动清空成功
- [ ] 缓存大小 size() 正确

---

## 监控要点

### 应用日志 (logs/application.log)

```bash
# 监控价格调整
tail -f logs/application.log | grep "maxBuyPrice\|adjustPrice"

# 监控撤单操作
tail -f logs/application.log | grep -i "cancel.*order"

# 监控趋势分析
tail -f logs/application.log | grep "Trend.*FALLING\|RISING\|OSCILLATING"
```

### 交易日志 (logs/trade.log)

```bash
# 监控下单记录
tail -f logs/trade.log | grep "新建买单\|新建卖单"

# 监控撤单记录
tail -f logs/trade.log | grep "撤单"
```

### 数据库监控

```bash
# 实时监控配置变化
mongosh mongodb://localhost:27017/strategy_db --eval "
  while(true) {
    var config = db.strategy_config.findOne({ _id: 'USDCUSDT_TESTNET' });
    print(new Date().toISOString() + ' maxBuyPrice: ' + config.maxBuyPrice);
    sleep(5000);
  }
"
```

---

## 常见问题

### Q1: 价格下跌时为什么没有撤单？

**可能原因**:
1. DynamicPriceAdjuster 没有运行（需要启动应用）
2. 趋势分析未触发（需要足够的历史数据）
3. 价格变化幅度太小，未达到趋势判断阈值
4. 币安API调用失败

**排查方法**:
```bash
# 检查DynamicPriceAdjuster是否运行
grep "DynamicPriceAdjuster" logs/application.log

# 检查趋势分析结果
grep "Trend.*Result" logs/application.log

# 检查API调用
grep "binance.*cancel" logs/application.log
```

### Q2: 缓存没有清空？

**可能原因**:
1. 直接操作数据库而不是通过 StrategyService
2. 缓存配置错误
3. 应用没有重启

**解决方法**:
```java
// 方法1: 使用StrategyService（推荐，自动清空缓存）
strategyService.updateMaxBuyPrice("USDCUSDT_TESTNET", 1.0006);

// 方法2: 手动清空缓存
strategyService.clearCache("USDCUSDT");

// 方法3: 清空所有缓存
cacheService.evictAll();
```

### Q3: 单元测试运行失败？

**可能原因**:
1. MongoDB未启动
2. 测试配置不存在
3. 依赖注入失败

**解决方法**:
```bash
# 检查MongoDB
docker ps | grep mongo

# 检查配置
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.strategy_config.findOne({ _id: 'USDCUSDT_TESTNET' })
"

# 清理测试数据
mongosh mongodb://localhost:27017/strategy_db --eval "
  db.orders.deleteMany({ clientOrderId: /^TEST_/ });
  db.positions.deleteMany({ _id: /^TEST_/ });
"
```

---

## 下一步

1. **执行测试**
   ```bash
   # 运行单元测试
   ./gradlew test --tests PriceSimulationTest
   
   # 运行Shell脚本测试
   ./test-price-simulation.sh
   ```

2. **部署到测试环境**
   ```bash
   # 构建应用
   ./gradlew clean bootJar
   
   # 启动应用
   cd run && ./run.sh
   ```

3. **监控运行**
   ```bash
   # 监控日志
   tail -f logs/application.log logs/trade.log
   
   # 监控数据库
   mongosh mongodb://localhost:27017/strategy_db
   ```

4. **真实环境测试**
   - 等待真实价格变化
   - 观察策略行为
   - 验证撤单逻辑
   - 记录测试结果

---

## 总结

本测试指南涵盖了价格变化场景下的完整测试流程：

✅ **已完成**:
- 单元测试框架 (PriceSimulationTest.java)
- Shell脚本测试 (test-price-simulation.sh)
- 数据库迁移脚本 (run-migration.sh)
- 详细测试文档

⚠️ **需要验证**:
- 真实环境下的撤单逻辑
- 趋势分析触发条件
- API调用成功率
- 性能和稳定性

📝 **建议**:
- 先在测试网验证完整流程
- 监控日志和数据库变化
- 记录异常情况和改进点
- 逐步调优参数

---

**最后更新**: 2026-01-18

