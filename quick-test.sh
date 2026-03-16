#!/bin/bash

# 套利策略快速测试脚本
# 用于验证所有功能是否正常

set -e  # 遇到错误立即退出

echo "=========================================="
echo "套利策略快速测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查MongoDB
echo "1. 检查MongoDB状态..."
if docker ps | grep -q mongo; then
    echo -e "${GREEN}✓ MongoDB正在运行${NC}"
else
    echo -e "${RED}✗ MongoDB未运行${NC}"
    echo "启动MongoDB: cd run && docker-compose up -d"
    exit 1
fi
echo ""

# 2. 检查配置
echo "2. 检查策略配置..."
CONFIG_COUNT=$(docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "db.strategy_config.countDocuments({ symbol: 'USDCUSDT' })")
if [ "$CONFIG_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ 策略配置已存在${NC}"
else
    echo -e "${YELLOW}⚠ 策略配置不存在，正在初始化...${NC}"
    docker exec -i trading-mongodb mongosh -u admin -p admin123 --authenticationDatabase admin strategy_db < doc/db/init-strategy-config.js
    echo -e "${GREEN}✓ 策略配置初始化完成${NC}"
fi
echo ""

# 3. 显示配置信息
echo "3. 策略配置信息："
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
var config = db.strategy_config.findOne({ symbol: 'USDCUSDT', mode: 'TESTNET' });
print('   Symbol: ' + config.symbol);
print('   Mode: ' + config.mode);
print('   Enabled: ' + config.enabled);
print('   Base Price: ' + config.basePrice);
print('   Min Profit Tick: ' + config.minProfitTick + ' (0.01%)');
print('   Max Hold Seconds: ' + config.maxHoldSeconds + ' (30 min)');
print('   Trade Amount USDT: ' + config.tradeAmountUsdt);
print('   Min Support Ratio: ' + config.minSupportRatio + ' (60%)');
print('   Testnet API URL: ' + config.testnetApiUrl);
print('   Market Data WS URL: ' + config.marketDataWsUrl);
"
echo ""

# 4. 清理测试数据
echo "4. 清理测试数据..."
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
db.orders.deleteMany({});
db.positions.deleteMany({});
db.spread_stats.deleteMany({});
db.depth_stats.deleteMany({});
db.trade_stats.deleteMany({});
print('   ✓ 测试数据已清理');
"
echo ""

# 5. 运行测试
echo "5. 运行集成测试..."
echo "=========================================="
./gradlew test --tests StrategyIntegrationTest --info 2>&1 | grep -E "(test[0-9]+_|BUILD|PASSED|FAILED)"
echo "=========================================="
echo ""

# 6. 显示测试结果
echo "6. 测试结果摘要："
if [ -f build/reports/tests/test/index.html ]; then
    echo -e "${GREEN}✓ 测试报告已生成${NC}"
    echo "   查看报告: open build/reports/tests/test/index.html"
else
    echo -e "${RED}✗ 测试报告未生成${NC}"
fi
echo ""

# 7. 检查数据库变更
echo "7. 检查数据库变更..."
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
print('   Orders: ' + db.orders.countDocuments({}));
print('   Positions: ' + db.positions.countDocuments({}));
print('   Spread Stats: ' + db.spread_stats.countDocuments({}));
print('   Depth Stats: ' + db.depth_stats.countDocuments({}));
"
echo ""

echo "=========================================="
echo -e "${GREEN}测试完成！${NC}"
echo "=========================================="
echo ""
echo "下一步："
echo "1. 查看测试报告: open build/reports/tests/test/index.html"
echo "2. 启动应用: ./gradlew bootRun"
echo "3. 监控日志: tail -f logs/application.log"
echo "4. 查看交易日志: tail -f logs/trade.log"
echo ""

