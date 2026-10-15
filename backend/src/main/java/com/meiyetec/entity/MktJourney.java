package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_journey")
public class MktJourney {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String journeyCode;
    private String journeyName;
    /** MARKETING/CARE/RETENTION/ONBOARD */
    private String journeyType;
    private String description;
    private String nodesJson;
    private String edgesJson;
    private String triggerEvent;
    private String triggerFilter;
    /** 1草稿 2已发布 3已暂停 4已结束 */
    private Integer status;
    private LocalDateTime publishedAt;
    private Integer enterCount;
    private Integer completeCount;
    private Integer exitCount;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
