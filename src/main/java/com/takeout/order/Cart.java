package com.takeout.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_cart")
public class Cart {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long merchantId;
    private Long dishId;
    private String dishName;
    private String dishImage;
    private BigDecimal unitPrice;
    private String spec;
    private Integer quantity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
