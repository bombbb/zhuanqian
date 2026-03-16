#!/bin/bash

# 趋势分析功能配置脚本
# 用于快速配置和启动趋势分析功能

set -e

echo "================================================"
echo "  趋势分析和动态调价功能配置"
echo "================================================"
echo ""

# 检查MongoDB是否运行
echo "1. 检查MongoDB状态..."
if docker ps | grep -q trading-mongodb; then
    echo "   ✓ MongoDB 正在运行"
else
    echo "   ✗ MongoDB 未运行"
    echo "   启动MongoDB..."
    cd run && docker-compose up -d && cd ..
    sleep 3
fi

echo ""
echo "2. 更新数据库配置..."
docker exec -i trading-mongodb mongosh -u admin -p admin123 \
  --authenticationDatabase admin strategy_db < doc/db/update-trend-config.js

echo ""
echo "3. 验证配置..."
echo ""
echo "测试网配置:"
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
  --authenticationDatabase admin strategy_db \
  --eval "db.strategy_config.findOne({_id: 'USDCUSDT_TESTNET'}, {enableDynamicPriceAdjustment: 1, trendAnalysisIntervalSeconds: 1, trendAnalysisLookbackDays: 1, minConfidenceForAdjustment: 1, referencePrice: 1, maxBuyAmountUsdt: 1})"

echo ""
echo "================================================"
echo "  配置完成！"
echo "================================================"
echo ""
echo "下一步操作："
echo ""
echo "1. 启动应用："
echo "   ./gradlew bootRun"
echo ""
echo "   或后台运行："
echo "   cd run && ./run.sh"
echo ""
echo "2. 监控日志："
echo "   tail -f logs/application.log"
echo ""
echo "3. 查看趋势分析日志（每小时一次）："
echo "   tail -f logs/application.log | grep -E '趋势分析|Trend analysis|Price adjusted'"
echo ""
echo "4. 手动调整配置（可选）："
echo ""
echo "   # 改为每30分钟分析一次"
echo "   docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \\"
echo "     --authenticationDatabase admin strategy_db \\"
echo "     --eval \"db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\\\$set: {trendAnalysisIntervalSeconds: 1800}})\""
echo ""
echo "   # 暂时禁用动态调价"
echo "   docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \\"
echo "     --authenticationDatabase admin strategy_db \\"
echo "     --eval \"db.strategy_config.updateOne({_id: 'USDCUSDT_TESTNET'}, {\\\$set: {enableDynamicPriceAdjustment: false}})\""
echo ""
echo "5. 查看完整文档："
echo "   cat STRATEGY_GUIDE.md"
echo "   cat UPDATE_SUMMARY.md"
echo ""
echo "================================================"

