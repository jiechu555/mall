package com.macro.mall.portal.seckill.service.impl;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.portal.dao.FlashSaleSkuStockDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import com.macro.mall.portal.seckill.service.FlashSaleOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 秒杀订单落库服务实现
 * 两段事务设计：受理单插入（createAcceptance）与正式落库（createOrder）分开，
 * 落库失败时受理单以 status=0 留存，交由对账任务二轮处理，而不是依赖消息重投。
 * Created by jiechu555 on 2026/10/01.
 */
@Service
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOrderServiceImpl implements FlashSaleOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleOrderServiceImpl.class);

    private final SmsFlashPromotionOrderMapper acceptanceMapper;
    private final SmsFlashPromotionProductRelationMapper relationMapper;
    private final OmsOrderMapper orderMapper;
    private final PortalOrderItemDao orderItemDao;
    private final UmsMemberMapper memberMapper;
    private final UmsMemberReceiveAddressMapper addressMapper;
    private final PmsProductMapper productMapper;
    private final PmsSkuStockMapper skuStockMapper;
    private final FlashSaleSkuStockDao flashSaleSkuStockDao;
    private final RedisService redisService;

    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.key.orderId}")
    private String REDIS_KEY_ORDER_ID;

    public FlashSaleOrderServiceImpl(SmsFlashPromotionOrderMapper acceptanceMapper,
                                     SmsFlashPromotionProductRelationMapper relationMapper,
                                     OmsOrderMapper orderMapper,
                                     PortalOrderItemDao orderItemDao,
                                     UmsMemberMapper memberMapper,
                                     UmsMemberReceiveAddressMapper addressMapper,
                                     PmsProductMapper productMapper,
                                     PmsSkuStockMapper skuStockMapper,
                                     FlashSaleSkuStockDao flashSaleSkuStockDao,
                                     RedisService redisService) {
        this.acceptanceMapper = acceptanceMapper;
        this.relationMapper = relationMapper;
        this.orderMapper = orderMapper;
        this.orderItemDao = orderItemDao;
        this.memberMapper = memberMapper;
        this.addressMapper = addressMapper;
        this.productMapper = productMapper;
        this.skuStockMapper = skuStockMapper;
        this.flashSaleSkuStockDao = flashSaleSkuStockDao;
        this.redisService = redisService;
    }

    @Transactional
    @Override
    public Long createAcceptance(FlashSaleOrderMessage message) {
        SmsFlashPromotionOrder acceptance = new SmsFlashPromotionOrder();
        acceptance.setFlashPromotionId(message.getPromotionId());
        acceptance.setFlashPromotionSessionId(message.getSessionId());
        acceptance.setProductId(message.getProductId());
        acceptance.setProductSkuId(message.getProductSkuId());
        acceptance.setMemberId(message.getMemberId());
        acceptance.setQuantity(message.getQuantity());
        acceptance.setFlashPromotionPrice(resolvePrice(message));
        acceptance.setStatus(0);
        acceptance.setCreateTime(new Date());
        try {
            acceptanceMapper.insert(acceptance);
            return acceptance.getId();
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_promotion_session_product_member 冲突 = 重复投递，幂等丢弃
            LOGGER.info("重复的秒杀订单消息已幂等丢弃: promotionId={}, memberId={}",
                    message.getPromotionId(), message.getMemberId());
            return null;
        }
    }

    @Transactional
    @Override
    public OmsOrder createOrder(FlashSaleOrderMessage message, Long acceptanceId) {
        UmsMember member = memberMapper.selectByPrimaryKey(message.getMemberId());
        UmsMemberReceiveAddress address = message.getMemberReceiveAddressId() == null
                ? null : addressMapper.selectByPrimaryKey(message.getMemberReceiveAddressId());
        PmsProduct product = productMapper.selectByPrimaryKey(message.getProductId());
        PmsSkuStock sku = message.getProductSkuId() == null
                ? null : skuStockMapper.selectByPrimaryKey(message.getProductSkuId());
        BigDecimal price = resolvePrice(message);
        int quantity = message.getQuantity() == null ? 1 : message.getQuantity();

        OmsOrder order = new OmsOrder();
        order.setMemberId(message.getMemberId());
        order.setMemberUsername(member == null ? null : member.getUsername());
        order.setOrderSn(generateOrderSn());
        order.setCreateTime(new Date());
        // mall 的订单不存地址id，只存收货快照七字段（receiver*），与原生 generateOrder 一致
        if (address != null) {
            order.setReceiverName(address.getName());
            order.setReceiverPhone(address.getPhoneNumber());
            order.setReceiverPostCode(address.getPostCode());
            order.setReceiverProvince(address.getProvince());
            order.setReceiverCity(address.getCity());
            order.setReceiverRegion(address.getRegion());
            order.setReceiverDetailAddress(address.getDetailAddress());
        }
        order.setStatus(0);
        // mall 预留字段（0->正常订单；1->秒杀订单）此前在代码里永远写 0，本模块是第一个真实写入者
        order.setOrderType(1);
        order.setDeleteStatus(0);
        order.setPayType(message.getPayType() == null ? 0 : message.getPayType());
        order.setSourceType(1);
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPromotionAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setIntegrationAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        OmsOrderItem orderItem = new OmsOrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setOrderSn(order.getOrderSn());
        orderItem.setProductId(message.getProductId());
        orderItem.setProductSkuId(message.getProductSkuId());
        if (product != null) {
            orderItem.setProductName(product.getName());
            orderItem.setProductPic(product.getPic());
            orderItem.setProductSn(product.getProductSn());
            orderItem.setProductCategoryId(product.getProductCategoryId());
            orderItem.setProductBrand(product.getBrandName());
        }
        if (sku != null) {
            orderItem.setProductSkuCode(sku.getSkuCode());
        }
        orderItem.setProductQuantity(quantity);
        orderItem.setProductPrice(price);
        orderItem.setPromotionName("秒杀专场");
        orderItem.setPromotionAmount(BigDecimal.ZERO);
        orderItem.setCouponAmount(BigDecimal.ZERO);
        orderItem.setIntegrationAmount(BigDecimal.ZERO);
        orderItem.setRealAmount(totalAmount);
        orderItem.setGiftIntegration(0);
        orderItem.setGiftGrowth(0);
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        orderItemList.add(orderItem);
        orderItemDao.insertList(orderItemList);

        // 原子递增锁定库存：单条 UPDATE 自增，规避 mall 原有读-改-写丢更新竞态。
        // 秒杀订单也锁定 SKU 库存，使超时取消（释放锁定）与支付成功（实扣）沿用 mall 原有语义
        if (message.getProductSkuId() != null) {
            flashSaleSkuStockDao.increaseLockStock(message.getProductSkuId(), quantity);
        }

        SmsFlashPromotionOrder update = new SmsFlashPromotionOrder();
        update.setId(acceptanceId);
        update.setOrderId(order.getId());
        update.setOrderSn(order.getOrderSn());
        update.setStatus(1);
        acceptanceMapper.updateByPrimaryKeySelective(update);

        LOGGER.info("秒杀订单落库成功: orderSn={}, ticket={}", order.getOrderSn(), message.getTicket());
        return order;
    }

    /**
     * 秒杀价以服务端为准：消息带价用消息价（发送端已从关系表取过），
     * 未带价则现查关系表——价格永远不信任前端
     */
    private BigDecimal resolvePrice(FlashSaleOrderMessage message) {
        if (message.getFlashPromotionPrice() != null) {
            return message.getFlashPromotionPrice();
        }
        SmsFlashPromotionProductRelationExample example = new SmsFlashPromotionProductRelationExample();
        example.createCriteria().andFlashPromotionIdEqualTo(message.getPromotionId())
                .andFlashPromotionSessionIdEqualTo(message.getSessionId())
                .andProductIdEqualTo(message.getProductId());
        List<SmsFlashPromotionProductRelation> relations = relationMapper.selectByExample(example);
        if (relations.isEmpty() || relations.get(0).getFlashPromotionPrice() == null) {
            throw new IllegalArgumentException("秒杀价缺失且商品未配置秒杀关系: productId=" + message.getProductId());
        }
        return relations.get(0).getFlashPromotionPrice();
    }

    /**
     * 订单号与 mall 普通订单共用同一 Redis 自增序列，天然不冲突
     */
    private String generateOrderSn() {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ORDER_ID + date;
        Long increment = redisService.incr(key, 1);
        sb.append(date);
        sb.append(String.format("%02d", 1));
        sb.append(String.format("%02d", 0));
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }
}
