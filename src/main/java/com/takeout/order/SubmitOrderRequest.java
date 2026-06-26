package com.takeout.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitOrderRequest(
        @NotNull(message = "商家ID不能为空") Long merchantId,
        @NotNull(message = "收货地址不能为空") Long addressId,
        @NotEmpty(message = "订单商品不能为空") List<CartItemDTO> items,
        String remark,
        Integer payType,
        String userCouponId
) {}
