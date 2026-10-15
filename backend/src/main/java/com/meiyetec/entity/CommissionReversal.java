package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_reversal")
public class CommissionReversal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String reversalNo;
    private String reversalType;
    private String sourceType;
    private Long sourceId;
    private Long sourceItemId;
    private Long originalRecordId;
    private Long employeeId;
    private String employeeName;
    private BigDecimal amount;
    private String reason;
    private Integer status;
    private LocalDateTime approveTime;
    private Long approveBy;
    private String approveByName;
    private Long batchId;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
