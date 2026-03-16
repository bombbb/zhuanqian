# 快速启动指南 🚀

## 一键启动（推荐）⭐

```bash
# 进入运行目录
cd /Users/bao/java/zhuanqian/run

# 启动应用（自动编译打包JAR并运行）
./run.sh start

# 查看日志
tail -f ../logs/application.log
```

---

## 常用命令 📋

### 应用管理
```bash
cd /Users/bao/java/zhuanqian/run

# 启动（自动编译打包JAR）
./run.sh start

# 停止
./run.sh stop

# 重启
./run.sh restart

# 状态
./run.sh status
```

### 日志查看
```bash
cd /Users/bao/java/zhuanqian

# 应用日志
tail -f logs/application.log

# 交易日志
tail -f logs/trade.log

# 行情日志
tail -f logs/price.log

# 控制台日志
tail -f logs/app.log

# 搜索错误
tail -100 logs/application.log | grep "ERROR"
```

### MongoDB管理
```bash
cd /Users/bao/java/zhuanqian/run

# 启动
docker-compose up -d

# 停止
docker-compose down

# 查看状态
docker ps | grep mongo
```

---

## Mac息屏设置 🍎

### 方法1：命令行（推荐）
```bash
# 设置插电时永不休眠
sudo pmset -c sleep 0

# 查看当前设置
pmset -g
```

### 方法2：系统偏好设置
1. 打开 `系统偏好设置`
2. 点击 `电池`
3. 选择 `电源适配器`
4. ✓ 勾选 **"防止Mac自动进入睡眠"**

---

## 验证运行 ✅

```bash
cd /Users/bao/java/zhuanqian

# 1. 检查应用进程
ps aux | grep "zhuanqian-1.0-SNAPSHOT.jar" | grep -v grep

# 2. 检查caffeinate进程（防休眠）
ps aux | grep caffeinate | grep -v grep

# 3. 查看最新日志
tail -20 logs/application.log

# 4. 查看应用状态
cd run && ./run.sh status
```

---

## 运行方式说明 📝

### ✅ 现在的方式（JAR运行）
- 编译打包成JAR文件（`./gradlew clean bootJar`）
- 使用 `java -jar` 方式运行
- **更稳定，资源占用少（~300MB）**
- **启动速度快（10-20秒）**
- 适合7x24小时长期运行

### 特点
- ✅ 自动编译打包
- ✅ Mac息屏继续运行（caffeinate）
- ✅ 进程完全脱离终端（nohup + disown）
- ✅ 显示内存使用和JAR文件信息

---

## 故障排查 🔍

### 应用启动失败
```bash
# 查看错误日志
tail -100 logs/application.log

# 查看编译日志
tail -100 logs/build.log

# 查看控制台日志
tail -100 logs/app.log

# 检查MongoDB
docker ps | grep mongo

# 检查端口占用
lsof -i :8080

# 检查JAR文件
ls -lh build/libs/zhuanqian-1.0-SNAPSHOT.jar
```

### 息屏后应用停止
```bash
# 检查caffeinate进程
ps aux | grep caffeinate

# 检查系统休眠设置
pmset -g | grep sleep

# 重新启动应用
cd run
./run.sh restart
```

### 编译失败
```bash
# 清理重新编译
./gradlew clean
./gradlew bootJar

# 查看错误
tail -100 logs/build.log
```

---

## 重要提醒 ⚠️

### ✅ 可以做的
- ✅ 屏幕息屏（应用继续运行）
- ✅ 使用其他应用
- ✅ 锁定屏幕

### ❌ 不要做的
- ❌ 合上笔记本盖子（会暂停应用）
- ❌ 手动点击"睡眠"（会暂停应用）
- ❌ 拔掉电源（可能电量耗尽）

---

## 推荐配置 💡

### 生产环境（7x24小时运行）
1. ✅ 使用 `./run.sh start` 启动（自动JAR方式）
2. ✅ 设置Mac永不休眠：`sudo pmset -c sleep 0`
3. ✅ 保持电源连接
4. ✅ 不要合盖
5. ✅ 每天检查一次日志

---

## 启动流程说明 🔄

当你运行 `./run.sh start` 时，脚本会自动执行：

1. **停止旧进程**（如果有）
2. **检查MongoDB**（必须运行）
3. **编译打包JAR** (`./gradlew clean bootJar`)
4. **验证JAR文件**（build/libs/zhuanqian-1.0-SNAPSHOT.jar）
5. **启动应用**：
   - Mac系统：`caffeinate -i java -jar xxx.jar`
   - Linux系统：`java -jar xxx.jar`
6. **等待启动完成**（检查日志）
7. **显示监控命令**

---

## 性能对比 📊

| 特性 | 原Gradle方式 | 现JAR方式 |
|------|-------------|-----------|
| 启动命令 | `./gradlew bootRun` | `java -jar xxx.jar` |
| 资源占用 | ~500MB | ~300MB ✓ |
| 启动速度 | 30-60秒 | 10-20秒 ✓ |
| 稳定性 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ ✓ |
| 息屏支持 | ✓ | ✓ |
| 推荐度 | 开发环境 | **生产环境** ⭐ |

---

## 帮助文档 📚

- **[MAC_SLEEP_FIX_SUMMARY.md](MAC_SLEEP_FIX_SUMMARY.md)** - 修复总结
- **[MAC_SLEEP_SOLUTION.md](MAC_SLEEP_SOLUTION.md)** - 详细解决方案
- **[run/README.md](run/README.md)** - 启动脚本说明
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - 项目总结

---

**快速联系**：如有问题，请查看上述文档或检查日志文件。

**最后更新**：2026-01-19 - 统一使用JAR方式运行
