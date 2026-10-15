package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_sales_forecast")
public class AiSalesForecast {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private LocalDate forecastDate;
    /** REVENUE / ORDERS / MEMBERS / NEW_CUSTOMERS */
    private String forecastType;
    private BigDecimal predictedValue;
    private BigDecimal upperBound;
    private BigDecimal lowerBound;
    /** 0.0~1.0 */
    private BigDecimal confidence;
    /** LINEAR / EWMA / HOLT_WINTERS / ENSEMBLE */
    private String algorithm;
    private Integer baseWindowDays;
    private String inputFeatures;
    private String comment;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}