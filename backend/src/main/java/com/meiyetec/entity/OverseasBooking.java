package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("overseas_booking")
public class OverseasBooking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String bookingNo;
    private String source;
    private String externalId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerLocale;
    private String miniProgramOpenId;
    private Long serviceId;
    private String serviceName;
    private Long staffId;
    private Long storeId;
    private LocalDate bookingDate;
    private String bookingTime;
    private String notes;
    private String status;
    private String ipAddress;
    private String userAgent;
    private String rawPayload;
    private Long convertedMemberId;
    private Long convertedOrderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}