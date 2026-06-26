package com.takeout.merchant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

    List<MerchantSimpleVO> selectNearby(
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("radius") int radius,
            @Param("offset") long offset,
            @Param("size") long size);

    long countNearby(
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("radius") int radius);
}
