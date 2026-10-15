package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("commission_rule")
public class CommissionRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String ruleName;
    private String businessType;
    private Long targetId;
    private String calculationMethod;
    private BigDecimal rate;
    private BigDecimal fixedAmount;
    private Integer priority;
    private Integer versionNo;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private Integer status;
    private Integer isActive;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
