package com.macro.mall.portal.seckill;

import com.macro.mall.portal.domain.QueueEnum;
import com.macro.mall.portal.seckill.component.FlashSaleOrderSender;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀消息发送链路测试：直连本机 RabbitMQ（vhost /mall，与 dev 环境同实例）。
 * 测试自建与生产配置同名的交换机/队列/绑定（幂等，应用启动时会再次声明），
 * 发送一条消息后消费回来，验证 JSON 内容完整往返。
 */
class FlashSaleOrderSenderTest {

    private static CachingConnectionFactory factory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void init() {
        factory = new CachingConnectionFactory("localhost");
        factory.setUsername("mall");
        factory.setPassword("mall");
        factory.setVirtualHost("/mall");
        rabbitTemplate = new RabbitTemplate(factory);

        // 与 RabbitMqConfig 中同名的声明（应用启动时 RabbitAdmin 会幂等地再声明一次）
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        DirectExchange exchange = new DirectExchange(QueueEnum.QUEUE_FLASH_SALE_ORDER.getExchange(), true, false);
        Queue queue = new Queue(QueueEnum.QUEUE_FLASH_SALE_ORDER.getName());
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(QueueEnum.QUEUE_FLASH_SALE_ORDER.getRouteKey());
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);
    }

    @AfterAll
    static void cleanUp() {
        factory.stop();
    }

    @Test
    void 秒杀消息可发送且内容完整往返() {
        FlashSaleOrderMessage message = new FlashSaleOrderMessage();
        message.setPromotionId(9901L);
        message.setSessionId(1L);
        message.setProductId(27L);
        message.setProductSkuId(98L);
        message.setMemberId(42L);
        message.setMemberReceiveAddressId(7L);
        message.setQuantity(1);
        message.setFlashPromotionPrice(new BigDecimal("1999.00"));
        message.setPayType(0);
        message.setTicket("ticket-test-0001");

        FlashSaleOrderSender sender = new FlashSaleOrderSender(rabbitTemplate);
        sender.sendMessage(message);

        Object received = rabbitTemplate.receiveAndConvert(QueueEnum.QUEUE_FLASH_SALE_ORDER.getName(), 5000);
        assertNotNull(received, "消息应在 5 秒内可被消费到");
        String body = received.toString();
        assertTrue(body.contains("\"ticket\":\"ticket-test-0001\""), "受理号应完整往返，实际: " + body);
        assertTrue(body.contains("\"memberId\":42"), "会员id应完整往返，实际: " + body);
        assertTrue(body.contains("1999.00"), "秒杀价应完整往返，实际: " + body);
    }
}
