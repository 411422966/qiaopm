package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("skin_profile")
public class SkinProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long memberId;
    private LocalDate assessDate;

    private String skinType;
    private Integer oilLevel;
    private Integer moistureLevel;
    private Integer elasticityLevel;
    private Integer pigmentationLevel;
    private Integer sensitivityLevel;
    private Integer poreLevel;
    private Integer wrinkleLevel;

    private String concerns;
    private String allergies;
    private String lifestyleTags;

    private String ageGroup;
    private String lifeStage;

    private String deviceReportUrl;
    private Long assessorId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}