package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("map_config")
public class MapConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String provider;
    private String apiKey;
    private BigDecimal defaultLat;
    private BigDecimal defaultLng;
    private Integer defaultZoom;
    private String region;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}