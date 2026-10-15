package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_incident")
public class DrIncident {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String incidentNo;
    private String title;
    private String category;
    /** P1 / P2 / P3 / P4 */
    private String severity;
    /** OPEN / INVESTIGATING / MITIGATING / RESOLVED / POSTMORTEM */
    private String status;
    private String affectedServices;
    private Long runbookId;
    private String reportedBy;
    private LocalDateTime reportedAt;
    private LocalDateTime resolvedAt;
    private String rootCause;
    private String resolution;
    private Integer durationSeconds;
    private String customerImpact;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}