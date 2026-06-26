package com.takeout.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(
        Long id, String orderNo, Long userId, Long merchantId, Long riderId,
        Integer status,
        BigDecimal totalPrice, BigDecimal deliveryFee, BigDecimal actualPrice,
        BigDecimal discount,
        String receiver, String phone, String address,
        String remark, String cancelReason,
        Integer payType, LocalDateTime payTime,
        LocalDateTime estimatedTime, LocalDateTime deliveryTime,
        LocalDateTime createdAt,
        List<OrderItemVO> items,
        String merchantName
) {}
