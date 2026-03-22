package com.hmdp.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.transaction.RabbitTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class RabbitMQConfig {

    // ================== 1. 定义死信相关的常量 ==================
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_QUEUE = "error.dlx.queue";
    public static final String DLX_ROUTING_KEY = "error.routing.key";

    // ================== 2. 声明死信交换机 (DLX) ==================
    @Bean
    public DirectExchange dlxExchange() {
        // 创建一个名为 dlx.exchange 的直连交换机
        return new DirectExchange(DLX_EXCHANGE);
    }

    // ================== 3. 声明死信队列 (DLQ) ==================
    @Bean
    public Queue dlxQueue() {
        // 创建一个名为 error.dlx.queue 的持久化队列，用于兜底存放所有异常消息
        return new Queue(DLX_QUEUE, true);
    }

    // ================== 4. 绑定死信队列与死信交换机 ==================
    @Bean
    public Binding dlxBinding() {
        // 将死信队列通过 error.routing.key 绑定到死信交换机
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    // ================== 5. 改造原有的秒杀订单队列 (绑定DLX) ==================
    @Bean
    public Queue seckillOrderQueue() {
        // 使用 QueueBuilder 链式构建队列，便于添加额外参数
        return QueueBuilder.durable("seckill.order.queue")
                // 【核心配置】：指定该队列的死信交换机是 dlx.exchange
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                // 【核心配置】：指定死信被投递到 DLX 时使用的路由键
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    // ================== 6. 改造 Canal 缓存同步队列 (绑定DLX) ==================
    @Bean
    public Queue canalSyncQueue() {
        // 注意：将 "canal.queue" 替换为你项目中 Canal 实际使用的队列名称
        return QueueBuilder.durable("canal.queue")
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 保留原有的 RabbitMQ 事务管理器
     * 作用：保证在 Spring @Transactional 注解下，数据库操作与 MQ 消息投递的原子性。
     */
    @Bean
    public RabbitTransactionManager rabbitTransactionManager(ConnectionFactory connectionFactory) {
        return new RabbitTransactionManager(connectionFactory);
    }

    @Bean
    public Queue seckillQueue() {
        return new Queue("seckill.queue");
    }
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
    // 关键操作：使用 Gauge 监控队列深度
    @Bean
    public Gauge rabbitMetrics(MeterRegistry registry, RabbitAdmin rabbitAdmin) {
        // 加上 return，并且把返回值改成 Gauge
        return Gauge.builder("rabbitmq_queue_size", () -> {
                    // 1. 动态获取队列当前的 MessageCount
                    Properties props = rabbitAdmin.getQueueProperties("seckill.queue");
                    return props != null ? (Integer) props.get("QUEUE_MESSAGE_COUNT") : 0;
                })
                .description("秒杀队列当前积压消息数")
                .register(registry);
    }
}