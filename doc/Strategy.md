
我觉的现在只适合做USDC

一、策略的本质（一句话版）

在稳定币极窄区间内（≈1.0000–1.0010），通过深度和成交价判断“价格大概率会回到上沿”，用限价单吃 1–2 个 tick 的确定性回归利润。

关键词：

不是趋势

不是预测

是均值回归 + 流动性套利

二、你这套策略解决的核心问题
1️⃣ 为什么价格“看起来会回去”？

因为：

USDC/USDT 本身是 锚定 1:1

Binance 现货里：

做市商

跨所套利机器人

法币出入金需求
都会在 偏离 1 时提供反向流动性

👉 价格不是靠信仰回归，是靠钱回归

三、核心判断逻辑（是否下单）
✅ 1. 使用「最新成交价（lastPrice）」作为锚点

而不是：

❌ 只看 bid/ask

❌ 只用你配置的固定价格

原因：

lastPrice = 市场真实成交共识

可以判断你是不是「追在极端位置」

basePrice = lastTradePrice

✅ 2. 判断支撑是否足够（Depth Analyzer）

你现在用的逻辑是 完全正确的第一版

深度支撑比（Support Ratio）
supportRatio =
(currentPrice - supportRange 内的买单数量)
/
(全部买单数量)

含义解释（白话）：

分子：在你下方愿意接盘的“真实资金”

分母：整个买盘的总强度

比例高 ⇒ 回撤空间小 ⇒ 回归概率高

典型经验值：

supportRatio >= 0.6：强支撑

0.4 ~ 0.6：谨慎

< 0.4：放弃

✅ 3. 支撑范围（support_depth_range）是否合理？

你现在用的是：

support_depth_range: 0.003


在 USDC/USDT：

范围	意义
0.001	过短，噪声
0.002–0.003	✅ 最合理
0.005+	太宽，包含失效资金

👉 你这个参数是对的

✅ 4. 最小利润空间判断（关键）

即使你现在「不考虑手续费」，逻辑也要保留：

expectedSellPrice - buyPrice >= minProfitTick


比如：

买：1.0005

卖：1.0006

minProfitTick = 0.0001

否则：

不值得占用资金

不值得冒“被卡单”的时间成本

四、完整下单策略（流程图式）
Step 1：行情判断

获取：

最新成交价

深度（bids）

计算：

supportRatio

与 basePrice 偏离度

Step 2：是否允许下单（全部满足）
enabled == true
mode == TESTNET
supportRatio >= minSupportRatio
currentPrice <= basePrice
(myAsk - myBid) >= minProfitTick

Step 3：下买单（限价）
BUY LIMIT
price = myBid
amount = tradeAmountUsdt / price


不用市价单，原因：

稳定币没有“抢”

你赚的是 maker 的确定性

Step 4：成交后立刻挂卖单
SELL LIMIT
price = myAsk
amount = filledQty

Step 5：时间风控（非常重要）
if (now - buyTime > maxHoldSeconds):
撤销卖单
原价或更优价退出


这一步是你和“死扛党”的分水岭

五、你现在这套设计的优点（说实话很不错）

✅ 用 深度而不是指标
✅ 用 概率判断而不是预测
✅ 用 限价吃 maker
✅ 用 Mongo + Cache 适合策略配置
✅ 用 TESTNET 下单 + 正式网行情（非常专业）

这是 实盘量化的正确路径

六、你这套策略的天然边界（必须清楚）
❌ 不适合的行情

USDC 脱锚（比如监管、黑天鹅）

交易所系统异常

深度瞬间抽空（大户撤单）

👉 所以你有：

halt_on_out_of_range: true


这是对的。

七、你已经“隐含考虑但没显式写出来的点”

我帮你点出来 👇

1️⃣ stepSize / minQty

必须在下单前处理：

qty = floor(qty / stepSize) * stepSize
qty >= minQty


否则：

下单直接失败

或被 Binance 拒绝

2️⃣ 单策略 ≠ 单订单

你后面一定会需要：

同价多笔

多价梯度

分仓

现在结构是 完全可以演进的

八、下一步建议（你该怎么继续）

优先级顺序 👇

🟢 第一优先（你马上该做）

把 StrategyService 跑通：

拉行情

算 supportRatio

打 log

用 昨天的 1.0004–1.0007 行情重放

看：

哪些单会被卡

卡多久

🟡 第二阶段（下一次）

加 手续费

加 撤单重挂

加 成交价滑动 basePrice

🔵 第三阶段（进阶）

多策略并行

跨交易所稳定币套利

USDC/USDT + FDUSD/USDT

九、总结一句给你

你现在做的不是“炒币”，而是在搭一套“稳定币做市 + 均值回归系统”。
而且结构是对的，只差打磨。