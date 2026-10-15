package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * R3.0-F P1-5：需求预测（预约/订单/库存）
 */
@Data
@TableName("biz_forecast_demand")
public class BizForecastDemand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private LocalDate forecastDate;
    /** BOOKING/ORDER/STOCK */
    private String forecastType;
    private Long targetId;
    private String targetName;
    private BigDecimal predictedValue;
    private BigDecimal actualValue;
    private BigDecimal errorRate;
    private String modelVersion;
    private BigDecimal confidence;
    private String operatorName;
    private LocalDateTime forecastAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}