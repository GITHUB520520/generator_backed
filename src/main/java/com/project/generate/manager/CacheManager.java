package com.project.generate.manager;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存的实现
 */
@Component
public class CacheManager {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    Cache<String, Object> localCache = Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).maximumSize(10000).build();

    public void put(String key, Object value){
        redisTemplate.opsForValue().set(key, value, 2, TimeUnit.HOURS);
        localCache.put(key, value);
    }

    public Object get(String key){
        Object value = localCache.getIfPresent(key);
        if (value != null) return value;
        value = redisTemplate.opsForValue().get(key);
        if (value != null) localCache.put(key, value);
        return value;
    }

    public void delKey(String key){
        localCache.invalidate(key);
        redisTemplate.delete(key);
    }
}
