package com.zq.strategy;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class StrategyConfigWatcher {

    private final MongoTemplate mongoTemplate;

    public StrategyConfigWatcher(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void watch() {
        new Thread(this::startWatch, "strategy-config-watch").start();
    }

    private void startWatch() {
        MongoCollection<Document> col =
                mongoTemplate.getCollection("strategy_config");

        try (MongoCursor<ChangeStreamDocument<Document>> cursor =
                col.watch().iterator()) {

            while (cursor.hasNext()) {
                ChangeStreamDocument<Document> event = cursor.next();
                System.out.println("策略配置变更: " + event);

                // 触发策略重新加载
                // StrategyManager.reload(...)
            }
        }
    }
}
