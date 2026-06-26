package com.takeout.common.util;

public final class PageUtil {

    private PageUtil() {}

    public static int page(Integer page) {
        return (page != null && page > 0) ? page : 1;
    }

    public static int size(Integer size) {
        return size(size, 10);
    }

    public static int size(Integer size, int defaultSize) {
        return (size != null && size > 0) ? size : defaultSize;
    }
}
