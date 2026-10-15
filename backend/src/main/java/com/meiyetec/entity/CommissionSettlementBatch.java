package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("commission_settlement_batch")
public class CommissionSettlementBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String batchNo;
    private String batchType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String salaryMonth;
    private Integer employeeCount;
    private Integer recordCount;
    private BigDecimal totalCommission;
    private BigDecimal totalAdjustment;
    private BigDecimal totalPayable;
    private Integer status;
    private LocalDateTime confirmTime;
    private Long confirmBy;
    private String confirmByName;
    private LocalDateTime payTime;
    private Long payBy;
    private String payByName;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
