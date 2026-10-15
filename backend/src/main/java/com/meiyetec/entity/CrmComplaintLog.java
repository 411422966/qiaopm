package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_complaint_log")
public class CrmComplaintLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long ticketId;
    /** CREATE/ASSIGN/REPLY/RESOLVE/CLOSE/REOPEN/ESCALATE/REFUND */
    private String actionType;
    private Integer fromStatus;
    private Integer toStatus;
    private String content;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
