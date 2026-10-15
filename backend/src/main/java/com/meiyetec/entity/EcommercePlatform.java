package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ecommerce_platform")
public class EcommercePlatform {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String platform;
    private String shopName;
    private String shopId;
    private String appKey;
    // I028：appSecret / signSecret 加 @JsonProperty(WRITE_ONLY) — POST 请求可写入，GET 响应不回显
    // - 防历史 GET /ecommerce/platform/list 明文返回 secrets（已存在 DB 数据的清洗见 I091）
    // - 前端 type="password" + show-password 输入
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String appSecret;
    /** 有赞：开放平台 AppID（区别于 appKey，用于消息验签） */
    private String appId;
    /** 有赞：店铺 ID（kdtId），签名 / 回调 URL 构造用 */
    private String kdtId;
    /** 有赞：消息签名密钥（webhook / API 回调验签用，HTTP 头 X-Youzan-Sign） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String signSecret;
    private String accessToken;
    private LocalDateTime tokenExpireAt;
    private String webhookUrl;
    private String webhookSecret;
    private String apiBaseUrl;
    private Integer syncProducts;
    private Integer syncOrders;
    private Integer syncInventory;
    private LocalDateTime lastSyncAt;
    private String status;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}