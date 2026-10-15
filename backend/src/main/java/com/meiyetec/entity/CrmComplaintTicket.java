package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_complaint_ticket")
public class CrmComplaintTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private String ticketNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    /** SERVICE/STAFF/PRODUCT/PRICE/ENVIRONMENT/OTHER */
    private String complaintType;
    /** 1一般 2较严重 3严重 4紧急 */
    private Integer level;
    private String subject;
    private String content;
    private String evidence;
    /** ORDER/CARD/MEMBER/FOLLOW_UP/REVIEW */
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long handlerId;
    private String handlerName;
    private Long deptId;
    private LocalDateTime slaExpireAt;
    private Integer slaMinutes;
    /** 1待分配 2已分配 3处理中 4等待客户 5已解决 6已关闭 7已升级 */
    private Integer status;
    private String solution;
    /** REFUND/EXCHANGE/APOLOGY/COMPENSATION/NONE */
    private String solutionType;
    private BigDecimal refundAmount;
    private LocalDateTime resolvedAt;
    private Integer satisfactionRating;
    private String satisfactionNote;
    private Long parentId;
    private Integer escalateCount;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
