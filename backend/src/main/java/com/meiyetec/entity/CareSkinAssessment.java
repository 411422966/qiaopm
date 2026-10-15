package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("care_skin_assessment")
public class CareSkinAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String assessmentNo;
    private String skinType;
    private Integer oilLevel;
    private Integer moistureLevel;
    private Integer elasticityLevel;
    private Integer pigmentLevel;
    private Integer poreLevel;
    private Integer wrinkleLevel;
    private String concernsJson;
    private Integer skinScore;
    private Long recommendedRecipeId;
    private String recommendedRecipeIds;
    private String analysisJson;
    private String source;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime assessmentDate;
    private LocalDateTime nextAssessmentDate;
}