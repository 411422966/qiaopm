package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("booking_booking_resource")
public class BookingBookingResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookingId;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private String resourceType;
    private Integer quantity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
