package com.takeout.auth;

public record LoginVO(
        String accessToken,
        String refreshToken,
        UserInfoVO userInfo
) {}
