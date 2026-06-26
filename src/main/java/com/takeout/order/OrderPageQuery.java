package com.takeout.order;

public record OrderPageQuery(Integer status, int page, int size) {
    public OrderPageQuery {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
    }
}
