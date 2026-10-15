package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_idempotency_record")
public class SysIdempotencyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String idempotencyKey;
    private String bizType;
    private Long bizId;
    private String requestHash;
    /** PROCESSING / SUCCESS / FAILED */
    private String status;
    private Integer responseCode;
    private String responseMessage;
    private String responseData;
    private Integer retryCount;
    private LocalDateTime expiredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
