package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_dcep_order")
public class PayDcepOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private Long bizOrderId;
    private String bizOrderNo;
    private String dcepOrderNo;
    private String prepayId;
    private String transactionId;
    private Long amountCents;
    private Long channelId;
    private String channelCode;
    /** PERSONAL/MERCHANT */
    private String walletType;
    private String payerPhone;
    /** 1待支付 2已支付 3已关闭 4已退款 5失败 */
    private Integer status;
    private LocalDateTime prepayAt;
    private LocalDateTime paidAt;
    private LocalDateTime closeAt;
    private LocalDateTime refundAt;
    private LocalDateTime expireAt;
    private String errorCode;
    private String errorMsg;
    private String notifyRaw;
    private Integer notifyCount;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}