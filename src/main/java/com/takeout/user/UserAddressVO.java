package com.takeout.user;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserAddressVO(
        Long id,
        Long userId,
        String receiver,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        BigDecimal longitude,
        BigDecimal latitude,
        Integer isDefault,
        LocalDateTime createdAt
) {}
