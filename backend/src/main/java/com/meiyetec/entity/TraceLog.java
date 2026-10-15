package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P3.1 全链路追踪：调用链路日志
 */
@Data
@TableName("cloud_trace_log")
public class TraceLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Trace ID */
    private String traceId;

    /** 上一级 Span ID（根调用无） */
    private String parentSpanId;

    /** 当前 Span ID */
    private String spanId;

    /** 租户 */
    private Long tenantId;

    /** 服务名 */
    private String serviceName;

    /** 应用名（前端/后端模块） */
    private String appName;

    /** 调用类型 */
    private String callType;
    // HTTP_IN / HTTP_OUT / DB_QUERY / RPC_IN / RPC_OUT / METHOD_SYNC / METHOD_ASYNC / REDIS / SCHEDULED

    /** 请求路径 / 方法签名 */
    private String endpoint;

    /** HTTP Method / 操作类型 */
    private String method;

    /** 请求体摘要（最大 500 字符） */
    private String requestBody;

    /** 响应体摘要（最大 500 字符） */
    private String responseBody;

    /** HTTP 状态码 / 结果码 */
    private Integer statusCode;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 错误信息 */
    private String errorMsg;

    /** 客户端 IP */
    private String clientIp;

    /** 用户 ID（登录时） */
    private Long userId;

    /** 标签 JSON */
    private String tags;

    /** 阶段 */
    private String phase;
    // SERVER_RECV / SERVER_SEND / CLIENT_SEND / CLIENT_RECV

    /** 层级深度（0=根） */
    private Integer depth;

    /** 是否错误 */
    private Integer isError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
