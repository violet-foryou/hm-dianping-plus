package com.hmdp.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.transaction.RabbitTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 声明秒杀队列
    @Bean
    public Queue seckillOrderQueue() {
        return new Queue("seckill.order.queue", true); // 持久化队列
    }

    /**
     * 配置 RabbitMQ 事务管理器
     * 作用：保证在 Spring @Transactional 注解下，数据库操作与 MQ 消息投递的原子性。
     * 若后续抛出异常，投递的 MQ 消息会被回滚（不会真正发给消费者）。
     */
    @Bean
    public RabbitTransactionManager rabbitTransactionManager(ConnectionFactory connectionFactory) {
        return new RabbitTransactionManager(connectionFactory);
    }
}