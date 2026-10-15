package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_expiry_dashboard_cache")
public class BizExpiryDashboardCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private LocalDate statDate;
    private Integer expiredCount;
    private Integer criticalCount;
    private Integer warningCount;
    private Integer normalCount;
    private BigDecimal nearExpiryValue;
    private BigDecimal expiredValue;
    private Integer pendingAlertCount;
    private BigDecimal lossAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
