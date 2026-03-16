// 更新 maxBuyPrice 配置
// 问题：当前数据库中 maxBuyPrice 可能未设置或为0，导致使用 referencePrice=1.0
// 解决：将 maxBuyPrice 设置为 1.0005，允许在 1.0005 以下买入

// 更新测试网配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  {
    $set: {
      // 最高买入价设置为 1.0005
      // 只有当价格低于此值时才考虑买入
      maxBuyPrice: 1.0005,
      
      // 确保参考价格设置正确
      referencePrice: 1.0,
      
      // 确保其他核心参数正确
      maxBuyAmountUsdt: 15.0,
      minProfitTick: 0.0001,
      maxHoldSeconds: 1800,
      maxTotalInvestUsdt: 500.0,
      minSupportRatio: 0.6,
      
      // 深度范围配置
      supportRangeNear: 0.0025,
      supportRangeMid: 0.005,
      supportRangeFar: 0.01,
      
      // 价格日志打印间隔（秒）
      priceLogIntervalSeconds: 5
    }
  }
);

// 更新正式网配置（如果需要）
db.strategy_config.updateOne(
  { _id: "USDCUSDT_PRODUCTION" },
  {
    $set: {
      maxBuyPrice: 1.0005,
      referencePrice: 1.0,
      maxBuyAmountUsdt: 50.0,
      minProfitTick: 0.0001,
      maxHoldSeconds: 1800,
      maxTotalInvestUsdt: 1000.0,
      minSupportRatio: 0.6,
      supportRangeNear: 0.0025,
      supportRangeMid: 0.005,
      supportRangeFar: 0.01,
      priceLogIntervalSeconds: 5
    }
  }
);

print("✓ maxBuyPrice 配置已更新");

// 查看更新后的配置
print("\n=== 测试网配置 ===");
var testnetConfig = db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" });
print("maxBuyPrice: " + testnetConfig.maxBuyPrice);
print("referencePrice: " + testnetConfig.referencePrice);
print("maxBuyAmountUsdt: " + testnetConfig.maxBuyAmountUsdt);
print("minProfitTick: " + testnetConfig.minProfitTick);
print("enabled: " + testnetConfig.enabled);

print("\n=== 正式网配置 ===");
var prodConfig = db.strategy_config.findOne({ _id: "USDCUSDT_PRODUCTION" });
if (prodConfig) {
  print("maxBuyPrice: " + prodConfig.maxBuyPrice);
  print("referencePrice: " + prodConfig.referencePrice);
  print("enabled: " + prodConfig.enabled);
} else {
  print("正式网配置不存在");
}

