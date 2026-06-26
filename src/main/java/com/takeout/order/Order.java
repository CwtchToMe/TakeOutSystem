package com.takeout.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long riderId;
    private Integer status;
    private BigDecimal totalPrice;
    private BigDecimal deliveryFee;
    private BigDecimal actualPrice;
    private BigDecimal discount;
    private Long userCouponId;
    private String receiver;
    private String phone;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String remark;
    private String cancelReason;
    private Integer payType;
    private LocalDateTime payTime;
    private LocalDateTime estimatedTime;
    private LocalDateTime deliveryTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
