package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("forecast_accuracy_log")
public class ForecastAccuracyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private String algorithm;
    private String modelVersion;
    private LocalDate evalDate;
    private Integer windowDays;

    private BigDecimal mae;
    private BigDecimal mape;
    private BigDecimal rmse;
    private BigDecimal smape;

    private BigDecimal weekdayMae;
    private BigDecimal weekendMae;

    private String conclusion;
    private String recommendation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
