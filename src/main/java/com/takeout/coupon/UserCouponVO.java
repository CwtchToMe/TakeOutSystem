package com.takeout.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserCouponVO(
        String id,
        Long couponId,
        String title,
        BigDecimal minOrderPrice,
        BigDecimal discount,
        Integer status,
        LocalDateTime validEnd,
        LocalDateTime createdAt
) {}
