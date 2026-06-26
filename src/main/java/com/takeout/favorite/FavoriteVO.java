package com.takeout.favorite;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FavoriteVO(
        Long id,
        Long merchantId,
        String merchantName,
        String logoUrl,
        BigDecimal score,
        String description,
        BigDecimal deliveryFee,
        Integer deliveryTime,
        LocalDateTime createdAt
) {}
