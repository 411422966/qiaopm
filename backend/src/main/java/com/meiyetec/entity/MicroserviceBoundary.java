package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("microservice_boundary")
public class MicroserviceBoundary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceCode;
    private String serviceName;
    /** 对应限界上下文 */
    private String contextCode;
    private Integer port;
    private String basePath;
    private String apiVersion;
    private String responsibility;
    private String dependencies;
    /** JAR / DOCKER / K8S */
    private String deployUnit;
    /** PLANNED / RUNNING / DEPRECATED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}