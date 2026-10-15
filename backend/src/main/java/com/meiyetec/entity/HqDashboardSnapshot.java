package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hq_dashboard_snapshot")
public class HqDashboardSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** DAY / WEEK / MONTH */
    private String periodType;
    /** 2026-08-14 */
    private String periodKey;
    private Long brandId;
    private String widgetCode;
    private BigDecimal metricValue;
    private String extra;
    private LocalDateTime snapshotAt;
}