package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("no_show_prediction")
public class NoShowPrediction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long bookingId;
    private Long memberId;
    private String modelCode;
    private BigDecimal riskScore;
    private String riskLevel;
    private String featuresJson;
    private String reason;
    private String actualStatus;
    private LocalDateTime feedbackAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime predictedAt;
}