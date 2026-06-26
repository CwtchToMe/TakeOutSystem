package com.takeout.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddressRequest(
        @NotBlank(message = "收件人不能为空") String receiver,
        @NotBlank(message = "联系电话不能为空") String phone,
        String province,
        String city,
        String district,
        @NotBlank(message = "详细地址不能为空") String detail,
        @NotNull(message = "经度不能为空") BigDecimal longitude,
        @NotNull(message = "纬度不能为空") BigDecimal latitude,
        Boolean isDefault
) {}
