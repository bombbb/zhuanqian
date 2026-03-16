package com.zq;

import com.zq.strategy.StrategyConfig;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Configuration
@Profile("test")
public class TestStrategyConfigSeeder {

    @Bean
    public ApplicationRunner seedStrategyConfig(MongoTemplate mongoTemplate) {
        return args -> {
            mongoTemplate.remove(
                query(where("symbol").is("USDCUSDT")),
                StrategyConfig.class
            );

            StrategyConfig config = new StrategyConfig();
            config.setId("USDCUSDT_TESTNET");
            config.setSymbol("USDCUSDT");
            config.setMode(StrategyConfig.Mode.TESTNET);
            config.setEnabled(true);

            config.setReferencePrice(1.0000);
            config.setMaxBuyPrice(1.0004);
            config.setMaxBuyAmountUsdt(300.0);
            config.setMinProfitTick(0.0001);
            config.setMaxHoldSeconds(1800);
            config.setMaxTotalInvestUsdt(500.0);
            config.setMaxBuyOpenSeconds(120);
            config.setMaxBuyPriceCeiling(1.0004);

            config.setMinSupportRatio(0.6);
            config.setSupportRangeNear(0.0025);
            config.setSupportRangeMid(0.005);
            config.setSupportRangeFar(0.01);
            
            config.setEnableRecentPriceBandAdjust(true);
            config.setRecentPriceLookbackDays(30);
            config.setRecentPricePercentile(0.85);
            config.setRecentPriceSafetyMargin(0.00005);

            config.setTestnetApiUrl("https://testnet.binance.vision");
            config.setTestnetApiKey("test");
            config.setTestnetSecretKey("test");
            config.setMarketDataWsUrl("wss://stream.binance.com:9443");

            mongoTemplate.insert(config);
        };
    }
}
