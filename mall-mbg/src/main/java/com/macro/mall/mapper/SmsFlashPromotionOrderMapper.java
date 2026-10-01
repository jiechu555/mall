package com.macro.mall.mapper;

import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsFlashPromotionOrderMapper {
    long countByExample(SmsFlashPromotionOrderExample example);

    int deleteByExample(SmsFlashPromotionOrderExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SmsFlashPromotionOrder row);

    int insertSelective(SmsFlashPromotionOrder row);

    List<SmsFlashPromotionOrder> selectByExample(SmsFlashPromotionOrderExample example);

    SmsFlashPromotionOrder selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") SmsFlashPromotionOrder row, @Param("example") SmsFlashPromotionOrderExample example);

    int updateByExample(@Param("row") SmsFlashPromotionOrder row, @Param("example") SmsFlashPromotionOrderExample example);

    int updateByPrimaryKeySelective(SmsFlashPromotionOrder row);

    int updateByPrimaryKey(SmsFlashPromotionOrder row);
}