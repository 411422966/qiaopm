package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * M06 技师排班实体
 */
@Data
@TableName("biz_employee_schedule")
public class EmployeeSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    /** 1早班 2中班 3晚班 4全天 */
    private Integer shiftType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxBookings;
    /** 1正常 2请假 3休息 */
    private Integer status;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}