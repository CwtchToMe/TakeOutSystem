package com.takeout.product;

import java.math.BigDecimal;

public record DishSpecRequest(String name, String value, BigDecimal price) {}
