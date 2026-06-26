package com.takeout.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantVO {
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
    private Integer status;
    private BigDecimal minOrderPrice;
    private BigDecimal deliveryFee;
    private Integer deliveryTime;
    private BigDecimal score;
    private Integer salesCount;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalDateTime createdAt;
}
