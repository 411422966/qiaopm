package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dashboard_subscription")
public class DashboardSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dashboardId;
    private Long subscriberId;
    private String frequency;
    private String channel;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}