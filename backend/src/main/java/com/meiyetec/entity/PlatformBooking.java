package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("platform_booking")
public class PlatformBooking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** MEITUAN/DOUYIN */
    private String platform;
    private String platformBookingId;
    private Long storeId;
    private Long memberId;
    private String customerName;
    private String customerPhone;
    private String serviceName;
    private LocalDate bookingDate;
    private LocalDateTime bookingTime;
    /** PENDING/CONFIRMED/COMPLETED/CANCELLED */
    private String status;
    private BigDecimal amount;
    private String rawPayload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}