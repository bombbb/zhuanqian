package com.zq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主类（已废弃）
 * 
 * 注意：本项目使用 Spring Boot，实际入口是 Application.java
 * 策略配置完全从 MongoDB 读取，不再使用 YAML 配置文件
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        System.out.printf("Hello and welcome!");
        logger.info("This is a demo class. Use Application.java to start the application.");
        logger.info("Strategy configuration is loaded from MongoDB, not from YAML files.");
    }
}