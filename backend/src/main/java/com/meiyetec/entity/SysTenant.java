package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private String address;
    private String logoUrl;
    private Integer status;
    private String expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
