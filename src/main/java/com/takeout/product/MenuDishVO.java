package com.takeout.product;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MenuDishVO {
    private Long id;
    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private Integer sort;
}
