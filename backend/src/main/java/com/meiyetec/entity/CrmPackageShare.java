package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-E P2：套餐共享成员（一人买卡，多位会员共用）
 */
@Data
@TableName("crm_package_share")
public class CrmPackageShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long packageOrderId;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Integer useTimes;
    private Integer maxTimes;
    private Long invitedBy;
    private LocalDateTime inviteAt;
    private LocalDateTime firstUsedAt;
    /** 1待使用 2已激活 3已用完 4已撤销 */
    private Integer status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}