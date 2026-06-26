package com.takeout.user;

public record UserPageQuery(String role, Integer status, String keyword, int page, int size) {
    public UserPageQuery {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
    }
}
