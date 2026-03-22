package com.hmdp.config;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@EnableCaching // 核心注解：开启 Spring Cache 的缓存注解支持
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Object> caffeineCache(MeterRegistry registry) {
        Cache<String, Object> cache = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                // 1. 必须开启统计功能，否则监控不到数据
                .recordStats()
                .build();

        // 2. 关键：将当前缓存实例注册到 Micrometer 的指标注册表中
        // 这样在 /actuator/prometheus 里就能看到 cache_hit_total 等指标
        CaffeineCacheMetrics.monitor(registry, cache, "hmdpCache");

        return cache;
    }

    @Bean // 将该方法的返回值交由 Spring 容器管理
    public CacheManager cacheManager() {
        // 1. 创建 Caffeine 缓存管理器对象
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 2. 自定义 Caffeine 的底层配置
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100) // 初始大小：一开始预留100个缓存空位
                .maximumSize(1000)    // 最大容量：最多缓存1000条数据，超过会自动淘汰老数据，防止内存溢出
                .expireAfterWrite(10, TimeUnit.MINUTES)); // 过期策略：写入10分钟后自动过期，保证数据不会永远固化

        // 3. 返回配置好的管理器
        return cacheManager;
    }
}
//@Configuration // 声明这是一个配置类，Spring 启动时会加载它
//public class CaffeineConfig {
//
//    @Bean // 将该方法的返回值交由 Spring 容器管理
//    public CacheManager cacheManager() {
//        // 1. 创建 Caffeine 缓存管理器对象
//        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
//
//        // 2. 自定义 Caffeine 的底层配置
//        cacheManager.setCaffeine(Caffeine.newBuilder()
//                .initialCapacity(100) // 初始大小：一开始预留100个缓存空位
//                .maximumSize(1000)    // 最大容量：最多缓存1000条数据，超过会自动淘汰老数据，防止内存溢出
//                .expireAfterWrite(10, TimeUnit.MINUTES)); // 过期策略：写入10分钟后自动过期，保证数据不会永远固化
//
//        // 3. 返回配置好的管理器
//        return cacheManager;
//    }
//}
