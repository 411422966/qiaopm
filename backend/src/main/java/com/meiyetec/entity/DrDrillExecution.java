package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_drill_execution")
public class DrDrillExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String planCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** 1 成功 / 0 失败 / NULL 进行中 */
    private Integer success;
    private Integer rtoActualSec;
    private Integer rpoActualSec;
    private String issuesFound;
    private String lessonsLearned;
    /** 演练评分 0-100 */
    private Integer score;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}