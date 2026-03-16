# USDC稳定币套利交易系统

基于测试网的USDC/USDT稳定币套利交易系统，利用深度分析和均值回归策略自动执行买卖操作。

## 项目特点

- ✅ **TDD驱动开发**：完整的测试用例覆盖
- ✅ **Java 21虚拟线程**：高性能异步处理
- ✅ **MongoDB存储**：订单、持仓、统计数据持久化
- ✅ **深度分析**：基于订单簿深度判断买入时机
- ✅ **均值回归策略**：稳定币价格偏离时自动套利
- ✅ **参数优化**：基于历史数据的智能参数建议

## 核心模块

### 1. Domain实体（`com.zq.order`, `com.zq.position`, `com.zq.stats`）
- **Order**：订单实体，记录买卖订单详情
- **Position**：持仓实体，跟踪持仓数量和盈亏
- **SpreadStats**：价差统计，聚合价差分布
- **DepthStats**：深度统计，聚合深度数据
- **TradeStats**：交易统计，记录胜率、盈亏等

### 2. API服务（`com.zq.api`）
- **BinanceApiService**：币安API交互
  - 账户余额查询
  - 限价单/市价单下单
  - 订单查询和撤单
  - 行情数据获取

### 3. 业务服务
- **OrderService**：订单管理，虚拟线程异步写入
- **PositionService**：持仓管理，盈亏计算
- **StatsService**：统计服务，聚合统计数据
- **StrategyService**：策略配置管理
- **StrategyEngine**：策略引擎，核心交易逻辑

### 4. 策略优化（`com.zq.strategy`）
- **ParameterOptimizer**：参数优化器
  - 基于历史价差建议minProfitTick
  - 基于深度分布建议minSupportRatio
  - 基于交易结果建议maxHoldSeconds

## 策略逻辑

### 买入条件
1. 价格低于或等于basePrice
2. 深度支撑比率 >= minSupportRatio
3. 预期利润空间 >= minProfitTick

### 卖出条件
1. 买入后立即挂出卖单（价格 = 买入价 + minProfitTick）
2. 如果超过maxHoldSeconds仍未成交，撤单并市价平仓

### 风险控制
- 可用资金检查：确保资金充足
- 订单超时机制：避免长时间持仓
- 价格熔断：异常价格时停止交易

## MongoDB配置

### 初始化脚本
```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### 策略配置（strategy_config表）
```javascript
{
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  minProfitTick: 0.0001,      // 最小利润1个tick
  maxHoldSeconds: 1800,       // 最大持仓30分钟
  tradeAmountUsdt: 100.0,     // 单笔100 USDT
  minSupportRatio: 0.6,       // 最小支撑比60%
  basePrice: 1.0005,          // 基准价格
  supportRangeNear: 0.0025,   // 近端支撑范围
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "...",
  testnetSecretKey: "..."
}
```

## 项目结构

```
src/main/java/com/zq/
├── api/                    # API服务
│   ├── BinanceApiService.java
│   ├── Balance.java
│   ├── AccountInfo.java
│   ├── OrderResult.java
│   └── BinanceOrder.java
├── order/                  # 订单模块
│   ├── Order.java
│   └── OrderService.java
├── position/               # 持仓模块
│   ├── Position.java
│   └── PositionService.java
├── stats/                  # 统计模块
│   ├── SpreadStats.java
│   ├── DepthStats.java
│   ├── TradeStats.java
│   └── StatsService.java
└── strategy/               # 策略模块
    ├── StrategyConfig.java
    ├── StrategyService.java
    ├── StrategyEngine.java
    ├── DepthAnalyzer.java
    └── ParameterOptimizer.java
```

## 测试

所有核心模块都有对应的测试用例：
```bash
./gradlew test
```

- `BinanceApiServiceTest`：API交互测试
- `OrderServiceTest`：订单管理测试
- `PositionServiceTest`：持仓管理测试
- `StatsServiceTest`：统计服务测试

## 运行

### 1. 启动MongoDB
```bash
cd run
docker-compose up -d
```

### 2. 初始化配置
```bash
mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
```

### 3. 启动应用
```bash
./gradlew bootRun
```

## 数据分析

详细的历史数据分析见：
- [`doc/data.md`](doc/data.md) - USDC/TUSD历史价格分布
- [`doc/Strategy.md`](doc/Strategy.md) - 策略原理和设计思路
- [`doc/execution-plan.md`](doc/execution-plan.md) - 详细执行计划

## 技术栈

- Java 21 (虚拟线程)
- Spring Boot 3.3.5
- MongoDB 
- Lombok
- Gson
- JUnit 5

## 注意事项

1. **当前只支持测试网**：所有交易在测试网执行，但使用正式网行情
2. **参数优化**：ParameterOptimizer提供参数建议，但不会自动修改配置
3. **统计表设计**：所有统计表都是聚合表，不保存明细数据
4. **虚拟线程**：所有MongoDB写操作使用虚拟线程异步执行

## License

MIT

