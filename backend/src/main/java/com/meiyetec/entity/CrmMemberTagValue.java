package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_member_tag_value")
public class CrmMemberTagValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private String tagCode;
    private String tagName;
    /** RFM/LIFECYCLE/VALUE/PREFERENCE/AI */
    private String tagCategory;
    /** AUTO/MANUAL/AI */
    private String sourceType;
    private Long modelId;
    private String tagValue;
    private BigDecimal score;
    private String relatedType;
    private Long relatedId;
    private LocalDateTime effectiveAt;
    private LocalDateTime expireAt;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}