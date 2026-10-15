package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking_forecast_result")
public class BookingForecastResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private LocalDate forecastDate;
    private Integer forecastHour;
    private String forecastType;

    private BigDecimal predictedValue;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private BigDecimal confidence;

    private String algorithm;
    private String modelVersion;
    private Integer baseWindowDays;

    private BigDecimal actualValue;
    private BigDecimal errorPct;
    private BigDecimal mae;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime generatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
