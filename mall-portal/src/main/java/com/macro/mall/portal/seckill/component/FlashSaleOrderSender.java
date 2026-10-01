package com.macro.mall.portal.seckill.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.portal.domain.QueueEnum;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息的发送者（仿 CancelOrderSender）
 * 注意：mall 未注册全局 MessageConverter，为不影响现有 Long 消息链路，
 * 这里自行把消息体序列化为 JSON 字符串发送，消费端按 JSON 反序列化。
 * Created by jiechu555 on 2026/10/01.
 */
@Component
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOrderSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleOrderSender.class);

    private final AmqpTemplate amqpTemplate;
    private final OmsOrderSettingMapper orderSettingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlashSaleOrderSender(AmqpTemplate amqpTemplate, OmsOrderSettingMapper orderSettingMapper) {
        this.amqpTemplate = amqpTemplate;
        this.orderSettingMapper = orderSettingMapper;
    }

    public void sendMessage(FlashSaleOrderMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            amqpTemplate.convertAndSend(
                    QueueEnum.QUEUE_FLASH_SALE_ORDER.getExchange(),
                    QueueEnum.QUEUE_FLASH_SALE_ORDER.getRouteKey(),
                    payload,
                    new MessagePostProcessor() {
                        @Override
                        public Message postProcessMessage(Message m) throws AmqpException {
                            // 削峰期间消息可能在队列里排队，必须持久化，broker 重启不丢
                            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return m;
                        }
                    });
            LOGGER.info("send flashSaleOrder ticket:{} memberId:{}", message.getTicket(), message.getMemberId());
        } catch (JsonProcessingException e) {
            LOGGER.error("秒杀订单消息序列化失败: {}", message, e);
        }
    }

    /**
     * 为秒杀订单安排超时自动取消：复用 mall 现有 TTL/死信队列（mall.order.cancel.ttl），
     * 但取消时长读 mall 预留而未启用的 flash_order_overtime（分钟，种子值 60），
     * 而不是普通订单的 normal_order_overtime（120 分钟）
     */
    public void sendCancelMessage(Long orderId) {
        long minutes = 60;
        OmsOrderSetting setting = orderSettingMapper == null ? null : orderSettingMapper.selectByPrimaryKey(1L);
        if (setting != null && setting.getFlashOrderOvertime() != null) {
            minutes = setting.getFlashOrderOvertime();
        }
        final long delayMillis = minutes * 60 * 1000;
        amqpTemplate.convertAndSend(
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange(),
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey(),
                orderId,
                new MessagePostProcessor() {
                    @Override
                    public Message postProcessMessage(Message m) throws AmqpException {
                        m.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                        return m;
                    }
                });
        LOGGER.info("send flashSaleCancel orderId:{} delay:{}ms", orderId, delayMillis);
    }
}
