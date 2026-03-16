package com.zq.config;

import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheService {

    private final Cache<String, Object> cache;

    public CacheService(Cache<String, Object> cache) {
        this.cache = cache;
    }

    // 泛型版本的 getFromCache 方法
    public <T> T getFromCache(String key, Class<T> clazz) {
        Object value = cache.getIfPresent(key);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);  // 安全的强制类型转换
        }
        return null; // 或者抛出异常，具体看需求
    }

    // 存入缓存的方法（也可以使用泛型）
    public <T> void putInCache(String key, T value) {
        cache.put(key, value);
    }
    
    /**
     * 清空指定key的缓存
     * @param key 缓存键
     */
    public void evict(String key) {
        cache.invalidate(key);
        log.debug("Cache evicted for key: {}", key);
    }
    
    /**
     * 清空所有缓存
     */
    public void evictAll() {
        cache.invalidateAll();
        log.info("All cache evicted");
    }
    
    /**
     * 获取缓存大小
     */
    public long size() {
        return cache.size();
    }
}
