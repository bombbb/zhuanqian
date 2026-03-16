package com.zq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB连接测试
 */
@SpringBootTest
class MongoDBConnectionTest {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Test
    void testMongoDBConnection() {
        assertNotNull(mongoTemplate);
        String dbName = mongoTemplate.getDb().getName();
        System.out.println("Connected to MongoDB database: " + dbName);
        assertEquals("strategy_db", dbName);
    }
}

