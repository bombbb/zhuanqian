# 应用启动脚本说明

## 📋 启动脚本：`run.sh`

**唯一的启动脚本**，功能完整、稳定可靠

### 使用方法

```bash
./run.sh start    # 启动应用（编译打包JAR并运行）
./run.sh stop     # 停止应用
./run.sh restart  # 重启应用
./run.sh status   # 查看状态
```

### 特点

- ✅ **自动编译打包JAR** (`./gradlew clean bootJar`)
- ✅ **使用JAR方式运行** (`java -jar xxx.jar`)
- ✅ **更稳定** - 不依赖Gradle守护进程
- ✅ **资源占用少** - 约300MB内存
- ✅ **启动速度快** - 10-20秒
- ✅ **Mac息屏继续运行** - 使用caffeinate防止休眠
- ✅ **进程完全脱离终端** - nohup + disown

---

## 🍎 Mac电脑特别说明

### 已解决问题
- ✅ **屏幕息屏**后应用继续运行（使用`caffeinate -i`）
- ✅ 自动防止系统空闲休眠
- ✅ 进程完全脱离终端

### 仍会暂停的情况
- ⚠️ **合上笔记本盖子**（硬件限制，打开后自动恢复）
- ⚠️ 手动点击"睡眠"（用户操作）
- ⚠️ 电量耗尽（硬件限制）

### 推荐设置（避免暂停）

#### 方法1：系统偏好设置（推荐）✨
1. 打开 `系统偏好设置` → `电池` → `电源适配器`
2. ✓ 勾选 **"防止Mac自动进入睡眠"**
3. 设置 **"显示器关闭时间"** = `15分钟`（或更长）

#### 方法2：命令行设置
```bash
# 设置插电时永不休眠
sudo pmset -c sleep 0

# 设置显示器15分钟后息屏
sudo pmset -c displaysleep 15
```

---

## 🚀 快速开始

### 首次启动

```bash
# 1. 确保MongoDB运行
docker-compose up -d

# 2. 启动应用
./run.sh start

# 3. 查看启动日志
tail -f ../logs/application.log

# 4. 等待启动完成（看到"Started TradingApplication"）
```

### 日常监控

```bash
# 查看应用状态
./run.sh status

# 查看应用日志
tail -f ../logs/application.log

# 查看交易日志
tail -f ../logs/trade.log

# 查看行情日志
tail -f ../logs/price.log

# 查看控制台日志
tail -f ../logs/app.log

# 搜索错误日志
tail -100 ../logs/application.log | grep "ERROR"
```

### 停止/重启

```bash
# 停止应用
./run.sh stop

# 重启应用
./run.sh restart
```

---

## 📊 运行方式

### 启动流程

当你运行 `./run.sh start` 时，脚本自动执行：

1. **停止旧进程**（如果存在）
2. **检查MongoDB状态**（必须运行）
3. **编译打包项目**：
   ```bash
   ./gradlew clean bootJar
   ```
   生成：`build/libs/zhuanqian-1.0-SNAPSHOT.jar`

4. **验证JAR文件**（确保文件存在）

5. **启动应用**：
   - **Mac系统**：`caffeinate -i java -jar xxx.jar`
   - **Linux系统**：`java -jar xxx.jar`

6. **等待启动完成**（检查日志中的"Started TradingApplication"）

7. **显示监控命令**

### 技术细节

```bash
# Mac系统启动命令
nohup caffeinate -i java -jar build/libs/zhuanqian-1.0-SNAPSHOT.jar > logs/app.log 2>&1 &

# Linux系统启动命令
nohup java -jar build/libs/zhuanqian-1.0-SNAPSHOT.jar > logs/app.log 2>&1 &
```

**参数说明**：
- `nohup` - 忽略挂断信号（关闭终端不影响）
- `caffeinate -i` - Mac专用，防止系统空闲休眠
- `java -jar` - 运行JAR文件
- `> logs/app.log 2>&1` - 重定向输出到日志文件
- `&` - 后台运行
- `disown` - 将进程从shell中移除

---

## 🔍 故障排查

### 问题1：应用启动失败

```bash
# 查看启动日志
tail -100 ../logs/application.log

# 查看编译日志
tail -100 ../logs/build.log

# 查看控制台日志
tail -100 ../logs/app.log

# 检查MongoDB
docker ps | grep mongo

# 检查端口占用
lsof -i :8080
```

**常见原因**：
- MongoDB未启动 → `docker-compose up -d`
- 端口被占用 → 找出并停止占用进程
- 配置错误 → 检查数据库配置
- 内存不足 → 检查系统资源
- 编译失败 → 查看build.log

---

### 问题2：息屏后应用停止

```bash
# 检查caffeinate进程是否运行
ps aux | grep caffeinate

# 检查系统电源设置
pmset -g

# 查看睡眠设置（应该显示 sleep 0）
pmset -g | grep sleep
```

**解决方案**：
1. 确认使用的是最新版本的脚本
2. 设置Mac永不休眠（见上文"推荐设置"）
3. 不要合盖或手动点击睡眠

---

### 问题3：找不到JAR文件

```bash
# 检查JAR文件
ls -lh ../build/libs/zhuanqian-1.0-SNAPSHOT.jar

# 如果不存在，手动编译
cd ..
./gradlew clean bootJar

# 再次检查
ls -lh build/libs/zhuanqian-1.0-SNAPSHOT.jar
```

---

### 问题4：编译失败

```bash
# 查看编译日志
tail -100 ../logs/build.log

# 清理后重新编译
cd ..
./gradlew clean
./gradlew bootJar
```

**常见原因**：
- 代码语法错误
- 依赖下载失败（网络问题）
- Gradle版本不兼容
- 磁盘空间不足

---

## 📝 日志位置

所有日志文件位于 `../logs/` 目录：

```
logs/
├── application.log      # 应用主日志（Spring Boot）
├── trade.log           # 交易日志
├── price.log           # 行情日志
├── app.log             # 控制台输出（java -jar）
└── build.log           # 编译日志（bootJar）
```

---

## 📊 性能优势

相比Gradle bootRun方式：

| 特性 | Gradle bootRun | JAR方式 (现在) |
|------|----------------|----------------|
| **启动命令** | `./gradlew bootRun` | `java -jar xxx.jar` |
| **资源占用** | ~500MB | ~300MB ✓ |
| **启动速度** | 30-60秒 | 10-20秒 ✓ |
| **稳定性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ ✓ |
| **依赖** | Gradle守护进程 | 无依赖 ✓ |
| **适合场景** | 开发测试 | **生产运行** ⭐ |
| **息屏支持** | ✓ | ✓ |

---

## 💡 最佳实践

### 生产环境运行建议

1. **✅ 使用run.sh启动**
   ```bash
   ./run.sh start
   ```

2. **✅ 设置Mac永不休眠**（插电时）
   ```bash
   sudo pmset -c sleep 0
   ```

3. **✅ 不要合盖运行**
   - 使用外接显示器
   - 或保持笔记本打开

4. **✅ 保持电源连接**
   - 长期运行建议一直插电

5. **✅ 定期监控**
   ```bash
   # 每天检查一次
   ./run.sh status
   tail -20 ../logs/trade.log
   ```

6. **✅ 备份数据**
   ```bash
   # 定期备份数据库
   mongodump --uri="mongodb://localhost:27017/strategy_db" --out=backup/
   
   # 备份日志
   tar -czf logs-backup-$(date +%Y%m%d).tar.gz ../logs/
   ```

---

## 🔗 相关文档

- **[../QUICK_START.md](../QUICK_START.md)** - 快速启动指南
- **[../MAC_SLEEP_SOLUTION.md](../MAC_SLEEP_SOLUTION.md)** - Mac休眠问题详细解决方案
- **[../MAC_SLEEP_FIX_SUMMARY.md](../MAC_SLEEP_FIX_SUMMARY.md)** - 修复总结
- **[../IMPLEMENTATION_SUMMARY.md](../IMPLEMENTATION_SUMMARY.md)** - 项目实施总结

---

## 🎯 总结

### ✅ 核心特点
- 一个脚本搞定所有操作
- 自动编译打包JAR
- 稳定运行、资源占用少
- Mac息屏继续运行
- 完整的状态监控

### 💡 推荐使用场景
- ✅ 7x24小时生产环境运行
- ✅ 长期策略执行
- ✅ 资源受限的环境
- ✅ 需要稳定性的场景

---

**最后更新**：2026-01-19  
**版本**：v2.0（统一JAR方式）  
**维护者**：zhuanqian团队
