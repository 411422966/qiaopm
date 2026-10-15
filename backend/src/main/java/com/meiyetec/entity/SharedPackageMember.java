package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shared_package_member")
public class SharedPackageMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sharedPackageId;
    private Long memberId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinDate;
    private Integer usedSessions;
    private String relationship;
    private String joinMethod;
    private String status;
}