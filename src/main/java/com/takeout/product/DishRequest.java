package com.takeout.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record DishRequest(
        @NotNull Long merchantId,
        @NotNull Long categoryId,
        @NotBlank(message = "菜品名称不能为空") String name,
        String imageUrl,
        String description,
        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.00", inclusive = false, message = "价格必须大于 0") BigDecimal price,
        Integer stock,
        Integer sort,
        List<DishSpecRequest> specs
) {}
