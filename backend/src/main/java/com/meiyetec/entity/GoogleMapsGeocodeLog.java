package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EXT-17：Google Maps 地理编码日志
 *
 * <p>每次调用 geocode() 写入一行，用于：
 * <ol>
 *   <li>每日计费对账（对照 GCP 后台用量）</li>
 *   <li>命中率分析（OK / ZERO_RESULTS / OVER_QUERY_LIMIT）</li>
 *   <li>性能监控（durationMs）</li>
 * </ol>
 */
@Data
@TableName("google_maps_geocode_log")
public class GoogleMapsGeocodeLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;

    /** 请求地址 */
    private String address;
    /** 规范化地址（响应 results[0].formatted_address） */
    private String formattedAddress;
    /** 纬度 */
    private BigDecimal lat;
    /** 经度 */
    private BigDecimal lng;
    /** Place ID */
    private String placeId;

    /** 状态：OK / ZERO_RESULTS / OVER_QUERY_LIMIT / REQUEST_DENIED / UNKNOWN_ERROR */
    private String status;
    private String errorMessage;
    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime requestAt;
}
