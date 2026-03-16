#!/bin/bash

# 简单的价格检查脚本 - 直接查询 Binance API

echo "=========================================="
echo "USDC/USDT 价格检查"
echo "=========================================="
echo ""

# 检查 MongoDB 中的配置
echo "1. 数据库配置："
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 --authenticationDatabase admin strategy_db --eval "
var configs = db.strategy_config.find({symbol: 'USDCUSDT'}).toArray();
configs.forEach(function(c) {
    print('');
    print('配置ID: ' + c._id);
    print('  模式: ' + c.mode);
    print('  启用: ' + c.enabled);
    print('  basePrice: ' + c.basePrice);
    print('  minProfitTick: ' + c.minProfitTick);
});
"

echo ""
echo "2. 正式网当前价格："
echo "正在查询 Binance API..."

# 使用 Python 查询价格
python3 << 'EOF'
import urllib.request
import json

try:
    url = "https://api.binance.com/api/v3/ticker/bookTicker?symbol=USDCUSDT"
    with urllib.request.urlopen(url, timeout=10) as response:
        data = json.loads(response.read())
        bid = float(data['bidPrice'])
        ask = float(data['askPrice'])
        mid = (bid + ask) / 2
        spread = ask - bid
        
        print(f"  Bid (买价): {bid:.6f}")
        print(f"  Ask (卖价): {ask:.6f}")
        print(f"  Mid (中间价): {mid:.6f}")
        print(f"  Spread (价差): {spread:.6f}")
        print("")
        
        # 分析下单条件
        print("3. 下单条件分析（假设 basePrice=1.0）:")
        basePrice = 1.0
        minProfitTick = 0.0001
        
        priceOk = mid <= basePrice
        expectedSell = basePrice + minProfitTick
        profitSpace = expectedSell - bid
        profitOk = profitSpace >= minProfitTick
        
        print(f"  条件1 (价格): lastPrice({mid:.6f}) <= basePrice({basePrice}) ? {priceOk}")
        print(f"  条件2 (利润): profitSpace({profitSpace:.6f}) >= minProfitTick({minProfitTick}) ? {profitOk}")
        print("")
        
        if priceOk and profitOk:
            print("✅ 满足下单条件")
        else:
            if not priceOk:
                print("❌ 价格条件不满足：当前价格高于 basePrice")
                print(f"   建议：将 basePrice 更新为 {ask:.4f} 或更高")
            if not profitOk:
                print("❌ 利润空间不足")
        
except Exception as e:
    print(f"查询失败: {e}")
EOF

echo ""
echo "=========================================="
echo "提示："
echo "  - 如果要更新 basePrice，运行: ./check-price.sh update"
echo "  - 或手动更新数据库"
echo "=========================================="

