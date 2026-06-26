package com.takeout.product;

public record DishPageQuery(Long merchantId, Long categoryId, String keyword, Integer status, int page, int size) {
    public DishPageQuery {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
    }
}
