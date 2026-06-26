package com.takeout.user;

public record UpdateProfileRequest(
        String nickname,
        String avatarUrl,
        Integer gender
) {}
