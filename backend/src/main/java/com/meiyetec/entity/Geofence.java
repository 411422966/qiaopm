package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("geofence")
public class Geofence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String fenceName;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
    private Integer radiusM;
    private String alertType;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}