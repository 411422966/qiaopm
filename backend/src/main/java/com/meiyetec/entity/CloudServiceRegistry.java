package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_service_registry")
public class CloudServiceRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceName;
    private String serviceCode;
    private String instanceId;
    private String host;
    private Integer port;
    private String version;
    private String region;
    private String zone;
    private String metadata;
    /** UP / DOWN / STARTING / OUT_OF_SERVICE */
    private String status;
    private Integer weight;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime registerAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}