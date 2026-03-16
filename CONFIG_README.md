# 配置说明

## ⚠️ 重要：配置完全从 MongoDB 读取

本项目**不再使用 YAML 配置文件**（如 `usdc_strategy.yaml`），所有策略配置均从 MongoDB 数据库读取。

## 配置架构

```
MongoDB (strategy_config 集合)
    ↓
StrategyService (带缓存)
    ↓
StrategyEngine / BinanceApiService / 其他服务
```

## 配置初始化

### 1. 启动 MongoDB

```bash
cd run
docker-compose up -d
```

### 2. 初始化策略配置

使用 MongoDB 初始化脚本：

```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

或者手动连接 MongoDB：

```bash
mongosh mongodb://localhost:27017/strategy_db
```

然后执行脚本内容：`doc/db/init-strategy-config.js`

## 配置结构

### 测试网配置 (TESTNET)

```javascript
{
  _id: "USDCUSDT_TESTNET",
  symbol: "USDCUSDT",
  mode: "TESTNET",           // 运行模式：SIMULATION / TESTNET / PRODUCTION
  enabled: true,             // 是否启用
  
  // 核心参数
  referencePrice: 1.0,       // 参考价格（锚定价）
  maxBuyPrice: 1.0005,       // 最高买入价
  maxBuyAmountUsdt: 15.0,    // 单次最大买入金额
  minProfitTick: 0.0001,     // 最小利润（0.01%）
  maxHoldSeconds: 1800,      // 最大持仓时间（30分钟）
  maxTotalInvestUsdt: 500.0, // 最大总投入限制
  minSupportRatio: 0.6,      // 最小支撑比率
  
  // 深度范围
  supportRangeNear: 0.0025,
  supportRangeMid: 0.005,
  supportRangeFar: 0.01,
  
  // 测试网 API 配置
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "...",
  testnetSecretKey: "...",
  
  // WebSocket 配置
  marketDataWsUrl: "wss://stream.binance.com:9443"
}
```

### 正式网配置 (PRODUCTION)

```javascript
{
  _id: "USDCUSDT_PRODUCTION",
  symbol: "USDCUSDT",
  mode: "PRODUCTION",
  enabled: false,  // ⚠️ 默认禁用，需手动启用
  
  // 参数同上，金额更大
  maxBuyAmountUsdt: 50.0,
  maxTotalInvestUsdt: 1000.0,
  
  // 正式网 API 配置
  productionApiUrl: "https://api.binance.com",
  productionApiKey: "",  // TODO: 需要配置
  productionSecretKey: "",  // TODO: 需要配置
  
  marketDataWsUrl: "wss://stream.binance.com:9443"
}
```

## 查看当前配置

```bash
mongosh mongodb://localhost:27017/strategy_db
```

```javascript
// 查看所有配置
db.strategy_config.find().pretty()

// 查看测试网配置
db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" })

// 查看启用的配置
db.strategy_config.find({ enabled: true }).pretty()
```

## 修改配置

### 启用/禁用策略

```javascript
// 启用测试网
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { enabled: true } }
)

// 禁用测试网
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { enabled: false } }
)
```

### 调整买入价格

```javascript
// 调整最高买入价
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { maxBuyPrice: 1.0006 } }
)
```

### 调整交易金额

```javascript
// 调整单次最大买入金额
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { maxBuyAmountUsdt: 20.0 } }
)

// 调整最大总投入
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { maxTotalInvestUsdt: 1000.0 } }
)
```

### 调整持仓时间

```javascript
// 调整最大持仓时间（秒）
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { $set: { maxHoldSeconds: 3600 } }  // 1小时
)
```

## 配置缓存

系统使用 Redis 缓存配置，避免频繁查询数据库：

- **缓存键**: `strategy_config:{symbol}` (如 `strategy_config:USDCUSDT`)
- **缓存时间**: 配置更新后会自动刷新缓存
- **手动刷新**: 重启应用即可刷新缓存

## 配置生效时间

- **启用/禁用**: 立即生效（下次行情数据到达时）
- **价格参数**: 立即生效（下次行情数据到达时）
- **API 配置**: 需要重启应用
- **WebSocket 配置**: 需要重启应用

## 运行模式说明

| 模式 | 说明 | 交易API | 行情WebSocket |
|------|------|---------|---------------|
| **SIMULATION** | 模拟模式 | 不发送真实请求 | 正式网行情 |
| **TESTNET** | 测试网模式 | testnet.binance.vision | 正式网行情 |
| **PRODUCTION** | 正式网模式 | api.binance.com | 正式网行情 |

⚠️ **注意**: 测试网和正式网的行情都使用正式网 WebSocket，因为测试网行情不稳定。

## 常用配置脚本

更多配置脚本参考：

- `doc/db/init-strategy-config.js` - 初始化配置
- `doc/db/update-maxbuyprice.js` - 更新买入价格
- `doc/db/update-trend-config.js` - 更新趋势分析配置

## 故障排查

### 配置未生效

1. 检查 MongoDB 是否运行：`docker ps | grep mongo`
2. 检查配置是否正确：`db.strategy_config.find().pretty()`
3. 检查配置是否启用：`enabled: true`
4. 重启应用刷新缓存

### 找不到配置

```bash
# 重新初始化配置
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### 配置冲突

系统只会加载 **一个** 启用的配置（`enabled: true`）：

- 如果有多个启用的配置，系统会选择第一个
- 建议同时只启用一个配置（测试网或正式网）

## 相关文档

- [策略指南](STRATEGY_GUIDE.md) - 详细的策略说明和参数优化
- [测试指南](TEST_GUIDE.md) - 如何测试配置
- [快速开始](QUICK_START.md) - 快速启动指南

