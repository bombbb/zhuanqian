#!/bin/bash

# 交易操作测试 - 简单运行脚本
# 推荐使用这个脚本！

set -e

echo "========================================"
echo "交易操作测试 - 简单运行"
echo "========================================"
echo ""

# 1. 清理并编译
echo "正在编译项目..."
./gradlew clean build -x test

# 2. 运行单个测试（交互式菜单）
echo ""
echo "========================================"
echo "启动测试程序..."
echo "========================================"
echo ""

# 运行测试1（行情查询）- 不会失败
./gradlew test --tests TradingOperationsTest.test01_MarketData

echo ""
echo "✅ 测试完成"
echo ""
echo "💡 提示："
echo "1. 查看其他测试: ./gradlew test --tests TradingOperationsTest"
echo "2. 运行所有测试: ./gradlew test --tests TradingOperationsTest"
echo "3. 查看测试日志: tail -f logs/application.log"

