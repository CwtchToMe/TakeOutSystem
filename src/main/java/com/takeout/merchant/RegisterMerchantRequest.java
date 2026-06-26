package com.takeout.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record RegisterMerchantRequest(
        @NotBlank(message = "店铺名称不能为空") String name,
        String logoUrl,
        String description,
        @NotBlank(message = "联系电话不能为空") String phone,
        String province, String city, String district,
        @NotBlank(message = "地址不能为空") String address,
        @NotNull(message = "经度不能为空") BigDecimal longitude,
        @NotNull(message = "纬度不能为空") BigDecimal latitude,
        BigDecimal deliveryFee,
        BigDecimal minOrderPrice,
        Integer deliveryTime,
        LocalTime openTime,
        LocalTime closeTime
) {}
