package com.takeout.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitReviewRequest(
        @NotBlank(message = "订单号不能为空") String orderNo,
        @NotNull @Min(1) @Max(5) Integer score,
        String content
) {}
