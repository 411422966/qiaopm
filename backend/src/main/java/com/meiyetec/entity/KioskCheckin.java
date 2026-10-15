package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kiosk_checkin")
public class KioskCheckin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private Long storeId;
    private Long bookingId;
    private Long memberId;
    private String checkinMethod;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime checkinTime;
    private LocalDateTime arrivedAt;
    private LocalDateTime serviceStartedAt;
    private LocalDateTime serviceFinishedAt;
    private String status;
    private Long staffId;
    private String notes;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}