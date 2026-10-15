package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_churn_alert")
public class CrmChurnAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    /** HIGH / MEDIUM / LOW / SAFE */
    private String riskLevel;
    private BigDecimal churnScore;
    private LocalDate predictedLostDate;
    private String alertReason;
    private String suggestedAction;
    /** 1=待处理 / 2=已联系 / 3=已挽回 / 4=已流失 / 5=忽略 */
    private Integer status;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handledAt;
    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}