package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("booking_waitlist")
public class BookingWaitlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String waitlistNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long serviceItemId;
    private String serviceItemName;
    private Long employeeId;
    private String employeeName;
    private LocalDate preferredDate;
    private LocalTime preferredTimeFrom;
    private LocalTime preferredTimeTo;
    private Integer priority;
    /** 1等待 2已通知 3已转预约 4已取消 5已过期 */
    private Integer status;
    private LocalDateTime notifyAt;
    private Integer notifyCount;
    private LocalDateTime expiredAt;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
