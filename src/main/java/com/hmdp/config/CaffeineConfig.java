package com.hmdp.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@EnableCaching // 核心注解：开启 Spring Cache 的缓存注解支持
@Configuration // 声明这是一个配置类，Spring 启动时会加载它
public class CaffeineConfig {

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
