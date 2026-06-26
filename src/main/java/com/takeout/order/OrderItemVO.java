package com.takeout.order;

import java.math.BigDecimal;

public record OrderItemVO(
        Long id, Long dishId, String dishName, String dishImage,
        String spec, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal
) {}
