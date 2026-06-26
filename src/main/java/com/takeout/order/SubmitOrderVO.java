package com.takeout.order;

import java.math.BigDecimal;

public record SubmitOrderVO(String orderNo, BigDecimal actualPrice) {}
