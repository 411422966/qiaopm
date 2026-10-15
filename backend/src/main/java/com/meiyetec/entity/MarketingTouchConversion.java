package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("marketing_touch_conversion")
public class MarketingTouchConversion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long touchLogId;
    private Long taskId;
    /** ORDER/REDEMPTION/COUPON/MEMBER_REGISTER */
    private String conversionType;
    private Long relatedId;
    private String relatedNo;
    private BigDecimal amount;
    private LocalDateTime convertedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
