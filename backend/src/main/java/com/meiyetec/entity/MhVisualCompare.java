package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mh_visual_compare")
public class MhVisualCompare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private Long beforeId;
    private Long afterId;
    /** SAME_DAY/CROSS_DAY/CROSS_TREATMENT */
    private String compareType;
    private BigDecimal similarity;
    private BigDecimal diffScore;
    private String skinDiff;
    private String aiSummary;
    private String improvementAreas;
    private String regressionAreas;
    private Integer manualReviewed;
    private Long reviewerId;
    private String reviewerName;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private String modelVersion;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}