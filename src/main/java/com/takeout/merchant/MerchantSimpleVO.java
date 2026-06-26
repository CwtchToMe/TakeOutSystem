package com.takeout.merchant;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSimpleVO {
    private Long id;
    private String name;
    private String logoUrl;
    private BigDecimal score;
    private Integer salesCount;
    private BigDecimal minOrderPrice;
    private BigDecimal deliveryFee;
    private Integer deliveryTime;
    private Integer status;
    private BigDecimal distance;
}
