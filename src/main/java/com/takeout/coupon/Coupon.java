package com.takeout.coupon;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_coupon")
public class Coupon {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private Integer type;
    private BigDecimal minOrderPrice;
    private BigDecimal discount;
    private Integer totalCount;
    private Integer receivedCount;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
