package com.takeout.order;

import jakarta.validation.constraints.NotBlank;

public record CreatePayRequest(@NotBlank String orderNo, Integer payType) {}
