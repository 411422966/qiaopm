package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("service_contract")
public class ServiceContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceCode;
    private String methodName;
    private String httpMethod;
    private String path;
    /** JSON Schema */
    private String requestSchema;
    /** JSON Schema */
    private String responseSchema;
    private Integer authRequired;
    private Integer rateLimit;
    private Integer timeoutMs;
    private String version;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}