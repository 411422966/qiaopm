package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pos_device")
public class PosDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String vendor;
    private String deviceCode;
    private String deviceName;
    private String serialNumber;
    private String apiKey;
    private String locationId;
    private String webhookUrl;
    private LocalDateTime lastHeartbeat;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}