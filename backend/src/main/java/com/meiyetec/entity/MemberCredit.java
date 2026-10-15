package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member_credit")
public class MemberCredit {
    @TableId(type = IdType.INPUT)
    private Long memberId;
    private Long tenantId;
    private Integer credit;
    private Integer noShowCount;
    private Integer totalBookings;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastNoShowAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
