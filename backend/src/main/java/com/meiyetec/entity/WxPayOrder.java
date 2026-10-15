package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wx_pay_order")
public class WxPayOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String appid;
    private String outTradeNo;
    private String transactionId;
    private String openid;
    private Long memberId;
    private Long storeId;
    /** 金额（分） */
    private Integer totalFee;
    private String body;
    /** PENDING/SUCCESS/REFUND/CLOSED/FAIL */
    private String status;
    private LocalDateTime paidAt;
    private String notifyPayload;
    private Integer notifyCount;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}