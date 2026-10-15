package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pos_refund")
public class PosRefund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private String memberName;

    /** FULL / PART / CROSS */
    private String refundType;
    private BigDecimal refundAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private BigDecimal remainingAmount;

    /** ORIGINAL / WALLET / MIXED */
    private String refundStrategy;
    private BigDecimal cashRefund;
    private BigDecimal walletRefund;
    private BigDecimal thirdpartyRefund;

    private Integer reversePackage;
    private Integer reversePoints;
    private Integer reverseStock;
    private Integer reverseCommission;

    /** DRAFT/PENDING/PENDING_STORE/PENDING_HQ/PENDING_FINANCE/AUDIT/APPROVED/REJECTED/PROCESSING/SUCCESS/FAILED/CANCELLED */
    private String status;
    /** POS-008 多级审核阶段（独立于 status 的显式相位）：
     *  null/DRAFT/PENDING_STORE/PENDING_HQ/PENDING_FINANCE/APPROVED/REJECTED */
    private String auditStatus;
    /** 当前审核级别：0无需 / 1店长 / 2区域经理 / 3财务总监 */
    private Integer currentAuditLevel;
    /** 是否需要审核：0否 1是 */
    private Integer auditRequired;
    private String reason;
    private String rejectReason;
    private Long operatorId;
    private String operatorName;
    private Long auditorId;
    private String auditorName;

    private String channelTxnId;
    private String idempotencyKey;
    private String traceId;

    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 跨单退款分组 ID（多条 refund 共享） */
    private String crossGroupId;

    /** 乐观锁版本号 */
    private Integer version;

    @TableField(exist = false)
    private java.util.List<PosRefundItem> items;
    @TableField(exist = false)
    private java.util.List<PosRefundReverseLog> reverseLogs;
}
