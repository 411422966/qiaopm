package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P3.1 全链路追踪：Trace Span 日志
 */
@Data
@TableName("trace_span")
public class TraceSpan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private String spanId;

    /** 调用方 spanId（根调用为空） */
    private String parentSpanId;

    /** 服务名 */
    private String serviceName;

    /** 方法名 / 操作名 */
    private String operationName;

    private String httpMethod;
    private String uri;

    /** 状态：SUCCESS / ERROR / TIMEOUT */
    private String status;

    /** HTTP 状态码 */
    private Integer httpStatus;

    /** 错误码 */
    private String errorCode;

    /** 开始时间 */
    private LocalDateTime startAt;

    /** 耗时 ms */
    private Long durationMs;

    /** 租户 ID */
    private Long tenantId;

    /** 用户 ID */
    private Long userId;

    /** 额外标签（JSON） */
    private String tags;

    /** 调用来源 IP */
    private String clientIp;

    /** User-Agent */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
