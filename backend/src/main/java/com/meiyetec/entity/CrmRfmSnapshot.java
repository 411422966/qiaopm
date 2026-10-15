package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_rfm_snapshot")
public class CrmRfmSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private Integer recencyDays;
    private Integer frequency;
    private BigDecimal monetary;
    private Integer rScore;
    private Integer fScore;
    private Integer mScore;
    private String rfmScore;
    /** CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/CANT_LOSE/HIBERNATING */
    private String segment;
    private String segmentName;
    private String explanation;
    private LocalDate snapshotDate;
    private String calcMode;
    private Integer calcDurationMs;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}