package com.takeout.product;

import jakarta.validation.constraints.NotNull;

public record UpdateDishStatusRequest(@NotNull Integer status) {}
