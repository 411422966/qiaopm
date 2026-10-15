package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P2-16 BNPL 先买后付
 * 支持 Atome / Klarna / Afterpay 等 BNPL 平台
 */
@Data
@TableName("biz_bnpl_order")
public class BnplOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 关联业务订单号 */
    private String bizOrderNo;
    private Long memberId;
    private String memberName;
    private String memberEmail;
    private String memberPhone;
    /** BNPL 平台编码：atome/klarna/afterpay/affirm */
    private String provider;
    /** BNPL 平台返回的订单号 */
    private String providerOrderNo;
    /** 订单总额 */
    private BigDecimal amount;
    /** 币种 */
    private String currency;
    /** 分期期数 */
    private Integer installments;
    /** 每期金额 */
    private BigDecimal installmentAmount;
    /** 首付金额 */
    private BigDecimal downPayment;
    /** 平台手续费 */
    private BigDecimal platformFee;
    /** 状态：1待确认 2已确认(待发货) 3已发货 4已收货 5已完成 6已取消 7退款中 8已退款 */
    private Integer status;
    /** 分期还款计划（JSON） */
    private String repaymentSchedule;
    /** 首次还款日 */
    private LocalDate firstRepayDate;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
