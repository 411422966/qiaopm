package com.meiyetec.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * M04-07：套餐差价补款/升级 DTO
 *
 * <p>业务场景：会员将旧套餐折算剩余价值，升级为新套餐，多退少补。
 *
 * <p>计算公式：
 * <pre>
 * 旧套餐剩余价值 = 旧套餐原价 × (剩余次数 / 总次数)
 * 差价 = 新套餐价格 - 旧套餐剩余价值
 * 实际补缴 = max(0, 差价)
 * 退款     = max(0, -差价)  （降级场景，退至钱包）
 * </pre>
 */
@Data
public class PackageUpgradeDTO {

    /** 原套餐订单ID */
    @NotNull(message = "原套餐订单ID不能为空")
    private Long fromOrderId;

    /** 目标套餐ID */
    @NotNull(message = "目标套餐ID不能为空")
    private Long toPackageId;

    /** 补缴方式：wechat/alipay/wallet/cash 等，默认 wallet */
    private String payMethod;

    /** 第三方交易流水号（微信/支付宝） */
    private String transactionId;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 前端估算的差价（服务端独立核算，不依赖前端值） */
    private BigDecimal estimatedDiff;

    /** ========== 降级退款时使用 ========== */
    /** 退款至钱包（默认 true），false = 不退款仅换套餐 */
    private Boolean refundToWallet;
}
