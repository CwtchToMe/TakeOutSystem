package com.takeout.order;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddCartRequest(
        @NotNull Long merchantId,
        @NotNull Long dishId,
        String dishName,
        String dishImage,
        BigDecimal unitPrice,
        String spec,
        @NotNull Integer quantity
) {}
