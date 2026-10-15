package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_expiry_alert_log")
public class BizExpiryAlertLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private Long inventoryId;
    private Long productId;
    private String productName;
    private String batchNo;
    private Integer quantity;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Integer daysToExpiry;
    private String alertLevel;
    private String alertType;
    private String alertMessage;
    private String status;
    private String handleMethod;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handleRemark;
    private Integer notified;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
