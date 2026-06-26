package com.takeout.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        @NotNull(message = "商家ID不能为空") Long merchantId,
        @NotBlank(message = "分类名称不能为空") String name,
        Integer sort,
        Integer status
) {}
