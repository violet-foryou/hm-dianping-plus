package com.hmdp.Listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CanalRabbitMQListener {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 注入 CaffeineCacheManager 用于清理本地缓存
    @Resource
    private CacheManager cacheManager;

    /**
     * @RabbitListener 注解完成队列、交换机及其绑定关系的自动声明
     * bindings 配置了与 Canal 配置文件中一致的 exchange 和 routingKey
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "canal.shop.queue", durable = "true"),
            exchange = @Exchange(name = "canal.exchange", type = ExchangeTypes.DIRECT),
            key = "canal.routing.key"
    ))
    public void handleShopUpdate(String msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("接收到 Canal 监听的消息: {}", msg);
            // 1. 解析 Canal 发送过来的 JSON 数据
            JSONObject jsonObject = JSON.parseObject(msg);
            // 获取操作类型：INSERT, UPDATE, DELETE
            String type = jsonObject.getString("type");
            // 获取表名，二次确认
            String table = jsonObject.getString("table");

            if ("tb_shop".equals(table) && ("UPDATE".equals(type) || "DELETE".equals(type))) {
                // data 是一个数组，因为一条 SQL 可能影响多行数据
                JSONArray dataArray = jsonObject.getJSONArray("data");
                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject rowData = dataArray.getJSONObject(i);
                    String id = rowData.getString("id");

                    // 2. 删除 Redis 缓存
                    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
                    log.info("已成功清除 Redis 中店铺 {} 的缓存", id);

                    // 3. 删除 Caffeine 本地缓存
                    // 对应 CaffeineConfig 中配置的缓存管理器，根据你在 @Cacheable 中指定的 value 也就是 "shopCache" 来获取
                    org.springframework.cache.Cache shopCache = cacheManager.getCache("shopCache");
                    if (shopCache != null) {
                        shopCache.evict(Long.valueOf(id));
                        log.info("已成功清除 Caffeine 中店铺 {} 的缓存", id);
                    }
                }
            }
            // 4. 手动 ACK，通知 RabbitMQ 消息消费成功
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 Canal 消息失败", e);
            // 5. 处理失败，将消息重新放回队列（或者存入死信队列/数据库日志表）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}