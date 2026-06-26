package com.takeout.user;

import java.time.LocalDateTime;

public record UserVO(
        Long id,
        String phone,
        String nickname,
        String avatarUrl,
        Integer gender,
        String role,
        Integer status,
        LocalDateTime createdAt
) {}
