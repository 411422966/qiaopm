package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("member_day_config")
public class MemberDayConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String configName;
    private String dayType;
    private String triggerRule;
    private BigDecimal discountRate;
    private BigDecimal giftAmount;
    private Integer giftPoints;
    private Long giftCouponId;
    private String freeServiceIds;
    private BigDecimal couponAmount;
    private String targetLevel;
    private String targetStoreIds;
    private Integer birthdayOffsetDays;
    private Integer fixedMonth;
    private Integer fixedDay;
    private Integer repeatAnnually;
    private String bannerImage;
    private String smsTemplate;
    private String pushTitle;
    private String pushContent;
    private Integer priority;
    private String status;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}