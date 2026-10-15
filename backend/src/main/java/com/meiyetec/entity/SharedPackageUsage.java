package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shared_package_usage")
public class SharedPackageUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long sharedPackageId;
    private Long memberId;
    private Long serviceId;
    private Long bookingId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime usageDate;
    private Integer sessionsUsed;
    private Long employeeId;
    private Long storeId;
    private String remark;
}