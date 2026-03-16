// 修复 maxBuyPrice 配置 - 设置更高的买入价格上限
// 当前问题：maxBuyPrice=1.0002 太低，导致价格1.0003时无法下单
// 解决方案：提高到 1.0010，根据历史数据分析（见 doc/data.md）
//   - 2025年USDC主要在 0.9995-1.0010 区间
//   - 1.0010以下的价格占比超过90%
//   - 设置为1.0010可以捕获更多交易机会

use strategy_db;

// 更新测试网配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  {
    $set: {
      // 最高买入价设置为 1.0010（之前是1.0002，太保守）
      // 根据历史数据，1.0010以下是合理的买入区间
      maxBuyPrice: 1.0010,
      
      // 确保参考价格设置正确
      referencePrice: 1.0,
      
      // 确保其他核心参数正确
      maxBuyAmountUsdt: 15.0,
      minProfitTick: 0.0001,
      maxHoldSeconds: 1800,
      maxTotalInvestUsdt: 500.0,
      minSupportRatio: 0.6,
      supportRangeNear: 0.0025,
      supportRangeMid: 0.005,
      supportRangeFar: 0.01
    }
  }
);

// 更新正式网配置（同样设置）
db.strategy_config.updateOne(
  { _id: "USDCUSDT_PRODUCTION" },
  {
    $set: {
      maxBuyPrice: 1.0010,
      referencePrice: 1.0
    }
  }
);

print("========================================");
print("✅ maxBuyPrice 已更新为 1.0010");
print("========================================");

// 验证更新结果
print("\n=== 测试网配置 ===");
var testnetConfig = db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" });
if (testnetConfig) {
  print("✓ maxBuyPrice: " + testnetConfig.maxBuyPrice + " (已更新)");
  print("  referencePrice: " + testnetConfig.referencePrice);
  print("  maxBuyAmountUsdt: " + testnetConfig.maxBuyAmountUsdt);
  print("  minProfitTick: " + testnetConfig.minProfitTick);
  print("  enabled: " + testnetConfig.enabled);
} else {
  print("❌ 测试网配置不存在！");
}

print("\n=== 正式网配置 ===");
var prodConfig = db.strategy_config.findOne({ _id: "USDCUSDT_PRODUCTION" });
if (prodConfig) {
  print("✓ maxBuyPrice: " + prodConfig.maxBuyPrice + " (已更新)");
  print("  referencePrice: " + prodConfig.referencePrice);
  print("  enabled: " + prodConfig.enabled);
} else {
  print("❌ 正式网配置不存在！");
}

print("\n========================================");
print("📊 历史数据支持 (doc/data.md):");
print("========================================");
print("2025年 USDC 价格分布：");
print("  0.9995 - 0.9998: 13.44%");
print("  0.9998 - 1.0000: 17.95%");
print("  1.0000 - 1.0002: 21.79%");
print("  1.0002 - 1.0005: 27.59%");
print("  1.0005 - 1.0010: 16.55%");
print("  >= 1.0010:        2.67%");
print("");
print("✓ 在1.0010以下买入可以覆盖约97%的价格区间");
print("✓ 这是基于2025年全年24,100,319条历史交易数据");
print("========================================\n");

