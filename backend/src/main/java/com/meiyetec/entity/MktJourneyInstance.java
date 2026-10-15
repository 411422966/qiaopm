package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_journey_instance")
public class MktJourneyInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long journeyId;
    private Long memberId;
    private String memberName;
    private String currentNodeId;
    /** 1进行中 2已完成 3已退出 */
    private Integer status;
    private LocalDateTime enteredAt;
    private LocalDateTime completedAt;
    private String contextJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
