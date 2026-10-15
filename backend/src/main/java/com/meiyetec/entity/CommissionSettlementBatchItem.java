package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_settlement_batch_item")
public class CommissionSettlementBatchItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long batchId;
    private Long employeeId;
    private String employeeName;
    private BigDecimal baseSalary;
    private BigDecimal commissionAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal reversalAmount;
    private BigDecimal payableAmount;
    private Integer recordCount;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
