package com.takeout.product;

import java.math.BigDecimal;

public record DishSnapshotVO(
        Long dishId,
        String dishName,
        String dishImage,
        BigDecimal unitPrice,
        Integer quantity
) {}
