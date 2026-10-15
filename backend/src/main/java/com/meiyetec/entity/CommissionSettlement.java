package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_settlement")
public class CommissionSettlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String settlementNo;
    private String salaryMonth;
    private Integer employeeCount;
    private BigDecimal totalCommission;
    private BigDecimal totalAdjustment;
    private BigDecimal totalPayable;
    private Integer status;
    private LocalDateTime confirmTime;
    private Long confirmBy;
    private LocalDateTime payTime;
    private Long payBy;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
