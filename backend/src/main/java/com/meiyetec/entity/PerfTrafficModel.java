package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P3.2 生产压测：流量模型
 */
@Data
@TableName("perf_traffic_model")
public class PerfTrafficModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 场景名称 */
    private String sceneName;

    /** API 端点 */
    private String endpoint;

    /** HTTP 方法 */
    private String method;

    /** 权重（相对流量占比） */
    private Integer weight;

    /** 预期 QPS */
    private BigDecimal expectedQps;

    /** 平均响应时间 ms */
    private BigDecimal avgLatencyMs;

    /** P99 响应时间 ms */
    private BigDecimal p99LatencyMs;

    /** 错误率 % */
    private BigDecimal errorRate;

    /** 是否启用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
