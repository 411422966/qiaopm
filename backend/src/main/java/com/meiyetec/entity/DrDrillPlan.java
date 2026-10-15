package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dr_drill_plan")
public class DrDrillPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planCode;
    private String planName;
    /** TABLETOP / SIMULATION / PARTIAL_FAILOVER / FULL_FAILOVER */
    private String drillType;
    private LocalDateTime scheduledAt;
    private BigDecimal estimatedHours;
    private String scope;
    private String participants;
    private String scenario;
    private String objectives;
    /** SCHEDULED / RUNNING / COMPLETED / CANCELLED */
    private String status;
    private String owner;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}