package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("hipaa_encryption_key")
public class HipaaEncryptionKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String keyAlias;
    private Integer keyVersion;
    private String algorithm;
    private String keyStatus;
    private String publicKey;
    private String kmsArn;
    private LocalDateTime rotatedAt;
    private LocalDateTime nextRotateAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}