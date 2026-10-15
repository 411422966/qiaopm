package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_follow_up_task")
public class CrmFollowUpTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private String taskNo;
    /** POST_TREATMENT / BIRTHDAY / EXPIRE / COMPLAINT / CUSTOM */
    private String taskType;
    private Long planId;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    /** ORDER / CARD / MEMBER / COMPLAINT */
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private LocalDateTime plannedAt;
    private LocalDateTime slaExpireAt;
    private Integer slaMinutes;
    /** 1低 2中 3高 */
    private Integer priority;
    private String subject;
    private String content;
    private String suggestedScript;
    private Long ownerId;
    private String ownerName;
    private Long deptId;
    /** 1待处理 2处理中 3已完成 4已关闭 5已逾期 */
    private Integer status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** SATISFIED / NORMAL / UNSATISFIED / COMPLAINT */
    private String resultType;
    private String resultNote;
    private Integer rating;
    private Integer touchCount;
    private LocalDateTime lastTouchAt;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
