package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lbs_service_zone")
public class LbsServiceZone {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String zoneCode;
    private String zoneName;
    /** CIRCLE 圆形 / POLYGON 多边形 */
    private String zoneType;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
    private BigDecimal radiusKm;
    /** 多边形顶点 JSON [[lng,lat],...]（POLYGON 必填） */
    private String polygonJson;
    private BigDecimal serviceFeePerKm;
    private BigDecimal baseFee;
    private BigDecimal minFee;
    private BigDecimal maxFee;
    private Integer enabled;
    private Integer sortOrder;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}