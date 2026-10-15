package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("hq_dashboard_widget")
public class HqDashboardWidget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String widgetCode;
    private String widgetName;
    /** KPI / CHART / TABLE / LIST */
    private String widgetType;
    private String metricName;
    private String querySql;
    private Integer layoutX;
    private Integer layoutY;
    private Integer width;
    private Integer height;
    private Integer refreshSec;
    private Integer enabled;
    private Integer sortOrder;
}