package com.hmdp.Listener;

import com.alibaba.fastjson.JSON;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
public class SeckillRabbitMQListener {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @RabbitListener(queues = "seckill.order.queue")
    public void listenSeckillOrder(String msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 反序列化消息
            VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);
            Long voucherId = voucherOrder.getVoucherId();
            Long userId = voucherOrder.getUserId();

            // 2. 【幂等性处理方案】
            // 黑马点评中 tb_voucher_order 已经针对 voucher_id 和 user_id 设置了逻辑判断（一人一单），
            // 更为底层的幂等性依靠数据库的主键 ID（orderId是雪花算法提前生成的唯一ID）。
            // 当重复消费相同的消息时，数据库会抛出 DuplicateKeyException（主键冲突）。

            // 3. 扣减数据库库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();

            if (success) {
                // 4. 创建订单（入库）
                voucherOrderService.save(voucherOrder);
            } else {
                log.warn("扣减库存失败，可能是由于 Redis 拦截泄漏，兜底失败");
            }

            // 5. 手动 ACK
            channel.basicAck(deliveryTag, false);

        } catch (DuplicateKeyException e) {
            // 幂等性命中：主键冲突说明该订单已经被成功消费过了，直接 ACK 丢弃，避免反复重试
            log.warn("订单重复消费被拦截，已被幂等性处理。");
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理秒杀订单失败，将退回队列", e);
            // 业务异常，退回队列重新消费（实际生产建议配合重试次数限制，超过放死信队列）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}