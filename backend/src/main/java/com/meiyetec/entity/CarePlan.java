package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("care_plan")
public class CarePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long memberId;
    private String planCode;
    private String planName;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer cycleDays;
    private Integer sessionsTotal;
    private BigDecimal sessionsPerWeek;
    private BigDecimal estimatedCost;

    private String stepsJson;
    private String reasonJson;

    private Long treatmentPlanId;
    private String serviceIds;
    private String productIds;

    private String status;
    private BigDecimal adherenceScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}