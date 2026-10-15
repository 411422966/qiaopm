package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dcep_config")
public class DcepConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private String merchantName;
    private String appId;
    private String apiKey;
    private String privateKey;
    private String publicKey;
    private String notifyUrl;
    private String environment;
    private Integer enabled;
    private String description;

    /**
     * 二次确认密码（不入库，仅供控制器校验）。transient + JSON 注解避免被序列化到响应。
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private transient String confirmPassword;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
