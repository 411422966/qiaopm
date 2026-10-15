package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_member_store")
public class CrmMemberStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long storeId;
    private Long brandId;
    /** ORIGIN归属门店 / CROSS跨店关联 */
    private String relationType;
    /** PHONE手机号 / CARD会员卡 / MANUAL人工 */
    private String bindType;
    private LocalDateTime firstActiveAt;
    private LocalDateTime lastActiveAt;
    private Integer visitCount;
    /** 1活跃 0停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
