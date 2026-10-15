package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pos_refund_reverse_log")
public class PosRefundReverseLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long refundId;
    /** PACKAGE / POINTS / STOCK / COMMISSION */
    private String reverseType;
    private Long refId;
    private String payload;
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
