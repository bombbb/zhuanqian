#!/bin/bash

# 更新 basePrice 脚本
# 根据当前市场价格更新 basePrice

echo "=========================================="
echo "更新 basePrice"
echo "=========================================="
echo ""

# 获取当前价格并计算建议的 basePrice
SUGGESTED_PRICE=$(python3 << 'EOF'
import urllib.request
import json
import math

try:
    url = "https://api.binance.com/api/v3/ticker/bookTicker?symbol=USDCUSDT"
    with urllib.request.urlopen(url, timeout=10) as response:
        data = json.loads(response.read())
        ask = float(data['askPrice'])
        # 建议价格：略高于当前 ask，向上取整到4位小数
        suggested = math.ceil(ask * 10000) / 10000.0
        print(f"{suggested:.4f}")
except Exception as e:
    print("1.0005")  # 默认值
EOF
)

echo "当前市场价格分析："
python3 << 'EOF'
import urllib.request
import json

try:
    url = "https://api.binance.com/api/v3/ticker/bookTicker?symbol=USDCUSDT"
    with urllib.request.urlopen(url, timeout=10) as response:
        data = json.loads(response.read())
        print(f"  Bid: {data['bidPrice']}")
        print(f"  Ask: {data['askPrice']}")
except:
    pass
EOF

echo ""
echo "建议的 basePrice: $SUGGESTED_PRICE"
echo ""

# 询问确认
read -p "是否更新 TESTNET 配置的 basePrice 为 $SUGGESTED_PRICE? (y/n): " confirm

if [ "$confirm" == "y" ] || [ "$confirm" == "Y" ]; then
    echo ""
    echo "正在更新数据库..."
    
    docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
    db.strategy_config.updateOne(
        { _id: 'USDCUSDT_TESTNET' },
        { \$set: { basePrice: $SUGGESTED_PRICE } }
    );
    print('✅ basePrice 已更新为: $SUGGESTED_PRICE');
    print('');
    print('更新后的配置:');
    var config = db.strategy_config.findOne({ _id: 'USDCUSDT_TESTNET' });
    print('  basePrice: ' + config.basePrice);
    print('  minProfitTick: ' + config.minProfitTick);
    print('  预期卖出价: ' + (config.basePrice + config.minProfitTick));
    "
    
    echo ""
    echo "=========================================="
    echo "⚠️  重要提示："
    echo "  1. 配置已更新到数据库"
    echo "  2. 如果应用正在运行，需要重启才能生效"
    echo "  3. 或者等待配置自动重新加载"
    echo "=========================================="
else
    echo "取消更新"
fi

