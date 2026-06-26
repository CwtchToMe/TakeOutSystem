package com.takeout.merchant;

import jakarta.validation.constraints.NotNull;

public record AuditRequest(@NotNull Boolean approved, String reason) {}
