package com.macro.mall.portal.seckill.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import com.macro.mall.portal.seckill.service.FlashSaleOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息的消费者（仿 CancelOrderReceiver）
 * 关键约束：mall 未配置手动 ack（默认模式），监听方法抛异常会触发 requeue，
 * 毒消息将无限循环——因此这里整体 try-catch，失败靠受理单 status=0 + 对账任务兜底，
 * 而不是指望消息重投。
 * Created by jiechu555 on 2026/10/01.
 */
@Component
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOrderReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleOrderReceiver.class);

    private final FlashSaleOrderService flashSaleOrderService;
    private final FlashSaleOrderSender flashSaleOrderSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlashSaleOrderReceiver(FlashSaleOrderService flashSaleOrderService,
                                  FlashSaleOrderSender flashSaleOrderSender) {
        this.flashSaleOrderService = flashSaleOrderService;
        this.flashSaleOrderSender = flashSaleOrderSender;
    }

    @RabbitListener(queues = "mall.flashsale.order")
    public void handle(String messageJson) {
        FlashSaleOrderMessage message;
        try {
            message = objectMapper.readValue(messageJson, FlashSaleOrderMessage.class);
        } catch (JsonProcessingException e) {
            // 反序列化都失败的消息重投一万次也一样：记录后吞掉，绝不 requeue
            LOGGER.error("秒杀订单消息反序列化失败，已丢弃: {}", messageJson, e);
            return;
        }
        try {
            Long acceptanceId = flashSaleOrderService.createAcceptance(message);
            if (acceptanceId == null) {
                return;
            }
            OmsOrder order = flashSaleOrderService.createOrder(message, acceptanceId);
            // 事务提交后再安排延迟取消，避免给已回滚的订单安排取消
            flashSaleOrderSender.sendCancelMessage(order.getId());
        } catch (Exception e) {
            // 不重抛：受理单以 status=0 留存，交给对账任务二轮处理
            LOGGER.error("秒杀订单落库失败，受理单保持受理中待对账: ticket={}", message.getTicket(), e);
        }
    }
}
