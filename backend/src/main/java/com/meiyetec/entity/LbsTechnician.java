package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("lbs_technician")
public class LbsTechnician {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long technicianId;
    private String name;
    private String phone;
    private BigDecimal currentLat;
    private BigDecimal currentLng;
    private BigDecimal maxRadiusKm;
    private Integer available;
    private String serviceIds;
    private BigDecimal rating;
    private String onlineStatus;
    private LocalDateTime lastLocTime;
    private String status;
    // Phase 3 扩展字段
    private String skillTags;                  // 技能标签 JSON 数组
    private String skillLevels;                // 技能等级 JSON {"MANICURE": 5}
    private BigDecimal homeBaseLat;            // 出发地纬度
    private BigDecimal homeBaseLng;            // 出发地经度
    private Long zoneId;                       // 默认服务区域 ID
    private Integer maxOrdersPerDay;            // 单日最大订单数
    private Integer avgServiceMinutes;          // 平均单订单服务时长
    private BigDecimal travelSpeedKmh;          // 平均时速 km/h
    private LocalTime workStart;                // 工作开始时间
    private LocalTime workEnd;                  // 工作结束时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}