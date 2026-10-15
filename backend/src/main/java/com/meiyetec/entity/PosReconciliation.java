package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pos_reconciliation")
public class PosReconciliation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 对账日期 */
    private java.time.LocalDate reconcileDate;
    /** 支付通道 */
    private String channel;
    private String channelTxnId;
    private Long orderId;
    private String orderNo;
    private Long paymentId;
    private java.math.BigDecimal channelAmount;
    private java.math.BigDecimal localAmount;
    private java.math.BigDecimal diffAmount;
    /** MATCHED / DIFF_LOCAL_MORE / DIFF_CHANNEL_MORE / DIFF_AMOUNT / MISSING_LOCAL / MISSING_CHANNEL */
    private String status;
    /** PENDING / HANDLED / IGNORED */
    private String handleStatus;
    private Long handleBy;
    private String handleByName;
    private java.time.LocalDateTime handleTime;
    private String handleNote;
    private String rawPayload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
