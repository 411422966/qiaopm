package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_service_improvement_log")
public class CrmServiceImprovementLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long improvementId;
    /** CREATE / ASSIGN / TRANSIT / COMMENT / COMPLETE / VERIFY */
    private String actionType;
    private Integer fromStatus;
    private Integer toStatus;
    private String content;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}