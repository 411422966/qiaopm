package com.meiyetec.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * R2.0-C 创建退款单请求体。
 *
 * <ul>
 *   <li>FULL：refundAmount 可为空（默认 = paidAmount）</li>
 *   <li>PART：refundAmount 必填且 < paidAmount</li>
 *   <li>CROSS：通过 {@link #crossOrderIds} 传入多订单</li>
 * </ul>
 */
@Data
public class PosRefundCreateDTO {

    @NotNull(message = "原订单ID不能为空")
    private Long orderId;

    /** 退款金额；空 = 全额 */
    @DecimalMin(value = "0.01", message = "退款金额必须 > 0")
    private BigDecimal refundAmount;

    /** 退款策略：ORIGINAL / WALLET / MIXED */
    private String strategy = "ORIGINAL";

    /** 退款原因 */
    private String reason;

    /** 操作人ID（不传从上下文取） */
    private Long operatorId;

    /** 跨单退款订单ID（仅 CROSS 时使用） */
    private List<Long> crossOrderIds;

    /** 策略 MIXED 时，储值返还金额 */
    private BigDecimal walletAmount;

    /** 策略 MIXED 时，原路退款金额；不传则 refundAmount - walletAmount */
    private BigDecimal originalAmount;

    /**
     * 是否需要退款侧触发的逆向补偿（套餐/积分/库存/提成）
     * 默认 true；财务报表 / 冲正单可置 false。
     */
    private Boolean reverseAsset = Boolean.TRUE;
}
