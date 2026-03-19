package com.hmdp.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 配置类
 * 作用：将 SentinelResourceAspect 注入到 Spring 容器中。
 * 只有注入了这个 Bean，标注了 @SentinelResource 的方法才会被切面拦截，从而实现流量控制。
 */
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        // 实例化并返回 Sentinel 的切面对象
        return new SentinelResourceAspect();
    }
}