package com.takeout.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("UPDATE t_coupon SET received_count = received_count + 1 " +
            "WHERE id = #{id} AND received_count < total_count AND deleted = 0")
    int incrementReceivedCount(Long id);
}
