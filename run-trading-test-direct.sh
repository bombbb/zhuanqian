#!/bin/bash

# 交易操作测试 - 直接运行模式
# 不依赖JUnit，直接运行main方法

echo "========================================"
echo "交易操作测试 - 直接运行模式"
echo "========================================"

# 进入项目目录
cd "$(dirname "$0")"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境"
    exit 1
fi

echo ""
echo "✅ Java版本:"
java -version
echo ""

# 第一步：确保主程序已编译
echo "📦 步骤1: 编译主程序..."
./gradlew compileJava compileTestJava

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 第二步：构建classpath
echo "📦 步骤2: 构建classpath..."

# 基础classpath
CLASSPATH="build/classes/java/main:build/classes/java/test"
CLASSPATH="$CLASSPATH:build/resources/main:build/resources/test"

# 添加Gradle依赖
for jar in build/libs/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# 添加Gradle缓存的依赖
GRADLE_HOME="${HOME}/.gradle"
if [ -d "$GRADLE_HOME/caches/modules-2/files-2.1" ]; then
    # Spring Boot相关
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.springframework.boot/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.springframework/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.springframework.data/*/*/*.jar"
    
    # MongoDB相关
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.mongodb/*/*/*.jar"
    
    # 其他依赖
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/com.google.code.gson/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.slf4j/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/ch.qos.logback/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/org.projectlombok/*/*/*.jar"
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/caches/modules-2/files-2.1/jakarta.annotation/*/*/*.jar"
fi

echo "✅ Classpath已构建"
echo ""

# 第三步：运行测试
echo "🚀 步骤3: 启动测试程序..."
echo "========================================"
echo ""

# 使用Spring Boot启动
java -cp "$CLASSPATH" \
    -Dspring.profiles.active=test \
    -Dlogging.level.root=INFO \
    com.zq.TradingOperationsTest "$@"

EXIT_CODE=$?

echo ""
echo "========================================"
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ 测试程序正常退出"
else
    echo "❌ 测试程序异常退出 (退出码: $EXIT_CODE)"
fi
echo "========================================"

exit $EXIT_CODE

