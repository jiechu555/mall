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
 * 环境里可能存在活跃消费者（常驻应用或 @SpringBootTest 缓存上下文）与本测试抢消息，
 * 所以不直接消费业务队列，而是声明一个绑定到同一路由键的测试专属队列——
 * 直连交换机会把消息复制给所有匹配绑定，业务消费者随便抢，测试照样收到副本。
 */
class FlashSaleOrderSenderTest {

    private static final String TEST_QUEUE = "mall.flashsale.order.sendertest";

    private static CachingConnectionFactory factory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void init() {
        factory = new CachingConnectionFactory("localhost");
        factory.setUsername("mall");
        factory.setPassword("mall");
        factory.setVirtualHost("/mall");
        rabbitTemplate = new RabbitTemplate(factory);

        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareQueue(new Queue(TEST_QUEUE));
        admin.declareBinding(BindingBuilder.bind(new Queue(TEST_QUEUE))
                .to(new DirectExchange(QueueEnum.QUEUE_FLASH_SALE_ORDER.getExchange()))
                .with(QueueEnum.QUEUE_FLASH_SALE_ORDER.getRouteKey()));
    }

    @AfterAll
    static void cleanUp() {
        new RabbitAdmin(rabbitTemplate).deleteQueue(TEST_QUEUE);
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

        FlashSaleOrderSender sender = new FlashSaleOrderSender(rabbitTemplate, null);
        sender.sendMessage(message);

        Object received = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);
        assertNotNull(received, "测试专属队列应在 5 秒内收到消息副本");
        String body = received.toString();
        assertTrue(body.contains("\"ticket\":\"ticket-test-0001\""), "受理号应完整往返，实际: " + body);
        assertTrue(body.contains("\"memberId\":42"), "会员id应完整往返，实际: " + body);
        assertTrue(body.contains("1999.00"), "秒杀价应完整往返，实际: " + body);
    }
}
