package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lbs_route_plan")
public class LbsRoutePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String planNo;
    private Long technicianId;
    private LocalDate planDate;
    private BigDecimal startLat;
    private BigDecimal startLng;
    private Integer totalOrders;
    private BigDecimal totalDistanceKm;
    private Integer totalTravelMinutes;
    private Integer totalServiceMinutes;
    private LocalDateTime etaComplete;
    private String algorithm;
    private BigDecimal optimizeGainKm;
    /** DRAFT / ACTIVE / COMPLETED / CANCELLED */
    private String status;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}