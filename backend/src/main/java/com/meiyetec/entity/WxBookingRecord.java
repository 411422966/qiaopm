package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * R3.0-G P3-1：微信小程序预约记录
 */
@Data
@TableName("wx_booking_record")
public class WxBookingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String appid;
    private String openid;
    private Long memberId;
    private Long storeId;
    private Long serviceId;
    private String serviceName;
    private Long employeeId;
    private String employeeName;
    private LocalDate bookingDate;
    private LocalDateTime bookingTime;
    private Integer durationMinutes;
    private String contactPhone;
    /** PENDING/CONFIRMED/COMPLETED/CANCELLED */
    private String status;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String source;
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}