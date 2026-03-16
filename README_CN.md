# USDC套利策略系统

## 📋 项目概述

这是一个基于币安交易所的USDC稳定币套利策略系统，通过监控市场价格波动，在价格偏离锚定价格时进行低买高卖，实现稳定收益。

## ✨ 核心功能

- **实时行情监控**: 通过WebSocket接收币安实时行情数据
- **智能买入信号**: 基于价格、深度支撑等多维度判断买入时机
- **自动下单**: 支持限价单和市价单，自动执行买卖操作
- **持仓管理**: 实时跟踪持仓、计算盈亏、管理可用资金
- **订单监控**: 自动监控订单成交，超时自动撤单
- **统计分析**: 记录价差、深度、交易等统计数据
- **多模式支持**: 支持模拟、测试网、正式网三种运行模式

## 🏗️ 技术架构

- **语言**: Java 21 (使用虚拟线程)
- **框架**: Spring Boot 3.x
- **数据库**: MongoDB 7.0
- **WebSocket**: Java-WebSocket
- **构建工具**: Gradle 8.x

## 📁 项目结构

```
zhuanqian/
├── src/main/java/com/zq/
│   ├── api/                    # API层
│   │   ├── BinanceApiService.java       # 币安REST API
│   │   ├── BinanceWebSocketClient.java  # WebSocket客户端
│   │   └── MarketDataHandler.java       # 行情数据处理
│   ├── config/                 # 配置层
│   │   ├── BinanceApiConfig.java        # API配置
│   │   └── BinanceWebSocketConfig.java  # WebSocket配置
│   ├── strategy/               # 策略层
│   │   ├── StrategyEngine.java          # 策略引擎
│   │   ├── StrategyService.java         # 策略服务
│   │   ├── StrategyConfig.java          # 策略配置
│   │   └── DepthAnalyzer.java           # 深度分析
│   ├── order/                  # 订单层
│   │   ├── OrderService.java            # 订单服务
│   │   └── Order.java                   # 订单实体
│   ├── position/               # 持仓层
│   │   ├── PositionService.java         # 持仓服务
│   │   └── Position.java                # 持仓实体
│   └── stats/                  # 统计层
│       └── StatsService.java            # 统计服务
├── src/test/java/com/zq/       # 测试
│   └── strategy/
│       └── StrategyIntegrationTest.java # 集成测试
├── doc/                        # 文档
│   ├── db/
│   │   └── init-strategy-config.js      # 数据库初始化脚本
│   ├── Strategy.md                      # 策略文档
│   └── execution-plan.md                # 执行计划
├── TEST_GUIDE.md               # 测试指南
├── CHANGES_SUMMARY.md          # 变更总结
└── quick-test.sh               # 快速测试脚本
```

## 🚀 快速开始

### 1. 环境准备

**必需软件**:
- Java 21+
- Docker & Docker Compose
- MongoDB 7.0+ (通过Docker启动)
- Gradle 8.x

**检查环境**:
```bash
java -version        # 应该显示 Java 21
docker --version     # Docker版本
```

### 2. 启动MongoDB

```bash
cd run
docker-compose up -d
```

验证MongoDB是否运行:
```bash
docker ps | grep mongo
```

### 3. 初始化配置

```bash
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

验证配置:
```bash
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
> db.strategy_config.findOne({ symbol: "USDCUSDT" })
```

### 4. 运行测试

**方式1: 使用快速测试脚本**
```bash
./quick-test.sh
```

**方式2: 手动运行测试**
```bash
./gradlew test
```

**方式3: 运行特定测试**
```bash
./gradlew test --tests StrategyIntegrationTest
```

### 5. 查看测试报告

```bash
open build/reports/tests/test/index.html
```

### 6. 启动应用

```bash
./gradlew bootRun
```

## 📊 监控和日志

### 查看日志

```bash
# 主日志
tail -f logs/application.log

# 交易日志（下单、撤单）
tail -f logs/trade.log

# 价格日志（已禁用实时打印）
tail -f logs/price.log
```

### 查看数据库

```bash
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin

# 查看订单
> db.orders.find().pretty()

# 查看持仓
> db.positions.find().pretty()

# 查看统计
> db.spread_stats.find().limit(10).pretty()
```

## ⚙️ 配置说明

### 运行模式

系统支持三种运行模式：

| 模式 | 说明 | 交易API | 行情WebSocket |
|------|------|---------|---------------|
| **SIMULATION** | 模拟模式 | 不发送真实请求 | 正式网行情 |
| **TESTNET** | 测试网模式 | testnet.binance.vision | 正式网行情 |
| **PRODUCTION** | 正式网模式 | api.binance.com | 正式网行情 |

**重要**: 测试网和正式网的行情都使用正式网WebSocket，因为测试网行情不稳定。

### 策略参数

在MongoDB中配置 (`strategy_config` 集合):

```javascript
{
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  
  // 核心参数
  basePrice: 1.0005,           // 基准价格
  minProfitTick: 0.0001,       // 最小利润（0.01%）
  maxHoldSeconds: 1800,        // 最大持仓时间（30分钟）
  tradeAmountUsdt: 100.0,      // 单笔交易金额
  minSupportRatio: 0.6,        // 最小支撑比率
  
  // API配置
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "your-key",
  testnetSecretKey: "your-secret",
  
  // WebSocket配置
  marketDataWsUrl: "wss://stream.binance.com:9443"
}
```

## 🧪 测试覆盖

系统包含完整的测试套件，覆盖所有核心功能：

1. ✅ 策略配置加载
2. ✅ API服务初始化
3. ✅ 行情数据处理
4. ✅ 买入信号检测
5. ✅ 订单创建和查询
6. ✅ 持仓更新
7. ✅ 卖出订单创建
8. ✅ 订单撤销
9. ✅ 完整交易流程
10. ✅ 统计数据记录

详细测试指南: [TEST_GUIDE.md](TEST_GUIDE.md)

## 📝 最近变更

### v1.1.0 (2026-01-18)

1. **数据库名称变更**: `trading` → `strategy_db`
2. **日志优化**: 移除行情实时日志，只在下单/撤单时打印
3. **WebSocket配置**: 添加 `marketDataWsUrl` 字段
4. **URL逻辑优化**: 测试网和正式网都使用正式网行情
5. **完整测试**: 添加10个集成测试用例

详细变更: [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)

## 🔧 常见问题

### Q1: MongoDB连接失败
```bash
# 检查MongoDB是否运行
docker ps | grep mongo

# 重启MongoDB
cd run && docker-compose restart
```

### Q2: 配置未找到
```bash
# 重新初始化配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### Q3: Java版本错误
```bash
# 检查Java版本
java -version

# macOS安装Java 21
brew install openjdk@21
```

### Q4: 测试失败
```bash
# 查看详细测试报告
open build/reports/tests/test/index.html

# 查看测试日志
cat build/test-results/test/*.xml
```

## 📚 文档

- [策略说明](doc/Strategy.md) - 详细的策略逻辑和参数说明
- [执行计划](doc/execution-plan.md) - 开发计划和实现细节
- [测试指南](TEST_GUIDE.md) - 如何运行和验证测试
- [变更总结](CHANGES_SUMMARY.md) - 最近的变更记录

## 🎯 下一步计划

- [ ] 添加更多交易对支持
- [ ] 实现动态参数调整
- [ ] 添加风险控制模块
- [ ] 实现Web管理界面
- [ ] 添加实时监控面板
- [ ] 优化深度分析算法

## ⚠️ 风险提示

1. **测试充分**: 在正式网运行前，务必在测试网充分测试
2. **资金管理**: 合理设置单笔交易金额和最大持仓
3. **监控日志**: 定期检查交易日志和异常情况
4. **网络稳定**: 确保网络连接稳定，避免断连
5. **API限制**: 注意币安API的频率限制

## 📄 许可证

MIT License

## 👥 联系方式

如有问题或建议，请通过以下方式联系：
- 提交Issue
- 发送邮件

---

**祝交易顺利！** 🚀💰

