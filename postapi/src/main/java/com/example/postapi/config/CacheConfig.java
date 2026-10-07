package com.example.postapi.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class CacheConfig {

    @Bean
    CacheManager cacheManager(@Value("${app.cache.enabled:true}") boolean cacheEnabled) {
        if (!cacheEnabled) {
            return new NoOpCacheManager();
        }
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "postById", "postPages", "postSearchPages");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5)));
        return cacheManager;
    }
}