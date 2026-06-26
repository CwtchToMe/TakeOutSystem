package com.takeout.coupon;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_user_coupon")
public class UserCoupon {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long couponId;
    private String title;
    private BigDecimal minOrderPrice;
    private BigDecimal discount;
    private Integer status;
    private LocalDateTime validEnd;
    private LocalDateTime usedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
