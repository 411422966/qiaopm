package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@TableName("booking_resource")
public class BookingResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String resourceCode;
    private String resourceName;
    /** ROOM/BED/DEVICE */
    private String resourceType;
    private Integer capacity;
    private String location;
    private String equipment;
    private Integer defaultDuration;
    private LocalTime availableFrom;
    private LocalTime availableTo;
    private Integer status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
