package com.hmdp.Listener;

import com.alibaba.fastjson.JSON;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillRabbitMQListener {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 自动 ACK 模式下的秒杀订单处理
     * 修改点：删除了 Channel 和 Message 参数
     */
    @Transactional
    @RabbitListener(queues = "seckill.order.queue")
    public void listenSeckillOrder(String msg) {
        try {
            // 1. 反序列化消息
            VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);
            Long voucherId = voucherOrder.getVoucherId();

            // 3. 创建订单（入库）
            // 依靠数据库主键/唯一索引实现底层幂等
            voucherOrderService.save(voucherOrder);

            // 2. 扣减数据库库存
            // 使用乐观锁防止超卖：gt("stock", 0)
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();

            if (!success) {
                // 如果库存扣减失败，说明库存不足或并发冲突
                log.warn("扣减库存失败，券ID: {}", voucherId);
                // 这里可以选择抛出异常触发重试，或者直接返回（ACK）结束流程
                return;
            }



            // 4. 方法正常结束 -> Spring 自动发送 ACK
            log.info("秒杀订单处理成功，订单ID: {}", voucherOrder.getId());

        } catch (DuplicateKeyException e) {
            // 【核心修改点：幂等性处理】
            // 如果捕获到主键冲突，说明该订单已经入库过了（重复消费）。
            // 此时我们直接打印日志并“吞掉”异常，不向外抛出。
            // 这样方法会正常结束，Spring 认为处理成功，会自动 ACK 掉这条重复的消息。
            log.warn("订单重复消费（幂等性命中），订单已存在，跳过处理。");

        } catch (Exception e) {
            // 【核心修改点：异常处理】
            // 捕获到其他业务异常（如数据库断开、超时等）。
            // 必须打印日志并抛出 RuntimeException。
            // 只有抛出异常，Spring 才会根据配置触发 retry（重试3次），重试耗尽后进入死信队列。
            log.error("处理秒杀订单过程中发生系统异常，准备重试", e);
            throw new RuntimeException("秒杀订单处理失败", e);
        }
    }
}