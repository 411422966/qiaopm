package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_review_template")
public class CrmReviewTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String templateCode;
    private String templateName;
    private String triggerEvent;
    private String dimensions;
    private String tagsPool;
    private String introText;
    private String shareImage;
    private String touchChannel;
    private Integer touchDelayMinutes;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
