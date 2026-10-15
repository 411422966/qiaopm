package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mh_visual_archive")
public class MhVisualArchive {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private Long memberId;
    private String archiveCode;
    /** FACE/SKIN/BODY/HAIR/SCAR/OTHER */
    private String archiveType;
    private String bodyPart;
    private Long projectId;
    private String projectName;
    private String imageUrl;
    private String thumbnailUrl;
    private String imageHash;
    private Integer faceMasked;
    /** BLUR/EYE/MOSAIC/REPLACE */
    private String faceMaskType;
    private Integer aiAnalyzed;
    private String aiFeaturesJson;
    private String aiTags;
    private BigDecimal aiScore;
    private Long relatedOrderId;
    private String relatedOrderNo;
    private Long photographerId;
    private String photographerName;
    private String device;
    private String envInfo;
    private String remark;
    private Integer revoked;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}