#!/bin/bash

# 数据库配置迁移脚本
# 用途：将旧的basePrice字段迁移到新的配置结构
# 
# 新字段说明：
# - maxBuyAmountUsdt: 单次最大买入金额（原来的basePrice现在表示这个意思）
# - referencePrice: 参考价格，用于判断是否值得买入（对于稳定币，通常设为1.0）
# 
# 迁移逻辑：
# - 保留旧的basePrice字段（用于兼容性）
# - 添加新字段 referencePrice = 1.0（稳定币的锚定价格）
# - 添加新字段 maxBuyAmountUsdt（根据旧配置推荐值）

set -e

echo "========================================="
echo "策略配置迁移工具"
echo "========================================="
echo ""
echo "此脚本将更新策略配置以支持新的买入逻辑："
echo "- basePrice 不再表示价格阈值，而改为表示'单次最大买入金额'"
echo "- 新增 referencePrice 字段，表示参考价格（稳定币通常为1.0）"
echo "- 新增 maxBuyAmountUsdt 字段，表示单次最大买入金额"
echo ""

# 检查MongoDB容器是否运行
if ! docker ps | grep -q trading-mongodb; then
    echo "错误: MongoDB容器未运行，请先启动容器"
    echo "运行: cd run && docker-compose up -d"
    exit 1
fi

echo "查询当前配置..."
echo ""

# 显示当前TESTNET配置
CURRENT_CONFIG=$(docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
    --authenticationDatabase admin strategy_db \
    --eval "db.strategy_config.findOne({_id: 'USDCUSDT_TESTNET'})")

echo "当前 TESTNET 配置:"
echo "$CURRENT_CONFIG"
echo ""

# 提取当前的basePrice和tradeAmountUsdt
CURRENT_BASE_PRICE=$(echo "$CURRENT_CONFIG" | grep "basePrice:" | awk '{print $2}' | tr -d ',')
CURRENT_TRADE_AMOUNT=$(echo "$CURRENT_CONFIG" | grep "tradeAmountUsdt:" | awk '{print $2}' | tr -d ',')

echo "检测到的配置:"
echo "  旧 basePrice: $CURRENT_BASE_PRICE"
echo "  旧 tradeAmountUsdt: $CURRENT_TRADE_AMOUNT"
echo ""

# 计算推荐值
# 对于USDC这样的稳定币，参考价格应该是1.0
RECOMMENDED_REF_PRICE=1.0

# 最大买入金额建议使用原来的tradeAmountUsdt，如果没有则使用20
if [ -z "$CURRENT_TRADE_AMOUNT" ] || [ "$CURRENT_TRADE_AMOUNT" == "null" ]; then
    RECOMMENDED_MAX_BUY=20.0
else
    RECOMMENDED_MAX_BUY=$CURRENT_TRADE_AMOUNT
fi

echo "推荐的新配置:"
echo "  referencePrice: $RECOMMENDED_REF_PRICE  (稳定币锚定价格)"
echo "  maxBuyAmountUsdt: $RECOMMENDED_MAX_BUY  (单次最大买入金额)"
echo ""
echo "说明："
echo "  - referencePrice (1.0): 当市场价格低于此价格时，系统会考虑买入"
echo "  - maxBuyAmountUsdt ($RECOMMENDED_MAX_BUY): 单次买入的最大金额"
echo "  - 实际买入金额会根据价格偏离程度动态计算，但不超过maxBuyAmountUsdt"
echo ""

read -p "是否应用此配置? (y/n): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消迁移"
    exit 0
fi

echo ""
echo "开始迁移..."

# 执行更新（TESTNET配置）
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
    --authenticationDatabase admin strategy_db <<EOF
db.strategy_config.updateOne(
    { _id: 'USDCUSDT_TESTNET' },
    { 
        \$set: { 
            referencePrice: $RECOMMENDED_REF_PRICE,
            maxBuyAmountUsdt: $RECOMMENDED_MAX_BUY
        } 
    }
)
EOF

echo "✓ TESTNET 配置已更新"

# 检查是否有PRODUCTION配置
PROD_EXISTS=$(docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
    --authenticationDatabase admin strategy_db \
    --eval "db.strategy_config.countDocuments({_id: 'USDCUSDT_PRODUCTION'})")

if [[ $PROD_EXISTS == *"1"* ]]; then
    echo ""
    read -p "检测到 PRODUCTION 配置，是否也更新? (y/n): " -n 1 -r
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
            --authenticationDatabase admin strategy_db <<EOF
db.strategy_config.updateOne(
    { _id: 'USDCUSDT_PRODUCTION' },
    { 
        \$set: { 
            referencePrice: $RECOMMENDED_REF_PRICE,
            maxBuyAmountUsdt: $RECOMMENDED_MAX_BUY
        } 
    }
)
EOF
        echo "✓ PRODUCTION 配置已更新"
    fi
fi

echo ""
echo "========================================="
echo "迁移完成！"
echo "========================================="
echo ""
echo "验证新配置:"
docker exec -i trading-mongodb mongosh --quiet -u admin -p admin123 \
    --authenticationDatabase admin strategy_db \
    --eval "db.strategy_config.findOne({_id: 'USDCUSDT_TESTNET'}, {referencePrice: 1, maxBuyAmountUsdt: 1, basePrice: 1, tradeAmountUsdt: 1})"

echo ""
echo "注意事项:"
echo "1. 旧字段 basePrice 和 tradeAmountUsdt 仍保留，用于兼容性"
echo "2. 新逻辑优先使用 referencePrice 和 maxBuyAmountUsdt"
echo "3. 如果应用正在运行，请重启以加载新配置"
echo ""
echo "重启命令: ./gradlew bootRun"
echo ""

