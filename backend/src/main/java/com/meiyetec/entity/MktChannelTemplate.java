package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_channel_template")
public class MktChannelTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String channelType;
    private Long channelConfigId;
    private String templateCode;
    private String templateName;
    private String externalTemplateId;
    private String scene;
    private String subject;
    private String content;
    private String variables;
    private String linkUrl;
    /** MINI/MINI_PAGE/H5/IMAGE_NOPAGE */
    private String linkType;
    /** 1待审核 2已审核 3驳回 */
    private Integer auditStatus;
    private String auditRemark;
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
