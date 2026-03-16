#!/bin/bash

# WebSocket 连接测试脚本
# 用于验证 WebSocket 是否正常启动并接收数据

echo "=========================================="
echo "WebSocket 连接测试"
echo "=========================================="
echo ""

echo "1. 检查数据库配置..."
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
var config = db.strategy_config.findOne({_id: 'USDCUSDT_TESTNET'});
if (config) {
    print('配置ID: ' + config._id);
    print('交易对: ' + config.symbol);
    print('模式: ' + config.mode);
    print('启用状态: ' + config.enabled);
    print('basePrice: ' + config.basePrice);
    print('价格日志间隔: ' + (config.priceLogIntervalSeconds || 5) + '秒');
} else {
    print('❌ 未找到配置');
}
"

echo ""
echo "2. 清理旧的应用日志..."
> logs/application.log

echo ""
echo "3. 启动应用（后台运行）..."
echo "   提示：应用将在后台运行，日志输出到 logs/application.log"
echo ""

# 启动应用（后台）
nohup ./gradlew bootRun > /dev/null 2>&1 &
APP_PID=$!

echo "   应用进程ID: $APP_PID"
echo "   等待应用启动..."

# 等待应用启动（最多30秒）
for i in {1..30}; do
    if grep -q "Started TradingApplication" logs/application.log 2>/dev/null; then
        echo "   ✅ 应用启动成功！"
        break
    fi
    echo -n "."
    sleep 1
done

echo ""
echo ""
echo "4. 检查 WebSocket 连接状态..."
sleep 3

# 检查 WebSocket 相关日志
if grep -q "WebSocket connection established successfully" logs/application.log; then
    echo "   ✅ WebSocket 连接成功！"
else
    echo "   ❌ WebSocket 连接失败或未启动"
    echo ""
    echo "   查看详细日志："
    grep -i "websocket\|connection" logs/application.log | tail -10
fi

echo ""
echo "5. 等待价格数据（最多20秒）..."

# 等待价格日志
FOUND_PRICE=false
for i in {1..20}; do
    if grep -q "Price update" logs/application.log; then
        FOUND_PRICE=true
        echo "   ✅ 已接收到价格数据！"
        break
    fi
    echo -n "."
    sleep 1
done

echo ""

if [ "$FOUND_PRICE" = true ]; then
    echo ""
    echo "6. 最近的价格日志："
    echo "=========================================="
    grep "Price update" logs/application.log | tail -5
    echo "=========================================="
else
    echo "   ❌ 未接收到价格数据"
    echo ""
    echo "   可能的原因："
    echo "   - WebSocket 连接失败"
    echo "   - 网络问题"
    echo "   - 配置未启用（enabled=false）"
fi

echo ""
echo "7. 检查是否有下单日志..."
if grep -q "BUY ORDER PLACED" logs/application.log; then
    echo "   ✅ 发现下单日志！"
    echo ""
    grep "BUY ORDER PLACED" logs/application.log | tail -3
else
    echo "   ℹ️  暂无下单记录（可能价格条件不满足）"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""
echo "应用正在后台运行（PID: $APP_PID）"
echo ""
echo "查看实时日志："
echo "  tail -f logs/application.log"
echo ""
echo "查看价格日志："
echo "  tail -f logs/application.log | grep 'Price update'"
echo ""
echo "停止应用："
echo "  kill $APP_PID"
echo ""

