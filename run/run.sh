#!/bin/bash

# ========================================
# 套利策略启动/停止脚本（JAR运行方式）
# ========================================
# 用法:
#   ./run.sh start   - 启动应用（编译打包JAR并运行）
#   ./run.sh stop    - 停止应用
#   ./run.sh restart - 重启应用
#   ./run.sh status  - 查看应用状态
# 
# 特点:
#   - 编译打包成JAR文件 (bootJar)
#   - 使用 java -jar 方式运行（稳定、资源占用少）
#   - Mac系统使用caffeinate防止休眠
#   - 支持屏幕息屏时继续运行
# 
# Mac电脑说明:
#   ✓ 屏幕可以息屏，应用继续运行
#   ⚠ 合上笔记本盖子时进程会暂停（硬件限制）
#   💡 建议设置Mac永不休眠: sudo pmset -c sleep 0
# ========================================

set -e  # 遇到错误立即退出

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 获取项目根目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
PID_FILE="$PROJECT_ROOT/run/app.pid"
LOG_DIR="$PROJECT_ROOT/logs"
DOCKER_BIN="/Volumes/ap/application/Docker.app/Contents/Resources/bin/docker"

# 确保日志目录存在
mkdir -p "$LOG_DIR"

# 查找运行中的应用进程
find_app_process() {
    # 查找包含zhuanqian-1.0-SNAPSHOT.jar的Java进程
    ps aux | grep "java" | grep "zhuanqian-1.0-SNAPSHOT.jar" | grep -v grep | awk '{print $2}'
}

# 停止应用
stop_app() {
    echo -e "${YELLOW}正在停止应用...${NC}"
    
    local pids=$(find_app_process)
    
    if [ -z "$pids" ]; then
        echo -e "${BLUE}应用未运行${NC}"
        # 清理PID文件
        [ -f "$PID_FILE" ] && rm -f "$PID_FILE"
        return 0
    fi
    
    # 停止所有找到的进程
    for pid in $pids; do
        echo -e "${YELLOW}停止进程 $pid ...${NC}"
        kill $pid 2>/dev/null || true
        
        # 等待进程结束（最多10秒）
        for i in {1..10}; do
            if ! ps -p $pid > /dev/null 2>&1; then
                echo -e "${GREEN}✓ 进程 $pid 已停止${NC}"
                break
            fi
            sleep 1
            
            # 如果10秒后还没停止，强制杀死
            if [ $i -eq 10 ]; then
                echo -e "${RED}进程 $pid 未响应，强制停止...${NC}"
                kill -9 $pid 2>/dev/null || true
                sleep 1
            fi
        done
    done
    
    # 清理PID文件
    [ -f "$PID_FILE" ] && rm -f "$PID_FILE"
    
    echo -e "${GREEN}✓ 应用已停止${NC}"
}

# 启动应用
start_app() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}启动套利策略应用${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    # 1. 先停止已有进程
    local existing_pids=$(find_app_process)
    if [ -n "$existing_pids" ]; then
        echo -e "${YELLOW}检测到运行中的应用进程，先停止...${NC}"
        stop_app
        echo ""
    fi
    
    # 2. 检查MongoDB
    echo -e "${YELLOW}检查MongoDB状态...${NC}"
    if "$DOCKER_BIN" ps | grep -q mongo; then
        echo -e "${GREEN}✓ MongoDB正在运行${NC}"
    else
        echo -e "${RED}✗ MongoDB未运行${NC}"
        echo -e "${YELLOW}启动MongoDB: cd $SCRIPT_DIR && $DOCKER_BIN compose -f docker-compose.yml up -d${NC}"
        exit 1
    fi
    echo ""
    
    # 3. 切换到项目根目录
    cd "$PROJECT_ROOT"
    
    # 3.5. 编译打包JAR文件
    echo -e "${YELLOW}编译打包项目（生成JAR文件）...${NC}"
    if ./gradlew clean bootJar > "$LOG_DIR/build.log" 2>&1; then
        echo -e "${GREEN}✓ 编译打包完成${NC}"
    else
        echo -e "${RED}✗ 编译打包失败，请查看日志: $LOG_DIR/build.log${NC}"
        exit 1
    fi
    
    # 3.6. 检查JAR文件是否存在
    JAR_FILE="$PROJECT_ROOT/build/libs/zhuanqian-1.0-SNAPSHOT.jar"
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}✗ JAR文件不存在: $JAR_FILE${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ JAR文件已生成: $JAR_FILE${NC}"
    echo ""
    
    # 4. 启动应用（后台运行，支持息屏继续运行）
    echo -e "${YELLOW}启动应用（使用JAR文件）...${NC}"
    
    # Mac专属：使用caffeinate防止系统休眠 + nohup后台运行
    # caffeinate -i: 防止系统空闲休眠（屏幕可以息屏，但进程继续运行）
    # nohup: 忽略挂断信号
    # disown: 将进程从当前shell中移除
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # Mac系统：使用caffeinate + nohup + java -jar
        echo -e "${BLUE}检测到Mac系统，使用caffeinate防止休眠${NC}"
        nohup caffeinate -dims java -jar "$JAR_FILE" > "$LOG_DIR/app.log" 2>&1 &
    else
        # Linux系统：只使用nohup + java -jar
        nohup java -jar "$JAR_FILE" > "$LOG_DIR/app.log" 2>&1 &
    fi
    
    local pid=$!
    disown -h $pid 2>/dev/null || true
    
    # 保存PID
    echo $pid > "$PID_FILE"
    
    echo -e "${GREEN}✓ 应用已启动 (PID: $pid)${NC}"
    echo ""
    
    # 5. 等待应用启动（检查日志）
    echo -e "${YELLOW}等待应用初始化...${NC}"
    for i in {1..30}; do
        if [ -f "$LOG_DIR/application.log" ] && grep -q "Started TradingApplication" "$LOG_DIR/application.log"; then
            echo -e "${GREEN}✓ 应用启动成功！${NC}"
            break
        fi
        
        # 检查进程是否还在运行
        if ! ps -p $pid > /dev/null 2>&1; then
            echo -e "${RED}✗ 应用启动失败，进程已退出${NC}"
            echo -e "${YELLOW}查看日志: tail -f $LOG_DIR/application.log${NC}"
            exit 1
        fi
        
        sleep 1
        
        if [ $i -eq 30 ]; then
            echo -e "${YELLOW}⚠ 应用仍在启动中，请稍后检查日志${NC}"
        fi
    done
    
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${GREEN}应用启动完成！${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    echo -e "${YELLOW}监控命令:${NC}"
    echo -e "  应用日志: tail -f $LOG_DIR/application.log"
    echo -e "  交易日志: tail -f $LOG_DIR/trade.log"
    echo -e "  行情日志: tail -f $LOG_DIR/price.log"
    echo -e "  控制台日志: tail -f $LOG_DIR/app.log"
    echo -e "  停止应用: $0 stop"
    echo ""
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo -e "${BLUE}Mac使用提示:${NC}"
        echo -e "  ✓ 应用使用caffeinate防止系统休眠"
        echo -e "  ✓ 屏幕可以息屏，应用继续运行"
        echo -e "  ⚠ 合上笔记本盖子时进程会暂停"
        echo -e "  💡 建议：在系统偏好设置中延长「防止Mac自动进入睡眠」时间"
        echo -e "  💡 或者使用: pmset -g 查看电源管理设置"
        echo ""
    fi
}

# 显示状态
show_status() {
    local pids=$(find_app_process)
    
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}应用状态${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    if [ -z "$pids" ]; then
        echo -e "${YELLOW}应用未运行${NC}"
    else
        echo -e "${GREEN}应用正在运行${NC}"
        for pid in $pids; do
            echo -e "  进程ID: $pid"
            # 显示进程运行时间
            ps -p $pid -o etime= | sed 's/^/  运行时间: /'
            # 显示内存使用
            ps -p $pid -o rss= | awk '{printf "  内存使用: %.2f MB\n", $1/1024}'
        done
    fi
    
    echo ""
    
    # 检查JAR文件
    JAR_FILE="$PROJECT_ROOT/build/libs/zhuanqian-1.0-SNAPSHOT.jar"
    if [ -f "$JAR_FILE" ]; then
        echo -e "${GREEN}✓ JAR文件存在${NC}"
        ls -lh "$JAR_FILE" | awk '{print "  大小: " $5 "  修改时间: " $6 " " $7 " " $8}'
    else
        echo -e "${RED}✗ JAR文件不存在: $JAR_FILE${NC}"
    fi
    
    echo ""
    
    # 检查MongoDB
    if docker ps | grep -q mongo; then
        echo -e "${GREEN}✓ MongoDB正在运行${NC}"
    else
        echo -e "${RED}✗ MongoDB未运行${NC}"
    fi
    
    echo ""
}

# 主逻辑
case "$1" in
    start)
        start_app
        ;;
    stop)
        stop_app
        ;;
    restart)
        stop_app
        echo ""
        start_app
        ;;
    status)
        show_status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "  start   - 启动应用（先停止已有进程）"
        echo "  stop    - 停止应用"
        echo "  restart - 重启应用"
        echo "  status  - 查看应用状态"
        echo ""
        exit 1
        ;;
esac

exit 0
