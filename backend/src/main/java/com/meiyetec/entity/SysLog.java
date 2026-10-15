package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String description;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String ip;
    private String userAgent;
    private Integer costMs;
    private Integer status;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
