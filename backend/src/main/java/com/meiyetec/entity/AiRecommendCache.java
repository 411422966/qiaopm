package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_recommend_cache")
public class AiRecommendCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    /** SERVICE / PRODUCT / COUPON / CONTENT */
    private String recommendType;
    private String recommendItems;
    /** COLLAB-V1 / CONTENT-V1 / HOT-V1 */
    private String algorithm;
    private BigDecimal score;
    private String cacheKey;
    private LocalDateTime expiresAt;
    private Integer hitCount;
    private LocalDateTime lastHitAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}