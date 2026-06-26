package com.takeout.merchant;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("t_merchant")
public class Merchant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerId;
    private String name;
    private String logoUrl;
    private String description;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal deliveryFee;
    private BigDecimal minOrderPrice;
    private Integer deliveryTime;
    private Integer status;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer salesCount;
    private BigDecimal score;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
