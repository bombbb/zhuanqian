<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
# Build project
./gradlew build

# Run all unit tests (excludes integration and manual tags)
./gradlew test

# Run application
./gradlew bootRun

# Run manual config update task
./gradlew updateMongoConfig

# Run specific test class
./gradlew test --tests "com.zq.api.BinanceApiServiceTest"

# Run specific test method
./gradlew test --tests "com.zq.api.BinanceApiServiceTest.testGetBalance"
```

## Architecture Overview

This is a USDC/USDT stablecoin arbitrage trading system using mean reversion strategy on Binance testnet.

### Core Trading Flow
1. **WebSocket** receives real-time market data → `BinanceWebSocketClient`
2. **StrategyEngine** evaluates buy conditions using `DepthAnalyzer`
3. On buy signal: place limit buy order, then immediately place limit sell order
4. Monitor orders: if sell not filled within `maxHoldSeconds`, cancel and market sell

### Key Packages

- **`com.zq.api`** - Binance API integration (`BinanceApiService`) and WebSocket (`BinanceWebSocketClient`)
- **`com.zq.strategy`** - Trading logic
  - `StrategyEngine` - orchestrates trading decisions
  - `StrategyConfig` - MongoDB-stored configuration (collection: `strategy_config`)
  - `DepthAnalyzer` - calculates order book support ratio
  - `DynamicPriceAdjuster` - adjusts maxBuyPrice based on trends
  - `TrendAnalyzer` - analyzes historical price trends
- **`com.zq.order`** - Order entity and `OrderService` (async writes with virtual threads)
- **`com.zq.position`** - Position tracking and P&L calculation
- **`com.zq.stats`** - Aggregate statistics (spread, depth, trade stats)

### Strategy Configuration

Configuration is stored in MongoDB (`strategy_config` collection), not in application.yml. Each config has an ID like `USDCUSDT_TESTNET`. Key fields:
- `maxBuyPrice` - maximum price threshold for buying
- `maxBuyAmountUsdt` - maximum single trade amount
- `minProfitTick` - minimum profit spread (e.g., 0.0001 = 0.01%)
- `maxHoldSeconds` - timeout for sell order before market close
- `minSupportRatio` - minimum bid-side support ratio (0-1)

See `doc/db/init-strategy-config.js` for initialization.

### Database

MongoDB with collections: `strategy_config`, `orders`, `positions`, `spread_stats`, `depth_stats`, `trade_stats`, `config_change_logs`

Connection: `mongodb://admin:admin123@localhost:27017/strategy_db`

## Running the Application

1. Start MongoDB: `cd run && docker-compose up -d`
2. Initialize config: `mongosh mongodb://localhost:27017/strategy_db < doc/db/init-strategy-config.js`
3. Run: `./gradlew bootRun`

## Test Categories

Tests are tagged using JUnit 5:
- Default tests (no tag): unit tests, run with `./gradlew test`
- `@Tag("integration")`: requires MongoDB connection, excluded from default test run
- `@Tag("manual")`: manual configuration tasks, run with `./gradlew updateMongoConfig`

## Important Notes

- **Testnet only**: All trades execute on Binance testnet, but market data uses production WebSocket
- **Virtual threads**: MongoDB writes use `Thread.startVirtualThread()` for async operations
- **API keys**: Stored in MongoDB strategy_config, initialized via init script
- **No parameter auto-update**: `ParameterOptimizer` provides suggestions but doesn't modify config automatically