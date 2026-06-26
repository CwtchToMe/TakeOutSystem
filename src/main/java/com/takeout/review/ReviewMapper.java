package com.takeout.review;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT AVG(score) FROM t_review WHERE merchant_id = #{merchantId} AND deleted = 0")
    BigDecimal avgScoreByMerchant(Long merchantId);
}
