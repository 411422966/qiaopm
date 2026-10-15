package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dianping_shop")
public class DianpingShop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String shopId;
    private String shopName;
    private String appKey;
    private String appSecret;
    private String accessToken;
    private LocalDateTime tokenExpireAt;
    private String category;
    private String city;
    private String businessKey;
    private String webhookUrl;
    private LocalDateTime lastSyncAt;
    private String status;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}