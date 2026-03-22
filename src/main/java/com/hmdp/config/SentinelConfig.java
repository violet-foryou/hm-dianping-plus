package com.hmdp.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Sentinel 配置类
 * 作用：将 SentinelResourceAspect 注入到 Spring 容器中。
 * 只有注入了这个 Bean，标注了 @SentinelResource 的方法才会被切面拦截，从而实现流量控制。
 */

@Configuration
public class SentinelConfig {

    // 保留你原有的切面配置
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 新版 Sentinel 全局限流异常处理器
     * 作用：当请求被 Sentinel 拦截时，会进入此方法。我们在这里进行指标埋点并返回友好提示。
     */
    @Bean
    public BlockExceptionHandler blockExceptionHandler(MeterRegistry registry) {
        // 1. 初始化指标：定义一个计数器，用于记录限流发生的次数
        Counter sentinelCounter = Counter.builder("sentinel_block_total")
                .description("Sentinel 限流触发总次数")
                .tag("project", "hm-dianping")
                .register(registry);

        // 2. 返回自定义的限流处理器
        return new BlockExceptionHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
                // 3. 每次触发限流时，Prometheus 计数器自增
                sentinelCounter.increment();

                // 4. 返回 HTTP 429 (Too Many Requests) 状态码和 JSON 提示
                response.setStatus(429);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print("{\"code\": 429, \"msg\": \"系统繁忙，请稍后再试 (Too many requests!)\"}");
            }
        };
    }
}

//@Configuration
//public class SentinelConfig {
//
//    @Bean
//    public SentinelResourceAspect sentinelResourceAspect() {
//        // 实例化并返回 Sentinel 的切面对象
//        return new SentinelResourceAspect();
//    }
//}