package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pos_refund_item")
public class PosRefundItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long refundId;
    private Long paymentId;
    private String paymentMethod;
    private BigDecimal paymentAmount;
    private BigDecimal refundAmount;
    private String paymentNo;
    /** PENDING / SUCCESS / FAILED */
    private String status;
    private String errorMsg;
    private String channelTxnId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
