package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_promotion")
public class WalletPromotion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private BigDecimal rechargeAmount;
    private BigDecimal giftAmount;
    private String ruleName;
    private Integer sortOrder;
    private Integer status;

    /** P0-M05: 限时活动起止时间，null 表示常驻 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
