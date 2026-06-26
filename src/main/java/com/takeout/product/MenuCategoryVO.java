package com.takeout.product;

import lombok.Data;

import java.util.List;

@Data
public class MenuCategoryVO {
    private Long id;
    private String name;
    private Integer sort;
    private List<MenuDishVO> dishes;
}
