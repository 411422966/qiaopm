package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_segment")
public class MktSegment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String segmentCode;
    private String segmentName;
    private String description;
    private String filterJson;
    private Integer memberCount;
    /** RFM/RULE/AI/MANUAL */
    private String sourceType;
    private Long sourceModelId;
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