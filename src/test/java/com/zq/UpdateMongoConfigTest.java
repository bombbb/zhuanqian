package com.zq;

import com.zq.strategy.StrategyConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.mongodb.core.query.Criteria;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * 手动执行的 MongoDB 配置更新测试
 * 运行：./gradlew updateMongoConfig
 */
@DataMongoTest
@TestPropertySource(locations = "classpath:application-test.yml")
@Tag("manual")
class UpdateMongoConfigTest {

    private static final Logger log = LoggerFactory.getLogger(UpdateMongoConfigTest.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void updateStrategyConfig() {
        String symbol = "USDCUSDT";

        // 优先更新 SIMULATION 配置；若不存在则更新当前启用配置
        StrategyConfig config = mongoTemplate.findOne(
            query(where("symbol").is(symbol).and("mode").is(StrategyConfig.Mode.SIMULATION)),
            StrategyConfig.class
        );
        if (config == null) {
            config = mongoTemplate.findOne(
                query(where("symbol").is(symbol).and("enabled").is(true)),
                StrategyConfig.class
            );
        }

        if (config == null) {
            config = new StrategyConfig();
            config.setId(symbol + "_SIMULATION");
            config.setSymbol(symbol);
            config.setMode(StrategyConfig.Mode.SIMULATION);
            config.setEnabled(true);
            mongoTemplate.insert(config);
        }

        Update update = new Update()
            .set("maxBuyAmountUsdt", 300.0)
            .set("maxBuyPrice", 1.00040)
            .set("maxBuyPriceCeiling", 1.00045)
            .set("maxBuyOpenSeconds", 120)
            .set("enableRecentPriceBandAdjust", true)
            .set("recentPriceLookbackDays", 3)
            .set("recentPricePercentile", 0.995)
            .set("recentPriceSafetyMargin", 0.0);

        // 保持原有 API key 不变，仅确保是模拟模式
        update.set("mode", StrategyConfig.Mode.SIMULATION);

        mongoTemplate.updateFirst(
            query(where("_id").is(config.getId())),
            update,
            StrategyConfig.class
        );
    }

    @Test
    void verifyStrategyConfig() {
        String symbol = "USDCUSDT";
        StrategyConfig config = mongoTemplate.findOne(
            query(where("symbol").is(symbol).and("mode").is(StrategyConfig.Mode.SIMULATION)),
            StrategyConfig.class
        );
        if (config == null) {
            config = mongoTemplate.findOne(
                query(where("symbol").is(symbol).and("enabled").is(true)),
                StrategyConfig.class
            );
        }
        if (config == null) {
            throw new IllegalStateException("No strategy_config found for " + symbol);
        }

        log.info("StrategyConfig verify: id={}, symbol={}, mode={}, enabled={}",
            config.getId(), config.getSymbol(), config.getMode(), config.isEnabled());
        log.info("maxBuyAmountUsdt={}, maxBuyPrice={}, maxBuyPriceCeiling={}, maxBuyOpenSeconds={}",
            config.getMaxBuyAmountUsdt(), config.getMaxBuyPrice(), config.getMaxBuyPriceCeiling(), config.getMaxBuyOpenSeconds());
        log.info("recentPriceLookbackDays={}, recentPricePercentile={}, recentPriceSafetyMargin={}, enableRecentPriceBandAdjust={}",
            config.getRecentPriceLookbackDays(), config.getRecentPricePercentile(), config.getRecentPriceSafetyMargin(),
            config.isEnableRecentPriceBandAdjust());

        System.out.println("VERIFY strategy_config:");
        System.out.println("  id=" + config.getId() + " symbol=" + config.getSymbol() + " mode=" + config.getMode() + " enabled=" + config.isEnabled());
        System.out.println("  maxBuyAmountUsdt=" + config.getMaxBuyAmountUsdt() +
            " maxBuyPrice=" + config.getMaxBuyPrice() +
            " maxBuyPriceCeiling=" + config.getMaxBuyPriceCeiling() +
            " maxBuyOpenSeconds=" + config.getMaxBuyOpenSeconds());
        System.out.println("  recentPriceLookbackDays=" + config.getRecentPriceLookbackDays() +
            " recentPricePercentile=" + config.getRecentPricePercentile() +
            " recentPriceSafetyMargin=" + config.getRecentPriceSafetyMargin() +
            " enableRecentPriceBandAdjust=" + config.isEnableRecentPriceBandAdjust());
    }

    @Test
    void cleanupStaleOrders() {
        Criteria criteria = new Criteria().orOperator(
            Criteria.where("orderId").is(null),
            Criteria.where("_id").regex("^TEST_ORDER_")
        );
        long before = mongoTemplate.count(query(criteria), com.zq.order.Order.class);
        mongoTemplate.remove(query(criteria), com.zq.order.Order.class);
        long after = mongoTemplate.count(query(criteria), com.zq.order.Order.class);
        System.out.println("CLEANUP orders: before=" + before + " after=" + after);
    }
}
