# 交易操作测试指南

## 📍 测试类位置

**文件路径**: `src/test/java/com/zq/TradingOperationsTest.java`

这是一个综合的交易操作测试类，整合了之前分散在不同测试类中的功能。

---

## 🎯 测试功能概览

### 1. 测试1: 行情查询 (`test01_MarketData`)
- 获取当前价格
- 获取深度数据（Book Ticker）
- 最佳买卖价查询
- 价差计算
- 价格合理性验证

### 2. 测试2: 账户余额查询 (`test02_AccountBalance`)
- 查询USDT余额
- 查询交易币种余额（如USDC）
- 可用/锁定余额分类
- 余额充足性检查

### 3. 测试3: 下单操作 (`test03_PlaceOrders`)
- 限价买单
- 限价卖单
- 订单参数验证
- 订单状态查询

### 4. 测试4: 挂单查询 (`test04_QueryOpenOrders`)
- 查询指定交易对挂单
- 查询所有交易对挂单
- 挂单列表展示
- 挂单数量统计

### 5. 测试5: 撤单操作 (`test05_CancelOrder`)
- 单个订单撤销
- 订单状态查询
- 撤单结果验证

### 6. 测试6: 批量撤单 (`test06_BatchCancelOrders`)
- 批量下单
- 批量撤销
- 撤单前后对比
- 成功/失败统计

### 7. 测试7: 全部撤单 (`test07_CancelAllOrders`)
- 获取所有挂单
- 全部撤销
- 挂单清空验证

### 8. 测试8: 配置读取 (`test08_ConfigurationLoading`)
- 从数据库读取策略配置
- 配置参数验证
- 缓存功能测试

### 9. 测试9: 综合测试 (`test09_CompleteTrading`)
- 完整交易流程
- 配置加载 → 查询余额 → 获取行情 → 下单 → 查询挂单 → 撤单 → 验证

---

## 🔧 配置说明

### 测试配置文件
**位置**: `src/test/resources/application-test.yml`

```yaml
# 币安API配置（测试网）
binance:
  testnet:
    api-url: https://testnet.binance.vision
    api-key: J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k
    secret-key: HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V

# 测试参数配置
test:
  symbol: USDCUSDT  # 测试交易对
  mode: TESTNET     # 运行模式
```

### 配置参数说明
- `binance.testnet.api-url`: 币安测试网API地址
- `binance.testnet.api-key`: 测试网API Key
- `binance.testnet.secret-key`: 测试网Secret Key
- `test.symbol`: 测试使用的交易对（默认USDCUSDT）
- `test.mode`: 测试模式（TESTNET/PRODUCTION）

---

## 🚀 运行测试

### 方法1: 使用测试脚本（推荐）

```bash
# 运行全部测试
./run-trading-tests.sh

# 运行单个测试
./run-trading-tests.sh test01_MarketData           # 行情查询
./run-trading-tests.sh test02_AccountBalance       # 账户余额
./run-trading-tests.sh test03_PlaceOrders          # 下单测试
./run-trading-tests.sh test04_QueryOpenOrders      # 挂单查询
./run-trading-tests.sh test05_CancelOrder          # 撤单测试
./run-trading-tests.sh test06_BatchCancelOrders    # 批量撤单
./run-trading-tests.sh test07_CancelAllOrders      # 全部撤单
./run-trading-tests.sh test08_ConfigurationLoading # 配置读取
./run-trading-tests.sh test09_CompleteTrading      # 综合测试
```

### 方法2: 使用Gradle命令

```bash
# 运行全部测试
./gradlew test --tests TradingOperationsTest

# 运行单个测试
./gradlew test --tests TradingOperationsTest.test01_MarketData

# 查看详细输出
./gradlew test --tests TradingOperationsTest --info
```

### 方法3: 使用IDE
1. 在IDE中打开 `src/test/java/com/zq/TradingOperationsTest.java`
2. 右键点击类名或方法名
3. 选择 "Run Test" 或 "Debug Test"

---

## ⚙️ 运行前准备

### 1. 启动MongoDB
```bash
cd run && docker-compose up -d
```

### 2. 初始化配置（如果尚未初始化）
```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### 3. 检查测试网账户余额
- 访问 [币安测试网](https://testnet.binance.vision/)
- 使用测试API Key登录
- 确保USDT余额 ≥ 10 USDT（用于下单测试）

### 4. 获取测试网代币（如果余额不足）
- 访问 [币安测试网充值页面](https://testnet.binance.vision/)
- 点击"Get Test Funds"获取测试代币

---

## 📊 测试结果查看

### 控制台输出
测试运行时会在控制台输出详细的日志信息：
- ✅ 表示成功
- ❌ 表示失败
- ⚠️ 表示警告
- 💡 表示提示

### 示例输出
```
========================================
测试1: 行情查询
========================================

--- 1.1 获取当前价格 ---
✅ 当前价格: 1.0002 (USDCUSDT)

--- 1.2 获取深度数据 (Book Ticker) ---
✅ 深度数据:
  - 最佳买价(Bid): 1.0001 (数量: 1000)
  - 最佳卖价(Ask): 1.0003 (数量: 1500)
  - 价差(Spread): 0.0002

✅ 价格合理性验证通过

✅ 测试1通过: 行情查询正常
```

---

## 🔍 与其他测试类的关系

### 原有测试类
1. **BinanceApiServiceTest** (`src/test/java/com/zq/api/BinanceApiServiceTest.java`)
   - 专注于币安API的单元测试
   - 测试单个API方法的功能

2. **PriceSimulationTest** (`src/test/java/com/zq/PriceSimulationTest.java`)
   - 专注于价格模拟和策略测试
   - 测试价格变化对策略的影响

### TradingOperationsTest（本测试类）
- **定位**: 综合性交易操作测试
- **特点**: 
  - 整合了行情、下单、撤单等所有交易操作
  - 自动读取配置文件
  - 包含完整的交易流程测试
  - 提供详细的测试日志
  - 自动清理测试数据

### 建议使用场景
- 日常功能测试 → 使用 `TradingOperationsTest`
- API单元测试 → 使用 `BinanceApiServiceTest`
- 策略模拟测试 → 使用 `PriceSimulationTest`

---

## ⚠️ 注意事项

### 1. 测试环境
- ✅ **使用测试网**: 所有测试默认使用币安测试网，不会影响真实资金
- ❌ **不要在生产环境运行**: 避免在生产配置下运行测试

### 2. 测试数据清理
- 所有测试都会在结束时自动撤销创建的订单
- 如果测试异常中断，可能需要手动清理挂单
- 清理命令: `./run-trading-tests.sh test07_CancelAllOrders`

### 3. 并发测试
- 不建议同时运行多个测试实例
- 可能导致订单状态冲突
- 建议串行运行测试

### 4. 网络要求
- 需要连接到币安测试网
- 确保网络稳定
- 如遇超时，可以重试

### 5. 余额要求
- USDT余额 ≥ 10 USDT（推荐 ≥ 50 USDT）
- 交易币种余额 ≥ 20 个（用于卖单测试）

---

## 🐛 常见问题

### Q1: 测试失败，提示"配置不应为空"
**原因**: 数据库中没有配置数据  
**解决**: 运行初始化脚本
```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### Q2: 测试失败，提示"余额不足"
**原因**: 测试网账户余额不足  
**解决**: 访问币安测试网获取测试代币

### Q3: 测试失败，提示"MongoDB连接失败"
**原因**: MongoDB未运行  
**解决**: 
```bash
cd run && docker-compose up -d
```

### Q4: 测试卡住不动
**原因**: 网络连接问题或API超时  
**解决**: 
- 检查网络连接
- 重新运行测试
- 增加超时时间

### Q5: 测试后有残留订单
**原因**: 测试异常中断，清理逻辑未执行  
**解决**: 
```bash
./run-trading-tests.sh test07_CancelAllOrders
```

---

## 📝 测试扩展

### 添加新的测试方法
在 `TradingOperationsTest.java` 中添加：

```java
/**
 * 测试10: 自定义测试
 */
@Test
public void test10_CustomTest() throws Exception {
    log.info("\n========================================");
    log.info("测试10: 自定义测试");
    log.info("========================================");
    
    // 你的测试代码
    
    log.info("\n✅ 测试10通过");
}
```

### 修改测试参数
编辑 `src/test/resources/application-test.yml`:

```yaml
test:
  symbol: TUSDUSDT  # 修改为其他交易对
  mode: TESTNET     # 保持测试网模式
```

---

## 📚 相关文档

- [快速开始指南](QUICK_START.md)
- [测试指南](TEST_GUIDE.md)
- [价格模拟测试指南](PRICE_SIMULATION_GUIDE.md)
- [策略指南](STRATEGY_GUIDE.md)

---

## 💬 技术支持

如有问题，请查看：
1. 项目README.md
2. 相关文档（见上方）
3. 日志文件: `logs/application.log`

---

**最后更新**: 2026-01-18  
**维护者**: zhuanqian

