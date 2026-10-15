package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_encryption_key")
public class DataEncryptionKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String keyAlias;
    private String algorithm;
    private Integer keyLength;
    /** 前 4 + 后 4 中间省略 */
    private String keyPreview;
    private String ivPreview;
    /** ACTIVE / ROTATED / REVOKED */
    private String status;
    private LocalDateTime rotatedAt;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}