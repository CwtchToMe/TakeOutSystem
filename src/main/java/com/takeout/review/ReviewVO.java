package com.takeout.review;

import java.time.LocalDateTime;

public record ReviewVO(
        Long id,
        String orderNo,
        Long userId,
        String nickname,
        Long merchantId,
        Integer score,
        String content,
        LocalDateTime createdAt
) {}
