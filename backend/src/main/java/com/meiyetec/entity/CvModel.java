package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cv_model")
public class CvModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String modelCode;
    private String modelName;
    private String provider;
    private String modelType;
    private String endpoint;
    private String apiKey;
    private String apiSecret;
    private String version;
    private BigDecimal accuracy;
    private Integer latencyMs;
    private Integer isSandbox;
    private Integer enabled;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}