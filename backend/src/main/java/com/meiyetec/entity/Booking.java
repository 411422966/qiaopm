package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M06 预约实体
 */
@Data
@TableName("biz_booking")
public class Booking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** M01-04：所属门店ID（跨店预约强制校验：员工+房间必须同属一个门店） */
    private Long storeId;
    private String bookingNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long employeeId;
    private String employeeName;
    private String serviceItem;
    private Long roomId;
    private String roomName;
    private LocalDate bookingDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1待确认 2已确认 3已签到 4已完成 5已取消 6爽约 */
    private Integer status;
    /** 1会员自助 2前台代约 3电话 */
    private Integer source;
    private String remark;
    private Long operatorId;
    private LocalDateTime checkinTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private String cancelReason;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 所属门店名称（查询填充，不存库） */
    @TableField(exist = false)
    private String storeName;
}