package com.meiyetec.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 通用钱包变动 DTO（充值 / 消费 / 退款 共用）
 * type: 1充值 2消费 3退款
 * amount: 主金额
 * giftAmount: 赠送金额（仅充值生效）
 */
@Data
public class WalletOpDTO {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    /** 赠送金额（可选，仅充值活动使用） */
    private BigDecimal giftAmount;

    /** 支付方式：微信/支付宝/银行卡/现金/赠送 等 */
    private String payMethod;

    /** 关联订单ID（可选） */
    private Long orderId;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
