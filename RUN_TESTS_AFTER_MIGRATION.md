# 测试配置迁移后的运行指南

## 📋 修改概述
测试类已从 YML 文件配置改为数据库配置，现在使用标准的 Spring 流程从数据库读取配置。

## 🚀 快速开始

### 1️⃣ 确保数据库配置存在

```bash
# 连接MongoDB并初始化配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

**预期输出**：
```
✅ Testnet config inserted successfully!

Testnet Configuration Summary:
  Symbol: USDCUSDT
  Mode: TESTNET
  Enabled: true
  Max Buy Price: 1.0005
  Testnet API URL: https://testnet.binance.vision
  Testnet API Key: J2rlMxM3JW...
```

### 2️⃣ 运行测试

```bash
# 方式1：运行单个测试
./gradlew test --tests TradingOperationsTest.test01_MarketData

# 方式2：运行所有测试
./gradlew test --tests TradingOperationsTest

# 方式3：使用交互式脚本
./run-trading-operations-test.sh
```

### 3️⃣ 验证配置加载

运行测试后，查看日志输出：

```
========================================
交易操作综合测试 - 初始化
========================================

--- 加载策略配置 ---
✅ 配置已加载: USDCUSDT_TESTNET
✅ 币安API服务已初始化: https://testnet.binance.vision
✅ 缓存已清空

📋 测试配置:
  - 配置ID: USDCUSDT_TESTNET
  - 交易对: USDCUSDT
  - 运行模式: TESTNET
  - API URL: https://testnet.binance.vision
  - 最高买入价: 1.0005
  - 最大买入金额: 15.0 USDT
========================================
```

如果看到以上输出，说明配置加载成功！

## ⚙️ 配置说明

### 配置存储位置
- **数据库**: `strategy_db`
- **集合**: `strategy_config`
- **文档ID**: `USDCUSDT_TESTNET` 或 `USDCUSDT_PRODUCTION`

### 配置结构
```javascript
{
  _id: "USDCUSDT_TESTNET",          // 配置ID = {symbol}_{mode}
  symbol: "USDCUSDT",                // 交易对
  mode: "TESTNET",                   // 运行模式
  enabled: true,                     // 是否启用
  
  // 测试网API配置（自动使用）
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "...",
  testnetSecretKey: "...",
  
  // 策略参数
  maxBuyPrice: 1.0005,               // 最高买入价
  maxBuyAmountUsdt: 15.0,            // 最大买入金额
  minProfitTick: 0.0001,             // 最小利润空间
  maxHoldSeconds: 1800,              // 最大持仓时间
  maxTotalInvestUsdt: 500.0,         // 最大总投入
  // ...
}
```

### 测试类默认配置
```java
// 在 TradingOperationsTest.java 中
private String testSymbol = "USDCUSDT";  // 测试交易对
private String testMode = "TESTNET";     // 测试运行模式
```

## 🔄 切换测试环境

### 方法1：修改测试类（推荐）
编辑 `src/test/java/com/zq/TradingOperationsTest.java`：

```java
// 切换到生产网
private String testSymbol = "USDCUSDT";
private String testMode = "PRODUCTION";  // 改为 PRODUCTION

// 或切换到其他交易对
private String testSymbol = "BTCUSDT";
private String testMode = "TESTNET";
```

### 方法2：添加新的配置
在数据库中添加其他交易对的配置：

```javascript
db.strategy_config.insertOne({
  _id: "BTCUSDT_TESTNET",
  symbol: "BTCUSDT",
  mode: "TESTNET",
  // ... 其他配置
});
```

## 🛠️ 故障排查

### ❌ 错误1：找不到配置
```
错误: 未找到配置: USDCUSDT_TESTNET
请确保数据库中存在该配置，或修改 testSymbol 和 testMode
```

**原因**：数据库中不存在对应的配置文档

**解决方法**：
```bash
# 重新初始化数据库配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### ❌ 错误2：API配置不完整
```
错误: API配置不完整，请检查数据库配置:
  - API URL: https://testnet.binance.vision
  - API Key: 未配置
  - Secret Key: 未配置
```

**原因**：数据库配置中缺少 API 密钥

**解决方法**：
```javascript
// 连接MongoDB
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin

// 更新配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: {
      testnetApiUrl: "https://testnet.binance.vision",
      testnetApiKey: "你的API Key",
      testnetSecretKey: "你的Secret Key"
    }
  }
)
```

### ❌ 错误3：MongoDB连接失败
```
错误: Unable to connect to MongoDB
```

**原因**：MongoDB服务未启动

**解决方法**：
```bash
# 检查MongoDB状态
docker ps | grep mongo

# 启动MongoDB
cd run && docker-compose up -d

# 等待几秒后重试
```

### ❌ 错误4：Spring注入失败
```
错误: Could not resolve placeholder 'binance.testnet.api-key'
```

**原因**：代码中还有旧的 `@Value` 注解

**解决方法**：
- 这个错误已经修复
- 如果仍然出现，请确认使用的是最新版本的测试类
- 重新编译：`./gradlew clean compileTestJava`

## 📊 测试功能列表

测试类提供以下测试功能：

| 测试编号 | 测试名称 | 功能说明 |
|---------|---------|---------|
| 测试1 | 行情查询 | 获取实时价格和深度数据 |
| 测试2 | 账户余额查询 | 查询USDT和交易币种余额 |
| 测试3 | 下单操作 | 测试限价买单和卖单 |
| 测试4 | 挂单查询 | 查询当前所有挂单 |
| 测试5 | 撤单操作 | 撤销单个订单 |
| 测试6 | 批量撤单 | 一次性撤销多个订单 |
| 测试7 | 全部撤单 | 撤销指定交易对的所有挂单 |
| 测试8 | 配置读取 | 验证从数据库读取策略配置 |
| 测试9 | 综合测试 | 完整的交易流程测试 |
| 测试10 | 持仓查询 | 查询当前持仓信息 |
| 测试11 | 历史订单查询 | 查询所有历史订单 |
| 测试12 | 盈亏统计 | 计算已实现和未实现盈亏 |

## 🔍 验证配置的方法

### 方法1：查看数据库
```javascript
// 连接MongoDB
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin

// 查询配置
db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" })

// 应该看到完整的配置，包括 testnetApiKey 等字段
```

### 方法2：运行测试8
```bash
# 运行配置读取测试
./gradlew test --tests TradingOperationsTest.test08_ConfigurationLoading

# 查看输出，应该显示：
# ✅ 配置已加载:
#   - 交易对: USDCUSDT
#   - 运行模式: TESTNET
#   - 参考价格: 1.0
#   - 最高买入价: 1.0005
#   ...
```

### 方法3：查看测试日志
```bash
# 运行测试并查看详细日志
./gradlew test --tests TradingOperationsTest --info

# 或者查看日志文件
tail -f logs/application.log
```

## ✅ 迁移检查清单

迁移完成后，请确认以下事项：

- [ ] MongoDB服务正常运行
- [ ] 数据库中存在 `USDCUSDT_TESTNET` 配置
- [ ] 配置包含完整的 API 密钥（testnetApiKey, testnetSecretKey）
- [ ] `application-test.yml` 中不再有 `binance.testnet.api-key` 配置
- [ ] 测试类中不再使用 `@Value` 注解读取 API 配置
- [ ] 测试能够成功启动并加载配置
- [ ] 至少一个测试能够成功运行

## 📚 相关文档

- [TEST_CONFIG_MIGRATION.md](TEST_CONFIG_MIGRATION.md) - 详细的迁移说明
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - 实施总结
- [TRADING_OPERATIONS_TEST_GUIDE.md](TRADING_OPERATIONS_TEST_GUIDE.md) - 测试使用指南
- [doc/db/init-strategy-config.js](doc/db/init-strategy-config.js) - 数据库初始化脚本

## 💡 提示

1. **首次运行前必须初始化数据库配置**
2. **配置修改后无需重启，会自动从数据库读取最新配置**
3. **测试网API密钥已包含在初始化脚本中，可以直接使用**
4. **生产网API密钥需要手动配置**

---

**最后更新**: 2026-01-18  
**适用版本**: 修改后的测试类  
**支持**: 如有问题，请参考 TEST_CONFIG_MIGRATION.md 或 IMPLEMENTATION_SUMMARY.md

