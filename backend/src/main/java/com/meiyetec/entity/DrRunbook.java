package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_runbook")
public class DrRunbook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runbookCode;
    private String title;
    /** DB_DOWN / NETWORK / SECURITY / DATA_CORRUPTION / PEAK_LOAD */
    private String category;
    /** P1 / P2 / P3 / P4 */
    private String severity;
    private String description;
    private String steps;
    private String escalation;
    private Integer estimatedMinutes;
    private LocalDateTime lastReviewedAt;
    private String owner;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}