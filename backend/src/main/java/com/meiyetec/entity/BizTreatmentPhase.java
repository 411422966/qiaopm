package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_treatment_phase")
public class BizTreatmentPhase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long planId;
    private Integer phaseNo;
    private String phaseName;
    private Integer sessionCount;
    private String sop;
    private Integer expectedDuration;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
