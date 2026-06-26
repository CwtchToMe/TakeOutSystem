package com.takeout.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DishVO(
        Long id, Long merchantId, Long categoryId,
        String name, String imageUrl, String description,
        BigDecimal price, Integer stock, Integer sales, Integer status, Integer sort,
        LocalDateTime createdAt,
        List<DishSpecVO> specs
) {}
