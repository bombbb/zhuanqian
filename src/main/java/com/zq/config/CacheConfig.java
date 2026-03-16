package com.zq.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Object> guavaCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(100)  // 最大缓存条目数
                .expireAfterWrite(1, TimeUnit.MINUTES)  // 设置过期时间
                .build();
    }
}
