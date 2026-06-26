package com.takeout.product;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_dish")
public class Dish {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private Integer status;
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
