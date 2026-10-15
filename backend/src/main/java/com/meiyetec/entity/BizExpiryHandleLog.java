package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_expiry_handle_log")
public class BizExpiryHandleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private Long alertLogId;
    private Long inventoryId;
    private Long productId;
    private String productName;
    private String batchNo;
    private String handleMethod;
    private Integer handleQuantity;
    private BigDecimal unitPrice;
    private BigDecimal lossAmount;
    private LocalDate handleDate;
    private String handleReason;
    private Long operatorId;
    private String operatorName;
    private String evidence;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
