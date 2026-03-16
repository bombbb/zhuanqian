# 启动前检查清单

在启动项目进行实际交易前，请逐项检查以下内容。

## 📋 系统检查

### 1. 环境检查
- [ ] Java 21 已安装并配置正确
  ```bash
  java -version  # 应显示 Java 21
  ```
- [ ] Docker 已安装并运行
  ```bash
  docker --version
  docker ps
  ```
- [ ] MongoDB 已启动
  ```bash
  docker ps | grep mongo
  ```

### 2. 数据库检查
- [ ] 数据库名称已改为 `strategy_db`
  ```bash
  mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
  > show dbs  # 应该看到 strategy_db
  ```
- [ ] 策略配置已初始化
  ```bash
  > db.strategy_config.findOne({ symbol: "USDCUSDT" })
  # 应该返回完整配置，包含 marketDataWsUrl 字段
  ```
- [ ] 索引已创建
  ```bash
  > db.strategy_config.getIndexes()
  > db.orders.getIndexes()
  > db.positions.getIndexes()
  ```

### 3. 配置检查
- [ ] 策略配置正确
  - symbol: USDCUSDT ✓
  - mode: TESTNET/PRODUCTION ✓
  - enabled: true ✓
  - basePrice: 1.0005 ✓
  - minProfitTick: 0.0001 ✓
  - maxHoldSeconds: 1800 ✓
  - tradeAmountUsdt: 100.0 ✓

- [ ] API配置正确
  - testnetApiUrl: https://testnet.binance.vision ✓
  - testnetApiKey: 已配置 ✓
  - testnetSecretKey: 已配置 ✓
  - marketDataWsUrl: wss://stream.binance.com:9443 ✓

- [ ] 日志配置正确
  - 行情实时日志已禁用 ✓
  - 下单日志使用TRADE logger ✓
  - 撤单日志使用TRADE logger ✓

## 🧪 测试检查

### 4. 单元测试
- [ ] 所有测试通过
  ```bash
  ./gradlew test
  ```
- [ ] 测试报告无错误
  ```bash
  open build/reports/tests/test/index.html
  ```

### 5. 集成测试
运行以下命令并确保所有测试通过：
```bash
./gradlew test --tests StrategyIntegrationTest
```

- [ ] test1_StrategyConfigLoaded - 配置加载 ✓
- [ ] test2_ApiServiceInitialized - API服务 ✓
- [ ] test3_MarketDataProcessing - 行情处理 ✓
- [ ] test4_BuySignalDetection - 买入信号 ✓
- [ ] test5_OrderCreationAndQuery - 订单管理 ✓
- [ ] test6_PositionUpdate - 持仓更新 ✓
- [ ] test7_SellOrderCreation - 卖出订单 ✓
- [ ] test8_OrderCancellation - 订单撤销 ✓
- [ ] test9_CompleteTradeFlow - 完整流程 ✓
- [ ] test10_StatisticsRecording - 统计记录 ✓

### 6. API连接测试
- [ ] 能够获取服务器时间
  ```bash
  # 在测试中验证
  ```
- [ ] 能够查询账户信息（如果使用真实API）
- [ ] WebSocket能够连接并接收行情数据

## 🔧 功能检查

### 7. 核心功能验证
- [ ] 行情数据接收正常
- [ ] 买入信号判断正确
- [ ] 订单创建和提交正常
- [ ] 持仓计算准确
- [ ] 订单监控和撤单正常
- [ ] 统计数据记录正常

### 8. 日志检查
- [ ] 日志文件可以正常创建
  ```bash
  ls -la logs/
  # 应该看到 application.log, trade.log, price.log
  ```
- [ ] 日志格式正确
  ```bash
  tail -f logs/application.log
  tail -f logs/trade.log
  ```
- [ ] 下单日志包含必要信息
  - 订单ID ✓
  - 本地ID ✓
  - 价格 ✓
  - 数量 ✓
  - 模式 ✓

## 🔐 安全检查

### 9. API密钥安全
- [ ] 测试网密钥已配置（用于测试）
- [ ] 正式网密钥未硬编码在代码中
- [ ] 正式网密钥通过环境变量或安全配置管理
- [ ] 密钥权限最小化（只开启必要的权限）

### 10. 风险控制
- [ ] 单笔交易金额合理（建议不超过总资金的30%）
- [ ] 最大持仓时间设置合理（建议30分钟）
- [ ] 最小利润设置合理（建议0.01%以上）
- [ ] 支撑比率阈值合理（建议0.6以上）

## 📊 监控准备

### 11. 监控工具
- [ ] 日志监控命令准备好
  ```bash
  tail -f logs/application.log
  tail -f logs/trade.log
  ```
- [ ] 数据库监控命令准备好
  ```bash
  mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
  ```
- [ ] 订单查询命令准备好
  ```bash
  > db.orders.find().sort({createTime: -1}).limit(10).pretty()
  ```
- [ ] 持仓查询命令准备好
  ```bash
  > db.positions.find().pretty()
  ```

### 12. 告警设置
- [ ] 设置异常告警（可选）
- [ ] 设置资金告警（可选）
- [ ] 设置订单超时告警（可选）

## 🚀 启动准备

### 13. 启动前最后检查
- [ ] 确认运行模式（TESTNET/PRODUCTION）
- [ ] 确认策略已启用（enabled: true）
- [ ] 确认资金充足
- [ ] 确认网络稳定
- [ ] 确认有足够的磁盘空间（日志文件）

### 14. 启动步骤
1. [ ] 清理旧数据（如需要）
   ```bash
   mongosh ... --eval "db.orders.deleteMany({}); db.positions.deleteMany({});"
   ```

2. [ ] 启动应用
   ```bash
   ./gradlew bootRun
   ```

3. [ ] 检查启动日志
   ```bash
   tail -f logs/application.log
   # 应该看到：
   # - StrategyEngine initialized
   # - BinanceApiService initialized
   # - WebSocket connection established
   ```

4. [ ] 验证WebSocket连接
   ```bash
   # 在日志中查找：
   # "WebSocket connection established successfully"
   ```

5. [ ] 监控首次交易
   ```bash
   tail -f logs/trade.log
   # 等待第一笔交易日志
   ```

## 📝 启动后验证

### 15. 运行时检查（启动后5分钟内）
- [ ] WebSocket连接稳定
- [ ] 行情数据正常接收
- [ ] 没有异常错误日志
- [ ] 数据库连接正常

### 16. 运行时检查（启动后30分钟内）
- [ ] 如果有交易信号，订单正常创建
- [ ] 订单状态正常更新
- [ ] 持仓数据正确
- [ ] 统计数据正常记录

## ⚠️ 紧急情况处理

### 17. 紧急停止
如果发现异常，立即执行：
```bash
# 停止应用
Ctrl+C

# 或者找到进程并kill
ps aux | grep java
kill -9 <PID>
```

### 18. 问题排查
- [ ] 查看错误日志
  ```bash
  grep ERROR logs/application.log
  ```
- [ ] 查看交易日志
  ```bash
  tail -100 logs/trade.log
  ```
- [ ] 查看订单状态
  ```bash
  mongosh ... --eval "db.orders.find({status: {$in: ['NEW', 'SUBMITTED']}}).pretty()"
  ```
- [ ] 查看持仓
  ```bash
  mongosh ... --eval "db.positions.find().pretty()"
  ```

## ✅ 最终确认

在启动前，请再次确认：

- [ ] 我已经阅读并理解了策略逻辑
- [ ] 我已经在测试网充分测试
- [ ] 我已经检查了所有配置
- [ ] 我已经准备好监控工具
- [ ] 我了解如何紧急停止系统
- [ ] 我接受交易风险

---

**签名**: ________________  
**日期**: ________________  
**模式**: [ ] TESTNET  [ ] PRODUCTION

---

## 🎯 快速启动命令

如果所有检查都通过，使用以下命令快速启动：

```bash
# 1. 快速测试（可选，但强烈建议）
./quick-test.sh

# 2. 启动应用
./gradlew bootRun

# 3. 监控日志（新终端）
tail -f logs/application.log

# 4. 监控交易（新终端）
tail -f logs/trade.log
```

**祝交易顺利！** 🚀

