package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shared_package_invite")
public class SharedPackageInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long sharedPackageId;
    private String inviteCode;
    private Long inviterId;
    private String inviteePhone;
    private String inviteeName;
    private LocalDateTime expireAt;
    private String status;
    private LocalDateTime acceptedAt;
    private Long acceptedMemberId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}