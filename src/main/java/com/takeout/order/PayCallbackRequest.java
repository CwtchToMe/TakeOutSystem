package com.takeout.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayCallbackRequest(@NotBlank String paymentNo, @NotNull Boolean success) {}
