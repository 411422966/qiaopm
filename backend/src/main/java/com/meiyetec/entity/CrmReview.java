package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_review")
public class CrmReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    /** ORDER / STAFF / SERVICE / STORE */
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Integer overallRating;
    private Integer serviceRating;
    private Integer skillRating;
    private Integer environmentRating;
    private Integer priceRating;
    private String tags;
    private String content;
    private String images;
    private Integer anonymous;
    /** INTERNAL / MINI_PROGRAM / SMS / OTHER */
    private String channel;
    private String replyContent;
    private LocalDateTime replyAt;
    private String replyBy;
    /** 1待审核 2已发布 3已隐藏 4已申诉 */
    private Integer status;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
