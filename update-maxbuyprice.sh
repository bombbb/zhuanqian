#!/bin/bash

# 更新 maxBuyPrice 配置脚本
# 用途：修复 "价格过高" 问题，将 maxBuyPrice 从 0 更新为 1.0005

echo "=================================================="
echo "更新 maxBuyPrice 配置"
echo "=================================================="
echo ""

# MongoDB 连接信息
MONGO_HOST="localhost"
MONGO_PORT="27017"
MONGO_DB="strategy_db"

# 检查 MongoDB 是否运行
echo "1. 检查 MongoDB 连接..."
if ! docker ps | grep -q mongo; then
    echo "❌ MongoDB 容器未运行"
    echo "请先启动 MongoDB: cd run && docker-compose up -d"
    exit 1
fi

echo "✓ MongoDB 正在运行"
echo ""

# 执行更新脚本
echo "2. 执行配置更新..."
docker exec -i $(docker ps -qf "name=mongo") mongosh strategy_db < doc/db/update-maxbuyprice.js

echo ""
echo "=================================================="
echo "✓ 配置更新完成"
echo "=================================================="
echo ""
echo "下一步："
echo "1. 如果程序正在运行，需要重启以加载新配置"
echo "2. 或者等待程序自动重新加载配置（约1分钟）"
echo ""
echo "验证方法："
echo "  tail -f logs/application.log | grep 'maxBuyPrice\\|暂不下单'"
echo ""

