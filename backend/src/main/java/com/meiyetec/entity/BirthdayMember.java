package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("birthday_member")
public class BirthdayMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private LocalDate birthday;
    private String birthdayType;
    private Integer lunarMonth;
    private Integer lunarDay;
    private String tags;
    private Integer autoSend;
    private String autoCoupon;
    private java.math.BigDecimal autoCouponAmount;
    private Integer vipLevel;
    private Integer lastSentYear;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}