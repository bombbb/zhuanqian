#!/bin/bash

# ========================================
# Mac息屏测试脚本
# ========================================
# 用途：测试应用在Mac息屏后是否继续运行
# 使用：./test-sleep-resistance.sh
# ========================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
LOG_DIR="$SCRIPT_DIR/logs"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Mac息屏抗性测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查应用是否运行
echo -e "${YELLOW}1. 检查应用状态...${NC}"
if ps aux | grep "java" | grep -E "(TradingApplication|zhuanqian-1.0-SNAPSHOT.jar)" | grep -v grep > /dev/null; then
    echo -e "${GREEN}✓ 应用正在运行${NC}"
    APP_PID=$(ps aux | grep "java" | grep -E "(TradingApplication|zhuanqian-1.0-SNAPSHOT.jar)" | grep -v grep | awk '{print $2}' | head -1)
    echo -e "  进程ID: $APP_PID"
else
    echo -e "${RED}✗ 应用未运行${NC}"
    echo -e "${YELLOW}请先启动应用：${NC}"
    echo -e "  cd run && ./run-jar.sh start"
    exit 1
fi
echo ""

# 2. 检查caffeinate进程
echo -e "${YELLOW}2. 检查caffeinate进程...${NC}"
if ps aux | grep "caffeinate" | grep -v grep > /dev/null; then
    echo -e "${GREEN}✓ caffeinate正在运行${NC}"
    CAFFEINATE_PID=$(ps aux | grep "caffeinate" | grep -v grep | awk '{print $2}' | head -1)
    echo -e "  进程ID: $CAFFEINATE_PID"
else
    echo -e "${RED}✗ caffeinate未运行${NC}"
    echo -e "${YELLOW}建议：使用run-jar.sh或run.sh启动应用${NC}"
fi
echo ""

# 3. 检查系统电源设置
echo -e "${YELLOW}3. 检查系统电源设置...${NC}"
SLEEP_SETTING=$(pmset -g | grep "^[ ]*sleep" | awk '{print $2}')
echo -e "  当前休眠设置: ${SLEEP_SETTING} 分钟"

if [ "$SLEEP_SETTING" = "0" ]; then
    echo -e "${GREEN}✓ 系统设置为永不休眠${NC}"
else
    echo -e "${YELLOW}⚠ 系统会在 ${SLEEP_SETTING} 分钟后休眠${NC}"
    echo -e "${YELLOW}建议：sudo pmset -c sleep 0${NC}"
fi
echo ""

# 4. 记录当前日志位置
echo -e "${YELLOW}4. 记录当前日志位置...${NC}"
if [ -f "$LOG_DIR/application.log" ]; then
    BEFORE_LINES=$(wc -l < "$LOG_DIR/application.log")
    BEFORE_TIME=$(tail -1 "$LOG_DIR/application.log" | awk '{print $1, $2}')
    echo -e "${GREEN}✓ 日志文件存在${NC}"
    echo -e "  当前行数: $BEFORE_LINES"
    echo -e "  最后时间: $BEFORE_TIME"
else
    echo -e "${RED}✗ 日志文件不存在${NC}"
    exit 1
fi
echo ""

# 5. 开始测试
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}开始测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}测试步骤：${NC}"
echo -e "  1. 让Mac屏幕息屏（等待或按 Control + Shift + Power）"
echo -e "  2. 等待 5 分钟"
echo -e "  3. 唤醒屏幕"
echo -e "  4. 运行此脚本检查结果"
echo ""
echo -e "${YELLOW}按Enter键开始记录，然后让屏幕息屏...${NC}"
read -r

echo -e "${GREEN}✓ 已记录当前状态${NC}"
echo ""
echo -e "${YELLOW}请执行以下操作：${NC}"
echo -e "  1. 让屏幕息屏（等待或按 Control + Shift + Power）"
echo -e "  2. 等待 5 分钟"
echo -e "  3. 唤醒屏幕后，再次运行此脚本查看结果"
echo ""
echo -e "${BLUE}重新运行命令：${NC}"
echo -e "  ./test-sleep-resistance.sh check"
echo ""

# 保存测试状态
echo "$BEFORE_LINES" > /tmp/sleep-test-before-lines
echo "$BEFORE_TIME" > /tmp/sleep-test-before-time
echo "$(date +%s)" > /tmp/sleep-test-start-time

exit 0

