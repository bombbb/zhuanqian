#!/bin/bash

# 交易操作测试运行脚本
# 使用方法: ./run-trading-operations-test.sh

echo "========================================"
echo "交易操作测试 - 启动脚本"
echo "========================================"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境"
    echo "请安装Java 21或更高版本"
    exit 1
fi

# 显示Java版本
echo ""
echo "Java版本:"
java -version
echo ""

# 检查是否已编译
if [ ! -f "build/libs/zhuanqian-1.0-SNAPSHOT.jar" ]; then
    echo "⚠️  未找到编译文件，开始编译..."
    echo ""
    ./gradlew clean bootJar
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "❌ 编译失败，请检查错误信息"
        exit 1
    fi
    echo ""
    echo "✅ 编译成功"
fi

# 编译测试类
echo ""
echo "正在编译测试类..."
./gradlew compileTestJava

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 测试类编译失败，请检查错误信息"
    exit 1
fi

echo ""
echo "✅ 测试类编译成功"
echo ""

# 运行测试（使用Spring Boot的测试运行器）
echo "========================================"
echo "启动测试程序..."
echo "========================================"
echo ""

# 方式1: 通过Gradle运行（推荐）
./gradlew test --tests TradingOperationsTest.main

# 如果Gradle方式失败，尝试直接运行
if [ $? -ne 0 ]; then
    echo ""
    echo "尝试直接运行..."
    echo ""
    
    # 构建classpath
    CLASSPATH="build/classes/java/main:build/classes/java/test:build/resources/main:build/resources/test"
    
    # 添加所有依赖
    for jar in ~/.gradle/caches/modules-2/files-2.1/**/*.jar; do
        CLASSPATH="$CLASSPATH:$jar"
    done
    
    # 运行主类
    java -cp "$CLASSPATH" com.zq.TradingOperationsTest
fi

echo ""
echo "========================================"
echo "测试程序已结束"
echo "========================================"

