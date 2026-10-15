package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_image_withdraw")
public class MhImageWithdraw {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    /** ALL/AI_TRAINING/STAFF_VIEW */
    private String withdrawScope;
    /** ARCHIVE/COMPARE/CONSENT */
    private String targetType;
    private String targetIds;
    private String reason;
    /** 1待处理 2已处理 3已驳回 */
    private Integer status;
    private Long handlerId;
    private String handlerName;
    private String handleRemark;
    private LocalDateTime handledAt;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}