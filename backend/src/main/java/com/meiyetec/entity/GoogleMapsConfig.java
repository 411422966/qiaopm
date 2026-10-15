package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * EXT-17：Google Maps 配置
 *
 * <p>结构对齐 {@link MktChannelConfig}，差异点：
 * <ul>
 *   <li>{@code apiKey} 必填（Google Maps API Key）</li>
 *   <li>{@code defaultLanguage} / {@code defaultRegion}：地理编码参数</li>
 *   <li>{@code quotaDaily}：每日调用预算（对照 GCP 配额）</li>
 * </ul>
 */
@Data
@TableName("google_maps_config")
public class GoogleMapsConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;

    /** Google Maps API Key（出参时只回显后 4 位） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /** 默认语言（zh-CN / en / ...） */
    private String defaultLanguage;
    /** 默认区域（cn / us / ...） */
    private String defaultRegion;
    /** 每日配额 */
    private Integer quotaDaily;
    /** 每分钟限流 */
    private Integer rateLimitPerMin;
    /** 1=启用 0=停用 */
    private Integer enabled;
    /** 1=启用 Place Details（计费接口）0=关闭，默认 0 */
    private Integer enablePlaceDetails;

    private LocalDateTime lastHealthAt;
    private String lastHealthMsg;

    /** 其它配置 JSON（地图样式 / 默认缩放 / 自定义区域） */
    private String configJson;

    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
