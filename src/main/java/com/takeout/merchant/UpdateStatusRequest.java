package com.takeout.merchant;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull Integer status) {}
