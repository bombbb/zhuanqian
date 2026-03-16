#!/bin/bash

# 统计系统测试脚本
# 用于测试统计分析和配置变更功能

set -e

echo "========================================="
echo "统计系统测试脚本"
echo "========================================="
echo ""

# 检查 MongoDB 是否运行
echo "1. 检查 MongoDB 连接..."
if ! mongosh --quiet mongodb://localhost:27017/strategy_db --eval "db.runCommand({ ping: 1 })" > /dev/null 2>&1; then
    echo "❌ MongoDB 未运行，请先启动:"
    echo "   cd run && docker-compose up -d"
    exit 1
fi
echo "✓ MongoDB 连接正常"
echo ""

# 重新初始化数据库（包含新增的索引）
echo "2. 初始化数据库..."
mongosh --quiet mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js
echo "✓ 数据库初始化完成"
echo ""

# 验证集合和索引
echo "3. 验证集合和索引..."
echo "   - strategy_config"
echo "   - orders"
echo "   - positions"
echo "   - spread_stats"
echo "   - depth_stats"
echo "   - trade_stats"
echo "   - config_change_logs (新增)"
mongosh --quiet mongodb://localhost:27017/strategy_db --eval "
  print('策略配置数量:', db.strategy_config.countDocuments());
  print('config_change_logs 索引:', db.config_change_logs.getIndexes().length);
"
echo ""

# 运行单元测试
echo "4. 运行单元测试..."
echo "   测试类: StatsAnalysisServiceTest"
echo ""
./gradlew test --tests StatsAnalysisServiceTest
echo ""

# 运行集成测试
echo "5. 运行集成测试..."
echo "   测试类: StatsIntegrationTest"
echo ""
./gradlew test --tests StatsIntegrationTest
echo ""

# 查看测试报告
echo "========================================="
echo "测试完成！"
echo "========================================="
echo ""
echo "测试报告位置:"
echo "  build/reports/tests/test/index.html"
echo ""
echo "查看配置变更日志:"
echo "  mongosh mongodb://localhost:27017/strategy_db"
echo "  > db.config_change_logs.find().sort({changeTime: -1}).limit(5)"
echo ""
echo "查看统计数据:"
echo "  > db.spread_stats.find().limit(5)"
echo "  > db.depth_stats.find().limit(5)"
echo "  > db.trade_stats.find().limit(5)"
echo ""
echo "========================================="

