package com.takeout.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull Integer status) {}
