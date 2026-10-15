package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("cross_platform_capability")
public class CrossPlatformCapability {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    /** camera / gps / biometric / push / nfc / ble */
    private String capabilityKey;
    private String capabilityName;
    private Integer supported;
    private String version;
    /** JSON */
    private String extraConfig;
}