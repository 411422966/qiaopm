package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("booking_resource_link")
public class BookingResourceLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long resourceId;
    private Long serviceItemId;
    private Integer isRequired;
    private Integer priority;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
