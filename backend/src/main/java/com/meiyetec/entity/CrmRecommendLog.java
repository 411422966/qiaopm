package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R3.0-F P1-5：推荐算法日志（曝光/点击/转化）
 */
@Data
@TableName("crm_recommend_log")
public class CrmRecommendLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    /** COUPON/PACKAGE/SERVICE/PUNCH/IMAGE */
    private String recommendType;
    private Long targetId;
    private String targetName;
    private BigDecimal score;
    private String reason;
    private String channel;
    private Integer shown;
    private Integer clicked;
    private Integer converted;
    private LocalDateTime exposedAt;
    private LocalDateTime clickedAt;
    private LocalDateTime convertedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}