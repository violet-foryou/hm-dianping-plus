package com.hmdp.Listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 生产级死信消费者
 */
@Slf4j
@Component
public class DeadLetterListener {

    /**
     * 监听指定的死信队列
     * @param message 原生 Message 对象，可以获取消息体和 Header 信息
     */
    @RabbitListener(queues = "error.dlx.queue")
    public void listenDeadLetter(Message message) {
        // 1. 获取消息的字节数组并转换为字符串 (假设数据是 JSON 格式)
        String msgBody = new String(message.getBody(), StandardCharsets.UTF_8);

        // 2. 打印详细的异常追踪日志，供 ELK 或本地日志系统收集
        log.error("【MQ严重告警】捕获到死信消息！");
        log.error("接收到的脏数据内容: {}", msgBody);
        log.error("消息所在的原始队列: {}", message.getMessageProperties().getConsumerQueue());

        // 3. 触发真实告警逻辑
        triggerAlertNotification("RabbitMQ 消费连续3次失败产生死信", msgBody);

        // 此处不需要抛出异常，消息被正常消费（等于从死信队列中移除了，或者你可以存入 MySQL 的一张 error_msg_log 异常数据表中兜底）
    }

    /**
     * 模拟发送告警通知（邮件 / 钉钉机器人 / 企业微信等）
     */
    private void triggerAlertNotification(String title, String content) {
        // 为了方便本地落地演示，使用 System.err 打印显眼的红色控制台告警
        System.err.println("=================================================");
        System.err.println("🚨 生产环境告警触发 🚨");
        System.err.println("告警标题：" + title);
        System.err.println("告警内容：" + content);
        System.err.println("处理建议：请开发人员立刻检查数据库死锁状态或业务代码Bug！");
        System.err.println("=================================================");

        // 如果你需要接入真实的邮件告警，可以通过引入 spring-boot-starter-mail，在此处调用 JavaMailSender 发送邮件
    }
}