package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_record")
public class CommissionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long employeeId;
    private String employeeName;
    private Long ruleId;
    private String ruleName;
    private String businessType;
    private String sourceType;
    private Long sourceId;
    private Long sourceItemId;
    private String orderNo;
    private Long serviceId;
    private String serviceName;
    private BigDecimal baseAmount;
    private String calculationMethod;
    private BigDecimal rate;
    private BigDecimal fixedAmount;
    private BigDecimal commissionAmount;
    private LocalDateTime businessTime;
    private String salaryMonth;
    private Long settlementId;
    private Integer status;
    private String invalidReason;
    private Integer reversalStatus;
    private Long reversalId;
    private Integer ruleVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
