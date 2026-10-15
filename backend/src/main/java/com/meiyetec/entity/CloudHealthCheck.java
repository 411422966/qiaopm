package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_health_check")
public class CloudHealthCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instanceId;
    /** HTTP / TCP / DB / REDIS */
    private String checkType;
    private String target;
    private String status;
    private Integer responseMs;
    private Integer httpCode;
    private String detail;
    private LocalDateTime checkedAt;
}