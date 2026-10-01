package com.macro.mall.portal.seckill;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.portal.seckill.service.FlashSaleService;
import com.macro.mall.portal.seckill.service.FlashSaleStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀接口验收（06 讲 commit 5）：Swagger 可调、单账号超频被拒、
 * 令牌核销防裸请求、下单受理→轮询出结果全链路。
 * 走真实 HTTP（随机端口 + 真实 JWT），中间件与 dev 环境同实例。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlashSaleApiTest {

    private static final Long SESSION = 1L;
    private static final Long PRODUCT = 27L;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private FlashSaleService flashSaleService;
    @Autowired
    private FlashSaleStockService flashSaleStockService;
    @Autowired
    private SmsFlashPromotionMapper promotionMapper;
    @Autowired
    private SmsFlashPromotionProductRelationMapper relationMapper;
    @Autowired
    private SmsFlashPromotionOrderMapper acceptanceMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private AmqpAdmin amqpAdmin;

    /** mbg 的 generatedKey 会忽略预设自增 id，统一用 insert 后回填的生成 id */
    private Long promotionId;
    private String jwt;

    @BeforeEach
    void setUp() {
        // 合成一场"正在进行"的秒杀：活动日期覆盖今天 + 关系表商品 27（价 1999、量 100、限购 2）
        SmsFlashPromotion promotion = new SmsFlashPromotion();
        promotion.setTitle("commit5-api-check");
        promotion.setStatus(1);
        promotion.setStartDate(new Date(System.currentTimeMillis() - 3600_000));
        promotion.setEndDate(new Date(System.currentTimeMillis() + 3600_000));
        promotionMapper.insert(promotion);
        promotionId = promotion.getId();
        SmsFlashPromotionProductRelation relation = new SmsFlashPromotionProductRelation();
        relation.setFlashPromotionId(promotionId);
        relation.setFlashPromotionSessionId(SESSION);
        relation.setProductId(PRODUCT);
        relation.setFlashPromotionPrice(new BigDecimal("1999.00"));
        relation.setFlashPromotionCount(100);
        relation.setFlashPromotionLimit(2);
        relationMapper.insert(relation);

        flashSaleStockService.warmUp(promotionId, SESSION);
        flashSaleService.issueTokens(promotionId, SESSION, 10);

        jwt = login();
    }

    @AfterEach
    void cleanUp() {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        for (SmsFlashPromotionOrder row : acceptanceMapper.selectByExample(example)) {
            if (row.getOrderId() != null) {
                OmsOrderItemExample itemExample = new OmsOrderItemExample();
                itemExample.createCriteria().andOrderIdEqualTo(row.getOrderId());
                orderItemMapper.deleteByExample(itemExample);
                orderMapper.deleteByPrimaryKey(row.getOrderId());
            }
            acceptanceMapper.deleteByPrimaryKey(row.getId());
        }
        SmsFlashPromotionProductRelationExample relationExample = new SmsFlashPromotionProductRelationExample();
        relationExample.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        relationMapper.deleteByExample(relationExample);
        promotionMapper.deleteByPrimaryKey(promotionId);
        amqpAdmin.purgeQueue("mall.order.cancel.ttl", false);
        amqpAdmin.purgeQueue("mall.flashsale.order", false);
    }

    @Test
    void 单账号超频被拒且令牌核验生效() throws Exception {
        int rateLimited = 0;
        int tokenInvalid = 0;
        // 连发带假令牌的请求：前 10 次过频控、死在令牌核销；之后死在频控。
        // 1 秒窗口边界可能把请求劈成两窗，故最多重试 3 轮（每轮间隔 1.2 秒重置窗口）
        for (int attempt = 0; attempt < 3 && rateLimited == 0; attempt++) {
            if (attempt > 0) {
                Thread.sleep(1200);
            }
            for (int i = 0; i < 12; i++) {
                String body = postOrder("bogus-token");
                if (body.contains("操作过于频繁")) {
                    rateLimited++;
                } else if (body.contains("秒杀令牌无效")) {
                    tokenInvalid++;
                }
            }
        }
        assertTrue(rateLimited >= 1, "超出 10 次/秒应被频控拦截，实际拦截 " + rateLimited + " 次");
        assertTrue(tokenInvalid >= 1, "伪造令牌应被核销拦截");
    }

    @Test
    void 全链路_下单受理并轮询到成功() throws Exception {
        // 频控测试可能刚打满 1 秒窗口，先等窗口过期再下单
        Thread.sleep(1200);
        List<String> tokens = flashSaleService.issueTokens(promotionId, SESSION, 1);
        String body = postOrder(tokens.get(0));
        assertTrue(body.contains("\"code\":200"), "有效令牌下单应受理成功，实际: " + body);
        String ticket = extractTicket(body);
        assertNotNull(ticket, "响应应包含受理号，实际: " + body);

        String resultBody = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            resultBody = getResult(ticket);
            if (resultBody != null && resultBody.contains("\"status\":\"SUCCESS\"")) {
                break;
            }
        }
        assertNotNull(resultBody, "轮询应返回结果");
        assertTrue(resultBody.contains("\"status\":\"SUCCESS\""), "消费端落库后应为 SUCCESS，实际: " + resultBody);
        assertTrue(resultBody.contains("orderSn"), "SUCCESS 应携带订单编号，实际: " + resultBody);
    }

    private String login() {
        String body = rest.postForObject("/sso/login?username=tester01&password=test123456", null, String.class);
        int start = body.indexOf("\"token\":\"") + 9;
        return body.substring(start, body.indexOf("\"", start));
    }

    private String postOrder(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwt);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String url = "/seckill/order?promotionId=" + promotionId + "&sessionId=" + SESSION
                + "&productId=" + PRODUCT + "&memberReceiveAddressId=7&token=" + token;
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), String.class).getBody();
    }

    private String getResult(String ticket) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwt);
        return rest.exchange("/seckill/result?ticket=" + ticket, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
    }

    private String extractTicket(String body) {
        int start = body.indexOf("\"data\":\"") + 8;
        if (start < 8) {
            return null;
        }
        return body.substring(start, body.indexOf("\"", start));
    }
}
