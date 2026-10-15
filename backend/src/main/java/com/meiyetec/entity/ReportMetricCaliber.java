package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_metric_caliber")
public class ReportMetricCaliber {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String metricKey;
    private String metricName;
    private String category;
    private String formula;
    private String sqlTemplate;
    private String unit;
    private String description;
    private String version;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
