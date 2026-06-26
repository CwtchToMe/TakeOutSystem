package com.takeout.auth;

public record UserInfoVO(
        Long id,
        String phone,
        String nickname,
        String avatarUrl,
        String role
) {}
