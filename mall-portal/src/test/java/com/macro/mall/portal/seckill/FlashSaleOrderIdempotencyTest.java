package com.macro.mall.portal.seckill;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import com.macro.mall.portal.seckill.component.FlashSaleOrderSender;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀消费端幂等验收（06 讲 commit 4 验收标准）：
 * 重复投递同一条消息，只产生一单。
 * 全链路走真实中间件（MySQL/RabbitMQ/Redis 与 dev 环境同实例），
 * 幂等由唯一键 uk_promotion_session_product_member 保证——
 * 注意本机常驻的 mall-portal 也可能消费到消息，这正是幂等设计要扛的场景。
 */
@SpringBootTest
class FlashSaleOrderIdempotencyTest {

    private static final Long PROMOTION = 9905L;
    private static final Long SESSION = 1L;
    private static final Long PRODUCT = 27L;
    private static final Long MEMBER = 12L;

    @Autowired
    private FlashSaleOrderSender flashSaleOrderSender;
    @Autowired
    private SmsFlashPromotionOrderMapper acceptanceMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private AmqpAdmin amqpAdmin;

    @AfterEach
    void cleanUp() {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(PROMOTION);
        List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(example);
        for (SmsFlashPromotionOrder row : rows) {
            if (row.getOrderId() != null) {
                OmsOrderItemExample itemExample = new OmsOrderItemExample();
                itemExample.createCriteria().andOrderIdEqualTo(row.getOrderId());
                orderItemMapper.deleteByExample(itemExample);
                orderMapper.deleteByPrimaryKey(row.getOrderId());
            }
            acceptanceMapper.deleteByPrimaryKey(row.getId());
        }
        // 清掉为已删除订单安排的延迟取消消息，避免 60 分钟后打到空订单
        amqpAdmin.purgeQueue("mall.order.cancel.ttl", false);
        amqpAdmin.purgeQueue("mall.flashsale.order", false);
    }

    @Test
    void 重复投递同一消息_只产生一单() throws Exception {
        cleanPreExistingRows();

        FlashSaleOrderMessage message = new FlashSaleOrderMessage();
        message.setPromotionId(PROMOTION);
        message.setSessionId(SESSION);
        message.setProductId(PRODUCT);
        message.setProductSkuId(98L);
        message.setMemberId(MEMBER);
        message.setMemberReceiveAddressId(7L);
        message.setQuantity(1);
        message.setFlashPromotionPrice(new BigDecimal("1999.00"));
        message.setPayType(0);
        message.setTicket("ticket-idem-0001");

        // 同一条消息投两次（模拟 MQ 至少一次语义下的重复投递）
        flashSaleOrderSender.sendMessage(message);
        flashSaleOrderSender.sendMessage(message);

        // 等待消费完成（本测试上下文与常驻应用两个消费者竞争，靠轮询收敛）
        SmsFlashPromotionOrder done = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(exampleOf());
            if (rows.size() == 1 && rows.get(0).getStatus() == 1) {
                done = rows.get(0);
                break;
            }
        }

        assertNotNull(done, "消息应在 15 秒内被消费且受理单转为已生成订单");
        List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(exampleOf());
        assertEquals(1, rows.size(), "重复投递两次，受理单必须只有一条");
        assertNotNull(done.getOrderId(), "受理单应回填正式订单id");
        assertNotNull(done.getOrderSn(), "受理单应回填订单编号");
        assertEquals(Integer.valueOf(1), orderMapper.selectByPrimaryKey(done.getOrderId()).getOrderType(),
                "秒杀订单的 order_type 必须是 1");

        QueueInformation ttlQueue = amqpAdmin.getQueueInfo("mall.order.cancel.ttl");
        assertNotNull(ttlQueue, "延迟取消队列应存在");
        assertTrue(ttlQueue.getMessageCount() >= 1, "应为秒杀订单安排至少一条 60 分钟延迟取消消息");
    }

    private void cleanPreExistingRows() {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(PROMOTION);
        List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(example);
        for (SmsFlashPromotionOrder row : rows) {
            if (row.getOrderId() != null) {
                OmsOrderItemExample itemExample = new OmsOrderItemExample();
                itemExample.createCriteria().andOrderIdEqualTo(row.getOrderId());
                orderItemMapper.deleteByExample(itemExample);
                orderMapper.deleteByPrimaryKey(row.getOrderId());
            }
            acceptanceMapper.deleteByPrimaryKey(row.getId());
        }
    }

    private SmsFlashPromotionOrderExample exampleOf() {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(PROMOTION);
        return example;
    }
}
