package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("schedule_recommendation")
public class ScheduleRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private LocalDate scheduleDate;

    private String recommendationJson;
    private BigDecimal coverageRate;
    private BigDecimal utilizationRate;
    private BigDecimal score;

    private String algorithm;
    private String modelVersion;

    private String status;
    private LocalDateTime appliedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
