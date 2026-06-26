package com.takeout.product;

import java.math.BigDecimal;

public record DishSpecVO(Long id, String name, String value, BigDecimal price) {}
