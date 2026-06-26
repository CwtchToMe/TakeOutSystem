package com.takeout.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartVO(
        Long id, Long merchantId, Long dishId,
        String dishName, String dishImage,
        BigDecimal unitPrice, String spec, Integer quantity,
        LocalDateTime updatedAt
) {}
