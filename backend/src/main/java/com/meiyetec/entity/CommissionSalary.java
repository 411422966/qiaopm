package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_salary")
public class CommissionSalary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long employeeId;
    private String employeeName;
    private String salaryMonth;
    private BigDecimal baseSalary;
    private BigDecimal performanceAmount;
    private BigDecimal commissionAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal payableAmount;
    private Integer status;
    private LocalDateTime confirmTime;
    private LocalDateTime payTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
