package com.takeout.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Update("UPDATE t_user_coupon SET status = 1, used_at = NOW() WHERE id = #{id} AND status = 0")
    int markUsed(Long id);

    @Update("UPDATE t_user_coupon SET status = 0 WHERE id = #{id} AND status = 1")
    int refund(Long id);
}
