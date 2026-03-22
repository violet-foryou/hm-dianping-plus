package com.hmdp.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MetricsAspect {

    private final Timer timer;

    public MetricsAspect(MeterRegistry registry) {
        // 1. 定义一个 Timer 指标，它会自动生成 QPS、平均响应时间、最大响应时间等多个数据
        this.timer = Timer.builder("http_requests_latency")
                .description("接口响应耗时")
                .tag("application", "hm-dianping")
                .register(registry);
    }

    // 2. 拦截 com.hmdp.controller 包下的所有方法
    @Around("execution(* com.hmdp.controller.*.*(..))")
    public Object recordMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        // 3. 使用 timer.record 包裹业务代码，自动计时
        return timer.recordCallable(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}