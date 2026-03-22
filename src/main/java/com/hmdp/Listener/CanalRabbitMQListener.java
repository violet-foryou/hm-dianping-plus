package com.hmdp.Listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CanalRabbitMQListener {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheManager cacheManager;

    private final Counter canalSuccessCounter;
    private final Counter canalErrorCounter;

    public CanalRabbitMQListener(MeterRegistry registry) {
        this.canalSuccessCounter = Counter.builder("canal_sync_success_total")
                .description("Canal同步成功数").register(registry);
        this.canalErrorCounter = Counter.builder("canal_sync_error_total")
                .description("Canal同步失败数").register(registry);
    }

    /**
     * 修改点 1：简化方法签名
     * 自动 ACK 模式下不需要 Channel channel 和 Message message 参数
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "canal.shop.queue", durable = "true"),
            exchange = @Exchange(name = "canal.exchange", type = ExchangeTypes.DIRECT),
            key = "canal.routing.key"
    ))
    public void handleShopUpdate(String msg) { // 删除了 Channel 和 Message 参数
        try {
            log.info("接收到 Canal 监听的消息: {}", msg);
            JSONObject jsonObject = JSON.parseObject(msg);
            String type = jsonObject.getString("type");
            String table = jsonObject.getString("table");

            if ("tb_shop".equals(table) && ("UPDATE".equals(type) || "DELETE".equals(type))) {
                JSONArray dataArray = jsonObject.getJSONArray("data");
                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject rowData = dataArray.getJSONObject(i);
                    String id = rowData.getString("id");

                    // 删除 Redis 缓存
                    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

                    // 删除 Caffeine 本地缓存
                    org.springframework.cache.Cache shopCache = cacheManager.getCache("shopCache");
                    if (shopCache != null) {
                        shopCache.evict(Long.valueOf(id));
                    }
                }
            }
            // 修改点 2：成功结束方法即可，Spring 会自动发送 ACK
            canalSuccessCounter.increment();

        } catch (Exception e) {
            // 修改点 3：必须抛出异常！
            // 只有抛出异常，Spring 才会触发 YAML 中配置的 retry 重试机制。
            // 重试耗尽后，配合死信队列配置，消息会自动进入死信队列。
            canalErrorCounter.increment();
            log.error("处理 Canal 消息失败，触发重试", e);
            throw new RuntimeException("Canal同步失败，等待重试", e);
        }
    }
}