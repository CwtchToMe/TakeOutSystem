package com.takeout.product;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_dish_spec")
public class DishSpec {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long dishId;
    private String name;
    private String value;
    private BigDecimal price;
}
