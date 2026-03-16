# 套利策略测试指南

## 测试前准备

### 1. 启动MongoDB
```bash
cd run
docker-compose up -d
```

### 2. 初始化策略配置
```bash
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### 3. 验证配置
```bash
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
```

在MongoDB shell中执行：
```javascript
db.strategy_config.findOne({ symbol: "USDCUSDT" })
```

应该看到完整的策略配置，包括新增的 `marketDataWsUrl` 字段。

## 运行测试

### 方式1：运行所有测试
```bash
./gradlew test
```

### 方式2：运行特定测试类
```bash
# 运行策略集成测试
./gradlew test --tests StrategyIntegrationTest

# 运行持仓服务测试
./gradlew test --tests PositionServiceTest

# 运行订单服务测试
./gradlew test --tests OrderServiceTest

# 运行统计服务测试
./gradlew test --tests StatsServiceTest

# 运行API服务测试
./gradlew test --tests BinanceApiServiceTest
```

### 方式3：运行单个测试方法
```bash
./gradlew test --tests StrategyIntegrationTest.test1_StrategyConfigLoaded
./gradlew test --tests StrategyIntegrationTest.test9_CompleteTradeFlow
```

## 测试覆盖范围

### StrategyIntegrationTest - 策略集成测试
测试完整的套利策略流程：

1. **test1_StrategyConfigLoaded** - 验证策略配置加载
   - 检查配置是否正确加载
   - 验证关键参数（basePrice, minProfitTick等）

2. **test2_ApiServiceInitialized** - 验证API服务初始化
   - 检查API服务是否正常
   - 测试获取服务器时间

3. **test3_MarketDataProcessing** - 模拟行情数据处理
   - 测试行情数据接收和处理
   - 验证价差统计记录

4. **test4_BuySignalDetection** - 测试买入信号判断
   - 模拟价格低于basePrice的情况
   - 验证是否触发买入信号

5. **test5_OrderCreationAndQuery** - 测试订单创建和查询
   - 创建测试订单
   - 验证订单查询功能

6. **test6_PositionUpdate** - 测试持仓更新
   - 模拟买入操作
   - 验证持仓数据和可用资金

7. **test7_SellOrderCreation** - 测试卖出订单创建
   - 创建卖出订单
   - 验证买卖单关联

8. **test8_OrderCancellation** - 测试订单撤销
   - 模拟订单过期
   - 验证撤单功能

9. **test9_CompleteTradeFlow** - 测试完整交易流程
   - 买入 -> 卖出 -> 验证资金
   - 完整的套利循环

10. **test10_StatisticsRecording** - 测试统计数据记录
    - 记录价差统计
    - 记录深度统计

## 查看测试报告

测试完成后，查看HTML报告：
```bash
open build/reports/tests/test/index.html
```

## 重要变更说明

### 1. 数据库名称变更
- **原来**: `trading`
- **现在**: `strategy_db`
- **影响**: 所有MongoDB连接配置已更新

### 2. 日志配置优化
- **行情日志**: 不再实时打印，减少日志噪音
- **交易日志**: 下单和撤单时使用TRADE logger打印详细信息
- **日志格式**: 包含订单ID、价格、数量、模式等关键信息

### 3. WebSocket URL配置
- **测试网模式**: 交易使用测试网API，行情使用正式网WebSocket
- **正式网模式**: 交易和行情都使用正式网
- **配置字段**: `marketDataWsUrl` - 默认值 `wss://stream.binance.com:9443`

### 4. API URL获取逻辑
```java
// 交易API：根据mode选择
switch (config.getMode()) {
    case TESTNET:
        baseUrl = config.getTestnetApiUrl();  // https://testnet.binance.vision
        break;
    case PRODUCTION:
        baseUrl = config.getProductionApiUrl();  // https://api.binance.com
        break;
}

// 行情WebSocket：统一使用正式网
String wsUrl = config.getMarketDataWsUrl();  // wss://stream.binance.com:9443
```

## 测试注意事项

### 1. 测试网API限制
- 测试网可能不稳定，部分测试可能失败
- 如果测试失败，检查测试网是否可用

### 2. 异步操作
- 测试中使用 `Thread.sleep()` 等待异步操作完成
- 如果测试失败，可能需要增加等待时间

### 3. 数据清理
- 每个测试前后都会清理数据
- 确保测试之间互不影响

### 4. MongoDB连接
- 确保MongoDB正在运行
- 检查连接字符串是否正确

## 常见问题

### Q1: 测试失败 - MongoDB连接错误
**解决方案**:
```bash
# 检查MongoDB是否运行
docker ps | grep mongo

# 如果没有运行，启动MongoDB
cd run && docker-compose up -d
```

### Q2: 测试失败 - 配置未找到
**解决方案**:
```bash
# 重新初始化配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### Q3: Java环境问题
**解决方案**:
```bash
# 检查Java版本（需要Java 21）
java -version

# 如果版本不对，安装Java 21
# macOS: brew install openjdk@21
```

## 下一步

测试通过后，可以：

1. **启动应用**: 
   ```bash
   ./gradlew bootRun
   ```

2. **监控日志**:
   ```bash
   tail -f logs/application.log  # 主日志
   tail -f logs/trade.log        # 交易日志
   tail -f logs/price.log        # 价格日志（已禁用实时打印）
   ```

3. **查看数据**:
   ```bash
   mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
   
   # 查看订单
   db.orders.find().pretty()
   
   # 查看持仓
   db.positions.find().pretty()
   
   # 查看统计
   db.spread_stats.find().limit(10).pretty()
   ```

## 测试清单

在启动正式交易前，确保以下测试都通过：

- [ ] test1_StrategyConfigLoaded - 配置加载
- [ ] test2_ApiServiceInitialized - API服务
- [ ] test3_MarketDataProcessing - 行情处理
- [ ] test4_BuySignalDetection - 买入信号
- [ ] test5_OrderCreationAndQuery - 订单管理
- [ ] test6_PositionUpdate - 持仓更新
- [ ] test7_SellOrderCreation - 卖出订单
- [ ] test8_OrderCancellation - 订单撤销
- [ ] test9_CompleteTradeFlow - 完整流程
- [ ] test10_StatisticsRecording - 统计记录

全部通过后，系统已准备好进行实际交易！🚀

