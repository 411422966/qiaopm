package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("subscription_member")
public class SubscriptionMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String subNo;
    private Long planId;
    private String planCode;
    private Long memberId;
    private LocalDate startDate;
    private LocalDate nextBillDate;
    private LocalDate endDate;
    private Integer usedCount;
    private Integer remainingCount;
    private Integer autoRenew;
    private String status;
    private String paymentMethod;
    private BigDecimal totalPaid;
    private Integer renewCount;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}