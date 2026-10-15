package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_expiry_alert_rule")
public class BizExpiryAlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private String ruleName;
    private String category;
    private Long productId;
    private Integer criticalDays;
    private Integer warningDays;
    private String recipients;
    private String notifyChannels;
    private Integer autoHandle;
    private Integer status;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
