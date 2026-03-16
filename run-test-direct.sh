#!/bin/bash

# 交易操作测试 - 直接运行脚本
# 使用 Java main 方法直接运行测试，不依赖 JUnit

set -e

echo "========================================"
echo "交易操作测试 - 直接运行"
echo "========================================"

# 1. 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到 Java，请先安装 Java 17+"
    exit 1
fi

echo "✅ Java 版本:"
java -version

# 2. 编译项目
echo ""
echo "正在编译项目..."
if ! ./gradlew clean compileJava compileTestJava; then
    echo "❌ 编译失败"
    exit 1
fi
echo "✅ 编译成功"

# 3. 准备 classpath
echo ""
echo "准备运行环境..."

# 获取所有依赖
CLASSPATH="build/classes/java/main:build/classes/java/test"

# 添加所有 Gradle 依赖
for jar in ~/.gradle/caches/modules-2/files-2.1/*/*/*/*/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "✅ Classpath 已配置"

# 4. 运行测试
echo ""
echo "========================================"
echo "启动测试程序..."
echo "========================================"
echo ""

java -cp "$CLASSPATH" com.zq.TradingOperationsTest

echo ""
echo "========================================"
echo "测试程序已退出"
echo "========================================"

