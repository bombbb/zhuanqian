#!/bin/bash

# 价格检查工具脚本
# 用于检查正式网的 bid/ask 价格并分析为什么没有下单

echo "=========================================="
echo "价格检查工具"
echo "=========================================="
echo ""

# 检查参数
UPDATE_MODE=false
if [ "$1" == "update" ]; then
    UPDATE_MODE=true
    echo "⚠️  更新模式：将更新数据库中的 basePrice"
    echo ""
fi

# 运行价格检查工具
if [ "$UPDATE_MODE" == "true" ]; then
    ./gradlew bootRun --args="--spring.main.web-application-type=none com.zq.tools.PriceChecker update"
else
    ./gradlew bootRun --args="--spring.main.web-application-type=none com.zq.tools.PriceChecker"
fi

