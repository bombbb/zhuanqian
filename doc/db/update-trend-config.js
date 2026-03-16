// 更新策略配置，添加趋势分析和动态调价相关字段

// 更新测试网配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_TESTNET" },
  {
    $set: {
      // 启用动态价格调整
      enableDynamicPriceAdjustment: true,
      
      // 趋势分析间隔（秒）- 默认1小时分析一次
      trendAnalysisIntervalSeconds: 3600,
      
      // 趋势分析回看天数 - 分析最近7天的数据
      trendAnalysisLookbackDays: 7,
      
      // 动态调价的最小置信度阈值 - 置信度低于0.6不调整
      minConfidenceForAdjustment: 0.6
    }
  }
);

// 更新正式网配置
db.strategy_config.updateOne(
  { _id: "USDCUSDT_PRODUCTION" },
  {
    $set: {
      enableDynamicPriceAdjustment: true,
      trendAnalysisIntervalSeconds: 3600,
      trendAnalysisLookbackDays: 7,
      minConfidenceForAdjustment: 0.6
    }
  }
);

print("趋势分析配置已更新");

// 查看更新后的配置
print("\n测试网配置:");
printjson(db.strategy_config.findOne({ _id: "USDCUSDT_TESTNET" }));

print("\n正式网配置:");
printjson(db.strategy_config.findOne({ _id: "USDCUSDT_PRODUCTION" }));
