# Mac电脑休眠/息屏问题解决方案

## 问题描述
在Mac电脑上运行应用时，当电脑息屏或休眠后，应用进程会被挂起或终止。

## 解决方案

### 方案1：使用优化后的启动脚本（推荐）✨

已经为你准备了两个优化后的启动脚本：

#### 1. `run/run.sh` - Gradle版本（已优化）
使用Gradle运行应用，添加了Mac防休眠支持。

```bash
cd run
./run.sh start    # 启动应用
./run.sh stop     # 停止应用
./run.sh restart  # 重启应用
./run.sh status   # 查看状态
```

**特点**：
- ✅ 使用`caffeinate -i`防止系统空闲休眠
- ✅ 屏幕可以息屏，应用继续运行
- ⚠️ 通过Gradle运行，资源占用稍高

#### 2. `run/run-jar.sh` - JAR版本（更稳定）⭐推荐
直接运行JAR文件，更稳定、资源占用更少。

```bash
cd run
./run-jar.sh start    # 启动应用
./run-jar.sh stop     # 停止应用
./run-jar.sh restart  # 重启应用
./run-jar.sh status   # 查看状态
```

**特点**：
- ✅ 直接运行JAR文件，不依赖Gradle守护进程
- ✅ 使用`caffeinate -i`防止系统空闲休眠
- ✅ 屏幕可以息屏，应用继续运行
- ✅ 资源占用少，更适合长期运行
- ⭐ **推荐用于7x24小时运行**

---

## 工作原理

### `caffeinate`命令（Mac特有）
```bash
caffeinate -i <command>
```

**参数说明**：
- `-i`: 防止系统空闲休眠（Prevent idle sleep）
- `-d`: 防止显示器休眠
- `-m`: 防止磁盘休眠
- `-s`: 防止系统休眠（需要插电）

**我们使用`-i`参数**：
- ✅ 允许屏幕息屏（节省电量和屏幕寿命）
- ✅ 防止系统进入空闲休眠
- ✅ 应用进程继续运行
- ⚠️ 合上笔记本盖子时仍会暂停（硬件限制）

---

## 不同场景的表现

### 场景1：屏幕息屏（自动或手动）
**现象**：✅ 应用继续正常运行

**说明**：
- 使用`caffeinate -i`后，屏幕息屏不影响应用
- 系统不会进入休眠状态
- CPU、网络、磁盘正常工作

### 场景2：合上笔记本盖子
**现象**：⚠️ 应用会暂停，打开盖子后自动恢复

**说明**：
- 这是Mac的硬件设计，合盖即休眠
- 除非使用外接显示器并关闭内置屏幕
- **解决方案**：
  1. 使用外接显示器
  2. 使用第三方工具（如Amphetamine）
  3. 或者不要合盖

### 场景3：手动点击「睡眠」
**现象**：⚠️ 应用会暂停

**说明**：
- 手动休眠会挂起所有进程
- `caffeinate`无法阻止手动休眠
- **解决方案**：不要手动点击睡眠，让屏幕自动息屏即可

### 场景4：电量耗尽
**现象**：❌ 应用会停止

**说明**：
- 电量耗尽后Mac会强制关机
- **解决方案**：保持电源连接或及时充电

---

## 推荐的Mac设置

### 1. 系统偏好设置（推荐）✨

**路径**：`系统偏好设置` → `电池` → `电源适配器`

**设置项**：
- ✅ **防止Mac自动进入睡眠**（勾选）← 重要！
- ✅ **显示器关闭时间**：设置为`永不`或较长时间（如1小时）
- ✅ **在电源适配器上使电脑进入睡眠状态**：设置为`永不`

**效果**：
- 插电时电脑永不休眠
- 屏幕可以息屏
- 应用持续运行

### 2. 使用命令行设置（可选）

```bash
# 查看当前电源管理设置
pmset -g

# 设置插电时永不休眠
sudo pmset -c sleep 0

# 设置使用电池时1小时后休眠
sudo pmset -b sleep 60

# 设置显示器15分钟后息屏（节省电量）
sudo pmset -c displaysleep 15

# 恢复默认设置
sudo pmset -c sleep 10
```

### 3. 使用第三方工具（可选）

推荐工具：**Amphetamine**（免费，Mac App Store）

**功能**：
- 防止Mac休眠
- 支持合盖运行
- 可设置定时规则
- 支持触发器（如特定应用运行时）

---

## 监控和验证

### 1. 验证应用是否运行

```bash
# 方式1：使用脚本查看状态
cd run
./run-jar.sh status  # 或 ./run.sh status

# 方式2：查看进程
ps aux | grep "java" | grep -v grep

# 方式3：查看日志
tail -f logs/application.log
```

### 2. 测试息屏后是否继续运行

```bash
# 1. 启动应用
cd run
./run-jar.sh start

# 2. 查看日志（持续输出）
tail -f logs/application.log

# 3. 让屏幕息屏（等待或手动按键盘 Control + Shift + Power）

# 4. 等待一段时间（如5分钟）

# 5. 唤醒屏幕，检查日志是否持续输出
# 如果日志时间戳连续，说明应用一直在运行 ✓
```

### 3. 查看caffeinate进程

```bash
# 查看caffeinate进程
ps aux | grep caffeinate

# 输出示例：
# user  12345  0.0  0.0  caffeinate -i ./gradlew bootRun
```

---

## 故障排查

### 问题1：应用启动后很快停止

**检查**：
```bash
# 查看启动日志
tail -100 logs/application.log

# 查看错误信息
tail -100 logs/app.log  # JAR版本
tail -100 logs/nohup.log  # Gradle版本
```

**常见原因**：
- MongoDB未启动
- 配置错误
- 端口占用
- 内存不足

### 问题2：息屏后应用仍然停止

**检查**：
```bash
# 1. 确认caffeinate是否在运行
ps aux | grep caffeinate

# 2. 查看系统电源设置
pmset -g

# 3. 确认不是合盖或手动休眠
```

**解决**：
- 检查系统偏好设置中的休眠选项
- 确保勾选了"防止Mac自动进入睡眠"
- 使用`sudo pmset -c sleep 0`命令

### 问题3：进程被意外杀死

**检查**：
```bash
# 查看系统日志
log show --predicate 'process == "java"' --info --last 1h

# 或查看崩溃日志
ls ~/Library/Logs/DiagnosticReports/
```

**可能原因**：
- 系统内存不足被OOM Killer杀死
- 系统更新或安全扫描
- 磁盘空间不足

---

## 最佳实践建议

### 🌟 推荐配置（适合7x24小时运行）

1. **使用JAR版本启动脚本**
   ```bash
   cd run
   ./run-jar.sh start
   ```

2. **设置Mac永不休眠**（插电时）
   - 系统偏好设置 → 电池 → 电源适配器
   - ✓ 防止Mac自动进入睡眠
   - 显示器关闭：15分钟（节省电量）

3. **监控应用运行**
   ```bash
   # 定期检查应用状态
   cd run
   ./run-jar.sh status
   
   # 查看最新日志
   tail -20 logs/application.log
   ```

4. **保持电源连接**
   - 长期运行建议一直插电
   - 或确保电池充足

5. **定期检查日志**
   ```bash
   # 每天检查一次日志
   tail -100 logs/trade.log | grep "ERROR\|WARN"
   ```

### 🔄 启动方式对比

| 特性 | `run.sh` (Gradle) | `run-jar.sh` (JAR) |
|------|-------------------|---------------------|
| 稳定性 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 资源占用 | 中等 | 低 |
| 启动速度 | 较慢 | 快 |
| 适合场景 | 开发测试 | 生产运行 |
| 推荐度 | 开发环境 | **生产环境** ⭐ |

---

## 总结

### ✅ 已解决的问题
- Mac息屏时应用继续运行
- 使用`caffeinate`防止空闲休眠
- 提供了两种启动方式（Gradle版和JAR版）
- 添加了详细的日志和状态监控

### ⚠️ 仍然会暂停的情况
- 合上笔记本盖子（硬件限制）
- 手动点击"睡眠"（用户操作）
- 电量耗尽（硬件限制）

### 💡 建议
1. **推荐使用`run-jar.sh`**启动应用（更稳定）
2. **设置Mac永不休眠**（插电时）
3. **不要合盖**运行，或使用外接显示器
4. **保持电源连接**
5. **定期检查应用状态**和日志

---

## 快速开始

```bash
# 1. 进入运行目录
cd /Users/bao/java/zhuanqian/run

# 2. 启动应用（推荐JAR版本）
./run-jar.sh start

# 3. 查看状态
./run-jar.sh status

# 4. 查看日志
tail -f ../logs/application.log

# 5. 如需停止
./run-jar.sh stop
```

---

**最后更新**：2026-01-19
**适用系统**：macOS (darwin)
**测试版本**：macOS Sonoma 14.6

