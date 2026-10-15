package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("hipaa_phi_access")
public class HipaaPhiAccess {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String fieldName;
    private String accessType;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String purpose;
    private String ipAddress;
    private Integer isBreakGlass;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}