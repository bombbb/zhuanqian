# 交易测试快速参考

## 🚀 快速开始

```bash
# 最简单的方式 - 直接运行脚本
./run-trading-operations-test.sh
```

---

## 📋 可用测试

| 编号 | 测试名称 | 功能说明 |
|------|----------|----------|
| 1 | 行情查询 | 获取实时价格、深度数据 |
| 2 | 账户余额查询 | 查询USDT和币种余额 |
| 3 | 下单操作 | 测试限价买单和卖单 |
| 4 | 挂单查询 | 查询当前所有挂单 |
| 5 | 撤单操作 | 撤销单个订单 |
| 6 | 批量撤单 | 一次撤销多个订单 |
| 7 | 全部撤单 | 撤销所有挂单 |
| 8 | 配置读取 | 验证策略配置 |
| 9 | 综合测试 | 完整交易流程 |
| 10 | 持仓查询 | 查询当前持仓 ⭐ |
| 11 | 历史订单查询 | 查询所有历史订单 ⭐ |
| 12 | 盈亏统计 | 计算盈亏和收益率 ⭐ |

⭐ = 新增功能

---

## 💰 盈亏统计

### 快速查看盈亏
```bash
# 方式1: 运行脚本，选择菜单12
./run-trading-operations-test.sh
# 然后输入: 12

# 方式2: 直接运行单个测试
./gradlew test --tests TradingOperationsTest.test12_ProfitLossStatistics
```

### 统计内容
- ✅ **已实现盈亏**: 已成交订单的盈亏
- ✅ **未实现盈亏**: 当前持仓的盈亏  
- ✅ **总盈亏**: 已实现 + 未实现
- ✅ **收益率**: 各项收益率百分比

---

## 📊 持仓查询

### 快速查看持仓
```bash
# 方式1: 运行脚本，选择菜单10
./run-trading-operations-test.sh
# 然后输入: 10

# 方式2: 直接运行
./gradlew test --tests TradingOperationsTest.test10_QueryPosition
```

### 显示内容
- 持仓数量
- 平均买入价
- 已投入金额
- 当前价格
- 未实现盈亏

---

## 📜 历史订单

### 快速查看历史
```bash
# 方式1: 运行脚本，选择菜单11
./run-trading-operations-test.sh
# 然后输入: 11

# 方式2: 直接运行
./gradlew test --tests TradingOperationsTest.test11_QueryHistoricalOrders
```

### 显示内容
- 订单总数
- 状态分布
- 最近订单列表

---

## 🛠️ 运行方式对比

| 方式 | 命令 | 优点 | 缺点 |
|------|------|------|------|
| **脚本运行** | `./run-trading-operations-test.sh` | 最简单，交互式菜单 | 需要编译 |
| **Gradle全部** | `./gradlew test --tests TradingOperationsTest` | 运行所有测试 | 较慢 |
| **Gradle单个** | `./gradlew test --tests TradingOperationsTest.test12_ProfitLossStatistics` | 只运行指定测试 | 命令较长 |
| **直接运行** | `./run-trading-test-direct.sh` | 不依赖Gradle | 实验性 |

---

## ⚙️ 配置

### 测试配置文件
`src/test/resources/application-test.yml`

```yaml
test:
  symbol: USDCUSDT    # 测试交易对
  mode: TESTNET       # TESTNET或PRODUCTION

binance:
  testnet:
    api-url: https://testnet.binance.vision
    api-key: your-api-key
    secret-key: your-secret-key
```

### 环境变量
```bash
export BINANCE_TESTNET_API_KEY="your-key"
export BINANCE_TESTNET_SECRET_KEY="your-secret"
```

---

## 🔍 常用命令

### 编译项目
```bash
./gradlew clean bootJar compileTestJava
```

### 运行所有测试
```bash
./gradlew test --tests TradingOperationsTest
```

### 运行单个测试
```bash
# 盈亏统计
./gradlew test --tests TradingOperationsTest.test12_ProfitLossStatistics

# 持仓查询
./gradlew test --tests TradingOperationsTest.test10_QueryPosition

# 历史订单
./gradlew test --tests TradingOperationsTest.test11_QueryHistoricalOrders

# 行情查询
./gradlew test --tests TradingOperationsTest.test01_MarketData

# 账户余额
./gradlew test --tests TradingOperationsTest.test02_AccountBalance
```

### 查看日志
```bash
tail -f logs/application.log
```

---

## ⚠️ 注意事项

### 运行前检查
- ✅ Java 21已安装: `java -version`
- ✅ MongoDB已运行
- ✅ API配置正确
- ✅ 网络连接正常

### 测试环境
- 🔐 默认使用**测试网**
- 💰 需要测试网账户余额
- 🔑 需要测试网API Key

### 安全提示
- ⚠️ 所有订单都是限价单
- ⚠️ 测试结束自动撤单
- ⚠️ 不会影响生产环境

---

## 📈 盈亏统计示例

```
✅ 盈亏统计结果:
==================== 已实现盈亏 ====================
  - 已成交买单数量: 15
  - 已成交卖单数量: 12
  - 总买入金额: 150.23 USDT
  - 总卖出金额: 152.89 USDT
  - 已实现盈亏: 2.66 USDT
  - 已实现收益率: 1.77%

==================== 未实现盈亏 ====================
  - 当前持仓数量: 30.0 USDC
  - 平均买入价: 0.9998
  - 当前价格: 1.0002
  - 持仓成本: 29.99 USDT
  - 未实现盈亏: 0.12 USDT
  - 未实现收益率: 0.40%

==================== 总盈亏 ====================
  - 总盈亏: 2.78 USDT
  - 总收益率: 1.85%
```

---

## 🆘 常见问题

### Q: 编译失败？
```bash
# 检查Java版本
java -version  # 应该是Java 21+

# 清理后重新编译
./gradlew clean build
```

### Q: 无法连接API？
- 检查网络连接
- 检查API Key是否正确
- 检查使用的是测试网还是生产环境

### Q: 盈亏统计为0？
- 确保有历史成交订单
- 检查MongoDB中是否有订单数据
- 确认交易对和模式匹配

### Q: 无法查询持仓？
- 确保MongoDB运行正常
- 确保有买入订单成交
- 检查数据库中Position集合

---

## 📚 完整文档

- [完整使用指南](TRADING_OPERATIONS_TEST_GUIDE.md)
- [改进总结](TRADING_TEST_IMPROVEMENTS.md)
- [实施总结](IMPLEMENTATION_SUMMARY.md)

---

## 🎯 推荐工作流程

### 首次使用
```bash
# 1. 检查环境
java -version
mongosh --eval "db.version()"

# 2. 配置API Key
vi src/test/resources/application-test.yml

# 3. 运行测试
./run-trading-operations-test.sh

# 4. 选择测试
# 输入 2 - 检查余额
# 输入 1 - 检查行情
# 输入 12 - 查看盈亏
```

### 日常使用
```bash
# 快速查看盈亏
./run-trading-operations-test.sh
# 输入: 12

# 快速查看持仓
./run-trading-operations-test.sh
# 输入: 10
```

---

**祝测试顺利！** 🎉

