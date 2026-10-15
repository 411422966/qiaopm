package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * R3.0-F P1-5：AI 智能排班建议
 */
@Data
@TableName("schedule_suggestion")
public class ScheduleSuggestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private LocalDate workDate;
    private Long employeeId;
    private String employeeName;
    /** DAY/MIDDLE/NIGHT */
    private String shiftCode;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 预测当班客流 0-100 */
    private Integer expectedLoad;
    private BigDecimal confidence;
    private String reason;
    /** PENDING/ACCEPTED/EXPIRED */
    private String status;
    private LocalDateTime acceptedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}