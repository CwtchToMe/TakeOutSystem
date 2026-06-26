package com.takeout.order;

public record MerchantOrderPageQuery(Long merchantId, Integer status, int page, int size) {
    public MerchantOrderPageQuery {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
    }
}
