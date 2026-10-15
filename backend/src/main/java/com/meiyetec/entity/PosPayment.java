package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M07 支付记录实体
 */
@Data
@TableName("pos_payment")
public class PosPayment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orderId;
    private String paymentNo;
    private String method;
    private BigDecimal amount;
    /** 状态:0待支付 1成功 2失败 3已退款 */
    private Integer status;
    /** 实收金额（现金场景：客人给了多少钱） */
    private BigDecimal tenderedAmount;
    /** 找零金额（tenderedAmount > amount 时产生） */
    private BigDecimal changeAmount;
    private String transactionId;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;
    private String errorMsg;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
