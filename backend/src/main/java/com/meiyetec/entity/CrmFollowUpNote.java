package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-H P0.2：回访任务操作日志
 */
@Data
@TableName("crm_follow_up_note")
public class CrmFollowUpNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    /** CREATE/ASSIGN/START/FINISH/CLOSE/REOPEN/EDIT */
    private String actionType;
    private Integer fromStatus;
    private Integer toStatus;
    private String content;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}