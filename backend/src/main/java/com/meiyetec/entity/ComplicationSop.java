package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("complication_sop")
public class ComplicationSop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sopCode;
    private String sopName;
    private String category;
    private String severity;
    private String triggerSymptom;
    private String triggerTiming;
    private String stepsJson;
    private String medications;
    private String equipment;
    private String escalationRule;
    private Integer responseDeadlineMin;
    private String responsibleRole;
    private String version;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}