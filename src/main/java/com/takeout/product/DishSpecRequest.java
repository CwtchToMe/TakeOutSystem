package com.takeout.product;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record DishSpecRequest(String name, String value,
        @DecimalMin(value = "0.00", inclusive = false, message = "规格价格不能为负数") BigDecimal price) {}
