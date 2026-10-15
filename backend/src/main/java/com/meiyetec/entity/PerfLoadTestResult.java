package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P3.2 生产压测：压测结果记录
 */
@Data
@TableName("perf_load_test_result")
public class PerfLoadTestResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 压测名称 */
    private String testName;

    /** 场景数量 */
    private Integer sceneCount;

    /** 总请求数 */
    private Long totalRequests;

    /** 成功请求数 */
    private Long successCount;

    /** 失败请求数 */
    private Long failureCount;

    /** 错误率 % */
    private BigDecimal errorRate;

    /** 实际 QPS */
    private BigDecimal actualQps;

    /** 预期 QPS */
    private BigDecimal expectedQps;

    /** 平均响应时间 ms */
    private BigDecimal avgLatencyMs;

    /** P50 响应时间 ms */
    private BigDecimal p50LatencyMs;

    /** P95 响应时间 ms */
    private BigDecimal p95LatencyMs;

    /** P99 响应时间 ms */
    private BigDecimal p99LatencyMs;

    /** 最大响应时间 ms */
    private BigDecimal maxLatencyMs;

    /** TPS */
    private BigDecimal tps;

    /** 并发数 */
    private Integer concurrency;

    /** 持续时间 秒 */
    private Integer durationSec;

    /** 压测结论 */
    private String conclusion;

    /** 报告 URL */
    private String reportUrl;

    /** 状态 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
