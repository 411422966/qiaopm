package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sla_target")
public class SlaTarget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String serviceCode;
    private String serviceName;
    /** 可用性 % 目标 */
    private BigDecimal availabilityTarget;
    /** 月度停机预算 30天 */
    private Integer monthlyDowntimeBudgetSec;
    private Integer responseP95Ms;
    private Integer responseP99Ms;
    /** 错误率 0.1% */
    private BigDecimal errorRateThreshold;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}