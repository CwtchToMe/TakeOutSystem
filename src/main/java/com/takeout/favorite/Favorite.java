package com.takeout.favorite;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_favorite")
public class Favorite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long merchantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
