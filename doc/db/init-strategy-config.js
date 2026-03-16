// ========================================
// USDC策略配置 - MongoDB初始化脚本
// ========================================
// 使用方法：
// 1. 启动MongoDB: docker-compose up -d (在run目录下)
// 2. 连接MongoDB: mongosh mongodb://localhost:27017/strategy_db
// 3. 执行此脚本: load('/path/to/init-strategy-config.js')
// 或者直接: mongosh mongodb://localhost:27017/strategy_db < init-strategy-config.js

// 切换到数据库
use strategy_db;

// ========================================
// 1. 删除旧配置（如果存在）
// ========================================
db.strategy_config.deleteOne({ _id: "USDCUSDT_TESTNET" });
db.strategy_config.deleteOne({ _id: "USDCUSDT_PRODUCTION" });

// ========================================
// 2. 插入USDC策略配置 - 测试网
// ========================================
// 参数说明和合理性分析见 doc/execution-plan.md
db.strategy_config.insertOne({
  _id: "USDCUSDT_TESTNET",
  symbol: "USDCUSDT",
  mode: "TESTNET",
  enabled: true,
  
  // ========== 核心策略参数（基于历史数据分析和风控要求）==========
  // referencePrice: 参考价格（锚定价）
  // - 设置为 1.0（USDC 锚定 1美元）
  // - 仅作为参考，用于计算价格偏离度
  // - 不参与下单判断
  referencePrice: 1.0,
  
  // maxBuyPrice: 最高买入价
  // - 设置为 1.0004（更保守的买入价格上限）
  // - 只有当价格低于此值时才考虑买入
  // - 2025年数据显示价格主要在0.9995-1.0010区间
  maxBuyPrice: 1.0004,
  
  // maxBuyPriceCeiling: 最高买入价硬上限（动态调整也不能超过）
  maxBuyPriceCeiling: 1.0004,
  
  // maxBuyAmountUsdt: 单次最大买入金额（USDT）
  // - 设置为 300 USDT（每次下单的最大金额）
  // - 实际下单金额会根据价格偏离程度动态计算，但不超过此值
  // - 单笔金额较小，可以分散风险，增加交易机会
  maxBuyAmountUsdt: 300.0,
  
  // minProfitTick: 最小利润空间
  // - 设置为 0.0001（0.01%）
  // - 符合风控建议：最小利润0.01%以上 ✓
  // - 在USDC这种稳定币上，0.01%是合理的利润目标
  minProfitTick: 0.0001,
  
  // maxHoldSeconds: 最大持仓时间（秒）
  // - 设置为 1800秒（30分钟）
  // - 符合风控建议：建议30分钟 ✓
  // - 稳定币均值回归较快，通常10-30分钟内会回归
  maxHoldSeconds: 1800,
  
  // maxBuyOpenSeconds: 买单最大挂单时间（秒）
  // - 设置为 120秒（2分钟）
  maxBuyOpenSeconds: 120,
  
  // maxTotalInvestUsdt: 最大总投入金额限制（USDT）
  // - 设置为 500 USDT
  // - 即使账户有更多资金，也不会超过此限额进行投资
  // - 500U / 平均15U单笔 = 约33单，提供充足的交易机会
  // - 可以控制总体风险敞口
  maxTotalInvestUsdt: 500.0,
  
  // minSupportRatio: 最小支撑比率
  // - 设置为 0.6（60%）
  // - 符合风控建议：支撑比率阈值0.6以上 ✓
  // - 当买盘深度60%集中在当前价附近，说明下跌空间有限
  // - 这是强支撑信号，回归概率高
  minSupportRatio: 0.6,
  
  // ========== 近期价格区间动态更新 ==========
  enableRecentPriceBandAdjust: true,
  recentPriceLookbackDays: 30,
  recentPricePercentile: 0.85,
  recentPriceSafetyMargin: 0.00005,
  
  // ========== 深度范围配置 ==========
  // supportRangeNear: 近端支撑范围
  // - 设置为 0.0025（对应约25个tick）
  // - 用于计算深度支撑比率
  // - 在USDC这种稳定币上，0.0025的范围足够捕捉关键支撑
  supportRangeNear: 0.0025,
  
  // 中端和远端支撑范围（预留，未来可用于多层次深度分析）
  supportRangeMid: 0.005,
  supportRangeFar: 0.01,
  
  // ========== 测试网API配置 ==========
  // 从 /Users/bao/java/usdt 项目提取的测试网配置
  testnetApiUrl: "https://testnet.binance.vision",
  testnetApiKey: "J2rlMxM3JWtzxe2acUIPe5crXVW3teXtYnjlgT6U4f8jNwE7CefuGGK9HxnSr28k",
  testnetSecretKey: "HTCxeF2FOEv4qL0nzvll1ysydZPCTSSJdShWY8llNhOkalTuL6wAv2VpSUHIlc8V",
  
  // ========== WebSocket配置 ==========
  // 注意：测试网和正式网的行情都使用正式网的WebSocket
  // 因为测试网行情不稳定，统一使用正式网行情
  marketDataWsUrl: "wss://stream.binance.com:9443"
});

// ========================================
// 3. 插入USDC策略配置 - 正式网（暂不启用）
// ========================================
db.strategy_config.insertOne({
  _id: "USDCUSDT_PRODUCTION",
  symbol: "USDCUSDT",
  mode: "PRODUCTION",
  enabled: false,  // ⚠️ 默认禁用，需要手动启用
  
  // ========== 核心策略参数（同测试网配置）==========
  referencePrice: 1.0,
  maxBuyPrice: 1.0005,
  maxBuyAmountUsdt: 50.0,  // 正式网使用更大的金额
  minProfitTick: 0.0001,
  maxHoldSeconds: 1800,
  maxTotalInvestUsdt: 1000.0,  // 正式网更大的总投入限制
  minSupportRatio: 0.6,
  
  // ========== 深度范围配置 ==========
  supportRangeNear: 0.0025,
  supportRangeMid: 0.005,
  supportRangeFar: 0.01,
  
  // ========== 正式网API配置 ==========
  // ⚠️ 需要手动填入正式网API密钥，不要硬编码在此处
  // 建议：通过环境变量或安全配置管理工具传入
  productionApiUrl: "https://api.binance.com",
  productionApiKey: "",  // TODO: 需要配置
  productionSecretKey: "",  // TODO: 需要配置
  
  // ========== WebSocket配置 ==========
  marketDataWsUrl: "wss://stream.binance.com:9443"
});

// ========================================
// 3. 创建索引
// ========================================
print("Creating indexes for strategy_config...");
db.strategy_config.createIndex({ "symbol": 1, "enabled": 1 });
db.strategy_config.createIndex({ "mode": 1 });

// ========================================
// 4. 创建其他集合的索引
// ========================================

// 订单表索引
print("Creating indexes for orders...");
db.orders.createIndex({ "symbol": 1, "status": 1, "createTime": -1 });
db.orders.createIndex({ "orderId": 1 });
db.orders.createIndex({ "relatedOrderId": 1 });
db.orders.createIndex({ "mode": 1, "status": 1 });

// 持仓表索引
print("Creating indexes for positions...");
db.positions.createIndex({ "symbol": 1, "mode": 1 }, { unique: true });

// 价差统计表索引（复合唯一索引，避免重复）
print("Creating indexes for spread_stats...");
db.spread_stats.createIndex({ 
  "symbol": 1, 
  "date": 1, 
  "bidPrice": 1, 
  "askPrice": 1 
}, { unique: true });

// 深度统计表索引
print("Creating indexes for depth_stats...");
db.depth_stats.createIndex({ 
  "symbol": 1, 
  "date": 1, 
  "priceRangeBucket": 1, 
  "supportRatioBucket": 1 
}, { unique: true });

// 交易统计表索引
print("Creating indexes for trade_stats...");
db.trade_stats.createIndex({ "symbol": 1, "date": 1 }, { unique: true });

// 配置变更日志表索引
print("Creating indexes for config_change_logs...");
db.config_change_logs.createIndex({ "configId": 1, "changeTime": -1 });
db.config_change_logs.createIndex({ "symbol": 1, "changeTime": -1 });
db.config_change_logs.createIndex({ "source": 1 });

// ========================================
// 5. 验证插入结果
// ========================================
print("\n========================================");
print("Verification Results:");
print("========================================");

// 测试网配置
var testnetConfig = db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" });
if (testnetConfig) {
  print("✅ Testnet config inserted successfully!");
  print("\nTestnet Configuration Summary:");
  print("  Symbol: " + testnetConfig.symbol);
  print("  Mode: " + testnetConfig.mode);
  print("  Enabled: " + testnetConfig.enabled);
  print("  Reference Price: " + testnetConfig.referencePrice + " (参考价/锚定价)");
  print("  Max Buy Price: " + testnetConfig.maxBuyPrice + " (最高买入价 ✓)");
  print("  Max Buy Amount USDT: " + testnetConfig.maxBuyAmountUsdt + " (单次最大买入金额 ✓)");
  print("  Min Profit Tick: " + testnetConfig.minProfitTick + " (0.01% ✓)");
  print("  Max Hold Seconds: " + testnetConfig.maxHoldSeconds + " (30 min ✓)");
  print("  Max Total Invest USDT: " + testnetConfig.maxTotalInvestUsdt + " (最大总投入 ✓)");
  print("  Min Support Ratio: " + testnetConfig.minSupportRatio + " (60% ✓)");
  print("  Support Range Near: " + testnetConfig.supportRangeNear);
  print("  Testnet API URL: " + testnetConfig.testnetApiUrl);
  print("  Testnet API Key: " + testnetConfig.testnetApiKey.substring(0, 10) + "...");
} else {
  print("❌ Failed to insert testnet config!");
}

print("");

// 正式网配置
var prodConfig = db.strategy_config.findOne({ _id: "USDCUSDT_PRODUCTION" });
if (prodConfig) {
  print("✅ Production config inserted successfully!");
  print("\nProduction Configuration Summary:");
  print("  Symbol: " + prodConfig.symbol);
  print("  Mode: " + prodConfig.mode);
  print("  Enabled: " + prodConfig.enabled + " (默认禁用 ⚠️)");
  print("  Reference Price: " + prodConfig.referencePrice);
  print("  Max Buy Price: " + prodConfig.maxBuyPrice);
  print("  Max Buy Amount USDT: " + prodConfig.maxBuyAmountUsdt);
  print("  Min Profit Tick: " + prodConfig.minProfitTick);
  print("  Max Hold Seconds: " + prodConfig.maxHoldSeconds);
  print("  Min Support Ratio: " + prodConfig.minSupportRatio);
  print("  Production API URL: " + prodConfig.productionApiUrl);
  print("  Production API Key: " + (prodConfig.productionApiKey || "未配置 ⚠️"));
} else {
  print("❌ Failed to insert production config!");
}

print("\n========================================");
print("Indexes Created:");
print("========================================");
print("strategy_config indexes:", db.strategy_config.getIndexes().length);
print("orders indexes:", db.orders.getIndexes().length);
print("positions indexes:", db.positions.getIndexes().length);
print("spread_stats indexes:", db.spread_stats.getIndexes().length);
print("depth_stats indexes:", db.depth_stats.getIndexes().length);
print("trade_stats indexes:", db.trade_stats.getIndexes().length);
print("config_change_logs indexes:", db.config_change_logs.getIndexes().length);

print("\n========================================");
print("✅ Initialization Complete!");
print("========================================");
print("\n💡 Next Steps:");
print("1. 使用测试网配置进行开发和测试");
print("2. 正式网配置已创建但默认禁用");
print("3. 启用正式网前需要配置API密钥");
print("4. 运行快速测试: ./quick-test.sh");
print("========================================");
print("\n⚠️  风控提醒:");
print("✓ 单笔交易金额: 10-20 USDT（基准15U，随机浮动）");
print("✓ 最大总投入: 500 USDT（硬上限）");
print("✓ 最大持仓时间: 30分钟");
print("✓ 最小利润: 0.01%");
print("✓ 支撑比率阈值: 0.6 (60%)");
print("========================================\n");
