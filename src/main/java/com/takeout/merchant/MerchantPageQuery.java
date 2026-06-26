package com.takeout.merchant;

public record MerchantPageQuery(
        Integer status,
        String keyword,
        int page,
        int size
) {
    public MerchantPageQuery {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
    }
}
