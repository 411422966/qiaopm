package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("market_coupon_grant")
public class CouponGrant {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;
    private Long memberId;
    private Long tenantId;
    private String code;
    private Integer status;      // 1未使用 2已使用 3已过期
    private Integer source;     // 1主动领取 2系统发放 3活动奖励
    private Long orderId;
    private LocalDateTime usedAt;
    private LocalDateTime expireTime;
    /** B-5 · 乐观锁版本号（核销时 CAS） */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String couponName;

    @TableField(exist = false)
    private String memberName;
}
