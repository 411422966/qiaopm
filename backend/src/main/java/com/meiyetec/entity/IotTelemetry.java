package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("iot_telemetry")
public class IotTelemetry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String metric;
    private String valueStr;
    private BigDecimal valueNum;
    private String unit;
    private String topic;
    private String payload;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime recordedAt;
}