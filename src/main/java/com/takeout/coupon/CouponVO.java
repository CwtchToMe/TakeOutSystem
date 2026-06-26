package com.takeout.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponVO(
        Long id,
        String title,
        Integer type,
        BigDecimal minOrderPrice,
        BigDecimal discount,
        Integer totalCount,
        Integer receivedCount,
        LocalDateTime validStart,
        LocalDateTime validEnd
) {}
