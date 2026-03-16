# 测试配置迁移说明

## 修改日期
2026-01-18

## 修改概述
将测试类 `TradingOperationsTest` 的配置方式从 YML 文件改为标准的 Spring 流程，从数据库读取配置。

## 修改原因
1. **安全性提升**：避免在 YML 文件中硬编码敏感的 API 密钥
2. **架构一致性**：与主应用保持一致，统一从数据库读取配置
3. **灵活性增强**：可以针对不同交易对和运行模式使用不同的配置
4. **集中管理**：所有配置集中在数据库中，方便管理和更新

## 修改内容

### 1. 测试类修改 (`TradingOperationsTest.java`)

#### 1.1 配置参数
**修改前**：
```java
@Value("${test.symbol:USDCUSDT}")
private String testSymbol;

@Value("${test.mode:TESTNET}")
private String testMode;

@Value("${binance.testnet.api-url:https://testnet.binance.vision}")
private String binanceApiUrl;

@Value("${binance.testnet.api-key}")
private String binanceApiKey;

@Value("${binance.testnet.secret-key}")
private String binanceSecretKey;
```

**修改后**：
```java
// 直接使用默认值，不再从 YML 文件读取
private String testSymbol = "USDCUSDT";
private String testMode = "TESTNET";

// 从数据库配置读取
private String binanceApiUrl;
private String binanceApiKey;
private String binanceSecretKey;
```

#### 1.2 初始化方法 (`setup()`)
**新增逻辑**：
1. 从数据库加载策略配置
2. 根据运行模式（TESTNET/PRODUCTION）选择对应的 API 配置
3. 验证 API 配置完整性
4. 初始化币安 API 服务

**核心代码**：
```java
@BeforeEach
public void setup() {
    // 1. 从数据库加载策略配置
    String configId = testSymbol + "_" + testMode;
    StrategyConfig config = strategyService.getStrategyConfig(testSymbol);
    
    // 2. 根据运行模式获取API配置
    StrategyConfig.Mode mode = config.getMode();
    switch (mode) {
        case TESTNET:
            binanceApiUrl = config.getTestnetApiUrl();
            binanceApiKey = config.getTestnetApiKey();
            binanceSecretKey = config.getTestnetSecretKey();
            break;
        case PRODUCTION:
            binanceApiUrl = config.getProductionApiUrl();
            binanceApiKey = config.getProductionApiKey();
            binanceSecretKey = config.getProductionSecretKey();
            break;
        // ...
    }
    
    // 3. 验证配置完整性
    if (binanceApiUrl == null || binanceApiKey == null || binanceSecretKey == null) {
        throw new RuntimeException("API配置不完整");
    }
    
    // 4. 初始化API服务
    apiService = new BinanceApiService(binanceApiUrl, binanceApiKey, binanceSecretKey);
}
```

### 2. 测试配置文件修改 (`application-test.yml`)

**修改前**：
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin

binance:
  testnet:
    api-url: https://testnet.binance.vision
    api-key: J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k
    secret-key: HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V

test:
  symbol: USDCUSDT
  mode: TESTNET
```

**修改后**：
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin

# 注意：币安API配置已移至数据库中
# 测试将从数据库的 strategy_config 集合读取配置
# 配置ID格式：{symbol}_{mode}，例如：USDCUSDT_TESTNET
```

### 3. 数据库配置

测试所需的 API 配置已包含在数据库初始化脚本中：

**脚本位置**：`doc/db/init-strategy-config.js`

**关键配置**：
```javascript
db.strategy_config.insertOne({
  _id: "USDCUSDT_TESTNET",
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  
  // 测试网 API 配置
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k",
  testnetSecretKey: "HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V",
  
  // 其他策略参数...
});
```

## 使用方法

### 1. 确保数据库配置存在

```bash
# 连接MongoDB并执行初始化脚本
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### 2. 运行测试

```bash
# 方式1：运行所有测试
./gradlew test --tests TradingOperationsTest

# 方式2：运行单个测试
./gradlew test --tests TradingOperationsTest.test01_MarketData

# 方式3：使用脚本
./run-trading-operations-test.sh
```

### 3. 配置ID格式

测试会根据以下规则查找配置：
- **配置ID格式**：`{symbol}_{mode}`
- **测试网示例**：`USDCUSDT_TESTNET`
- **生产网示例**：`USDCUSDT_PRODUCTION`

### 4. 切换测试环境

修改测试类中的默认值即可：

```java
// 在 TradingOperationsTest.java 中修改
private String testSymbol = "USDCUSDT";  // 修改交易对
private String testMode = "TESTNET";     // 修改运行模式：TESTNET/PRODUCTION
```

## 优势对比

### 修改前（YML 配置）
❌ API 密钥硬编码在配置文件中  
❌ 需要为每个环境维护单独的配置文件  
❌ 配置分散，管理困难  
❌ 与主应用配置方式不一致  

### 修改后（数据库配置）
✅ API 密钥集中存储在数据库中  
✅ 可以动态切换配置，无需重启  
✅ 配置统一管理，方便更新  
✅ 与主应用保持一致的架构  
✅ 支持多环境（测试网、生产网）  

## 配置结构

```
数据库配置 (StrategyConfig)
├── 基础信息
│   ├── symbol: 交易对
│   ├── mode: 运行模式（TESTNET/PRODUCTION/SIMULATION）
│   └── enabled: 是否启用
├── 测试网配置
│   ├── testnetApiUrl
│   ├── testnetApiKey
│   └── testnetSecretKey
├── 生产网配置
│   ├── productionApiUrl
│   ├── productionApiKey
│   └── productionSecretKey
└── 策略参数
    ├── referencePrice: 参考价格
    ├── maxBuyPrice: 最高买入价
    ├── maxBuyAmountUsdt: 最大买入金额
    └── ... 其他参数
```

## 兼容性说明

### 主应用
✅ 无影响，主应用已经使用数据库配置

### 其他测试类
⚠️ 其他测试类需要类似修改（如果使用 @Value 读取 API 配置）

### 配置文件
✅ `application-test.yml` 仅保留必要的 Spring 配置（如 MongoDB 连接）

## 故障排查

### 问题1：找不到配置
```
错误: 未找到配置: USDCUSDT_TESTNET
```

**解决方法**：
```bash
# 检查数据库中是否存在配置
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin
> db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" })

# 如果不存在，执行初始化脚本
mongosh mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin < doc/db/init-strategy-config.js
```

### 问题2：API配置不完整
```
错误: API配置不完整，请检查数据库配置
```

**解决方法**：
```javascript
// 手动更新数据库配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  { 
    $set: {
      testnetApiUrl: "https://testnet.binance.vision",
      testnetApiKey: "你的API Key",
      testnetSecretKey: "你的Secret Key"
    }
  }
)
```

### 问题3：MongoDB连接失败
```
错误: Unable to connect to MongoDB
```

**解决方法**：
```bash
# 检查MongoDB是否运行
docker ps | grep mongo

# 如果未运行，启动MongoDB
cd run && docker-compose up -d
```

## 后续优化建议

1. **环境变量支持**：支持通过环境变量覆盖数据库配置
2. **配置加密**：对敏感的 API 密钥进行加密存储
3. **配置验证**：启动时自动验证配置完整性
4. **多配置支持**：支持在运行时动态切换不同的配置

## 相关文档

- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - 实施总结
- [TRADING_OPERATIONS_TEST_GUIDE.md](TRADING_OPERATIONS_TEST_GUIDE.md) - 测试指南
- [doc/db/init-strategy-config.js](doc/db/init-strategy-config.js) - 数据库配置脚本

---

**最后更新**: 2026-01-18  
**影响范围**: 测试类配置加载方式  
**兼容性**: 向前兼容，不影响已有功能

