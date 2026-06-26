package com.takeout.merchant;

import java.math.BigDecimal;
import java.time.LocalTime;

public record UpdateMerchantRequest(
        String name,
        String logoUrl,
        String description,
        String phone,
        BigDecimal deliveryFee,
        BigDecimal minOrderPrice,
        Integer deliveryTime,
        LocalTime openTime,
        LocalTime closeTime
) {}
