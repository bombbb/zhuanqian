db.strategy_config.insertOne({
_id: "USDCUSDT_TESTNET",
symbol: "USDCUSDT",
mode: "TESTNET",
enabled: true,

minProfitTick: 0.0001,
maxHoldSeconds: 60,
tradeAmountUsdt: 100,

minSupportRatio: 0.6,
basePrice: 1.0005,

supportRangeNear: 0.001,
supportRangeMid: 0.002,
supportRangeFar: 0.003,

testnetApiUrl: "https://testnet.binance.vision",
testnetApiKey: "xxx",
testnetSecretKey: "xxx",

productionApiUrl: "https://api.binance.com",
productionApiKey: "",
productionSecretKey: ""
});
