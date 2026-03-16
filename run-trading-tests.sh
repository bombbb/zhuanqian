#!/bin/bash

# 交易操作测试运行脚本
# 用于运行TradingOperationsTest测试类
# 作者: zhuanqian
# 日期: 2026-01-18

echo "========================================"
echo "交易操作测试 - TradingOperationsTest"
echo "========================================"
echo ""

# 检查MongoDB是否运行
echo "检查MongoDB连接..."
if ! mongosh --quiet --eval "db.adminCommand('ping')" mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin &>/dev/null; then
    echo "❌ MongoDB未运行或连接失败"
    echo "请先启动MongoDB: cd run && docker-compose up -d"
    exit 1
fi
echo "✅ MongoDB连接正常"
echo ""

# 检查配置
echo "检查数据库配置..."
CONFIG_COUNT=$(mongosh --quiet mongodb://admin:admin123@localhost:27017/strategy_db?authSource=admin \
    --eval "db.strategy_config.countDocuments({symbol: 'USDCUSDT', enabled: true})")

if [ "$CONFIG_COUNT" -eq "0" ]; then
    echo "⚠️  警告: 未找到USDCUSDT的启用配置"
    echo "建议先初始化配置: mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js"
else
    echo "✅ 配置检查通过 (找到 $CONFIG_COUNT 个配置)"
fi
echo ""

# 运行测试
echo "========================================"
echo "开始运行测试..."
echo "========================================"
echo ""

# 如果有参数，运行指定的测试方法
if [ -n "$1" ]; then
    echo "运行单个测试: $1"
    ./gradlew test --tests "TradingOperationsTest.$1" --info
else
    echo "运行全部测试..."
    ./gradlew test --tests "TradingOperationsTest" --info
fi

TEST_EXIT_CODE=$?

echo ""
echo "========================================"
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ 测试完成"
else
    echo "❌ 测试失败 (退出码: $TEST_EXIT_CODE)"
fi
echo "========================================"
echo ""

# 显示可用的测试方法
if [ -z "$1" ]; then
    echo "💡 提示: 可以运行单个测试，例如:"
    echo "  ./run-trading-tests.sh test01_MarketData           # 行情查询测试"
    echo "  ./run-trading-tests.sh test02_AccountBalance       # 账户余额测试"
    echo "  ./run-trading-tests.sh test03_PlaceOrders          # 下单测试"
    echo "  ./run-trading-tests.sh test04_QueryOpenOrders      # 挂单查询测试"
    echo "  ./run-trading-tests.sh test05_CancelOrder          # 撤单测试"
    echo "  ./run-trading-tests.sh test06_BatchCancelOrders    # 批量撤单测试"
    echo "  ./run-trading-tests.sh test07_CancelAllOrders      # 全部撤单测试"
    echo "  ./run-trading-tests.sh test08_ConfigurationLoading # 配置读取测试"
    echo "  ./run-trading-tests.sh test09_CompleteTrading      # 综合测试"
    echo ""
fi

exit $TEST_EXIT_CODE

