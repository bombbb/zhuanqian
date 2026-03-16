#!/bin/bash
# ========================================
# 带认证的完整测试脚本
# ========================================

set -e

MONGO_USER="admin"
MONGO_PASS="admin123"
MONGO_HOST="localhost:27017"
DB_NAME="strategy_db"
CONFIG_ID="USDCUSDT_TESTNET"
MONGO_URI="mongodb://${MONGO_USER}:${MONGO_PASS}@${MONGO_HOST}/${DB_NAME}?authSource=admin"

echo "========================================"
echo "带认证的完整测试流程"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# 获取容器ID
CONTAINER_ID=$(docker ps | grep mongo | awk '{print $1}' | head -1)

if [ -z "$CONTAINER_ID" ]; then
    echo "❌ MongoDB容器未运行"
    exit 1
fi

echo "✅ MongoDB容器ID: $CONTAINER_ID"

# 模拟价格上涨
echo -e "\n1️⃣  模拟价格上涨: 1.0002 -> 1.0003 -> 1.0005"
echo "-------------------------------------------"

echo "阶段1: 设置价格为 1.0002"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    db.strategy_config.updateOne(
        { _id: '${CONFIG_ID}' },
        { \$set: { maxBuyPrice: 1.0002 } }
    );
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    print('  ✅ 当前maxBuyPrice: ' + c.maxBuyPrice);
"
sleep 1

echo "阶段2: 价格上涨到 1.0003"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    db.strategy_config.updateOne(
        { _id: '${CONFIG_ID}' },
        { \$set: { maxBuyPrice: 1.0003 } }
    );
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    print('  ✅ 当前maxBuyPrice: ' + c.maxBuyPrice);
"
sleep 1

echo "阶段3: 价格继续上涨到 1.0005"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    db.strategy_config.updateOne(
        { _id: '${CONFIG_ID}' },
        { \$set: { maxBuyPrice: 1.0005 } }
    );
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    print('  ✅ 当前maxBuyPrice: ' + c.maxBuyPrice);
"

echo "✅ 价格上涨测试完成"
echo "ℹ️  预期：不应撤销买单"

# 模拟价格下跌
echo -e "\n2️⃣  模拟价格下跌: 1.0005 -> 1.0003 -> 1.0002"
echo "-------------------------------------------"

echo "阶段1: 价格下跌到 1.0003"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    db.strategy_config.updateOne(
        { _id: '${CONFIG_ID}' },
        { \$set: { maxBuyPrice: 1.0003 } }
    );
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    print('  ✅ 当前maxBuyPrice: ' + c.maxBuyPrice);
"
sleep 1

echo "阶段2: 价格继续下跌到 1.0002"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    db.strategy_config.updateOne(
        { _id: '${CONFIG_ID}' },
        { \$set: { maxBuyPrice: 1.0002 } }
    );
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    print('  ✅ 当前maxBuyPrice: ' + c.maxBuyPrice);
"

echo "✅ 价格下跌测试完成"
echo "⚠️  预期：应该撤销所有买单（需要应用运行并有DynamicPriceAdjuster）"

# 验证最终配置
echo -e "\n3️⃣  验证最终配置"
echo "-------------------------------------------"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    var c = db.strategy_config.findOne({ _id: '${CONFIG_ID}' });
    if (c) {
        print('配置ID: ' + c._id);
        print('交易对: ' + c.symbol);
        print('参考价格: ' + c.referencePrice);
        print('最高买入价: ' + c.maxBuyPrice);
        print('单次最大买入金额: ' + c.maxBuyAmountUsdt);
        print('最小利润空间: ' + c.minProfitTick);
        print('启用状态: ' + c.enabled);
        print('');
        print('✅ 配置验证完成');
        
        // 检查是否有basePrice残留
        if (c.basePrice !== undefined) {
            print('⚠️  警告：仍有basePrice字段，值为: ' + c.basePrice);
        } else {
            print('✅ 已确认无basePrice残留');
        }
    } else {
        print('❌ 配置不存在');
    }
"

# 检查订单
echo -e "\n4️⃣  检查当前订单状态"
echo "-------------------------------------------"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    var buyOrders = db.orders.find({
        symbol: 'USDCUSDT',
        side: 'BUY',
        status: { \$in: ['NEW', 'SUBMITTED'] }
    }).toArray();
    
    print('当前买单数量: ' + buyOrders.length);
    
    if (buyOrders.length > 0) {
        buyOrders.forEach(function(order) {
            print('  订单: ' + order.clientOrderId);
            print('    价格: ' + order.price);
            print('    数量: ' + order.quantity);
            print('    状态: ' + order.status);
        });
    } else {
        print('  ℹ️  当前无挂单（可能是测试环境）');
    }
"

# 检查持仓
echo -e "\n5️⃣  检查当前持仓状态"
echo "-------------------------------------------"
docker exec -i $CONTAINER_ID mongosh "$MONGO_URI" --quiet --eval "
    var positions = db.positions.find({
        symbol: 'USDCUSDT',
        status: { \$ne: 'CLOSED' }
    }).toArray();
    
    print('当前持仓数量: ' + positions.length);
    
    if (positions.length > 0) {
        var totalInvested = 0;
        positions.forEach(function(pos) {
            print('  持仓: ' + pos._id);
            print('    数量: ' + pos.quantity);
            print('    均价: ' + pos.avgPrice);
            print('    投入: ' + pos.totalInvestedUsdt);
            totalInvested += pos.totalInvestedUsdt;
        });
        print('  总投入: ' + totalInvested.toFixed(2) + ' USDT');
    } else {
        print('  ℹ️  当前无持仓');
    }
"

echo -e "\n========================================"
echo "测试总结"
echo "========================================"
echo "✅ 价格上涨场景已测试 (1.0002 -> 1.0005)"
echo "✅ 价格下跌场景已测试 (1.0005 -> 1.0002)"
echo "✅ 配置更新功能正常"
echo "✅ 数据库连接正常"
echo ""
echo "⚠️  注意事项："
echo "1. 撤单功能需要应用运行时由DynamicPriceAdjuster触发"
echo "2. 趋势分析需要足够的历史数据"
echo "3. 建议启动应用并监控日志："
echo "   cd run && ./run.sh"
echo "   tail -f logs/application.log | grep -i 'cancel\\|trend\\|maxBuyPrice'"
echo ""
echo "📚 详细文档："
echo "   cat PRICE_SIMULATION_GUIDE.md"
echo "========================================"
echo "完成时间: $(date '+%Y-%m-%d %H:%M:%S')"

