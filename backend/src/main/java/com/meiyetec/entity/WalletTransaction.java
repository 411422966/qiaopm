package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_wallet_transaction")
public class WalletTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long memberId;

    /** 类型：1充值 2消费 3退款 4赠送 5冻结 6解冻 */
    private Integer type;

    /** 变动金额（正数） */
    private BigDecimal amount;

    /** 变动前余额 */
    private BigDecimal balanceBefore;

    /** 变动后余额 */
    private BigDecimal balanceAfter;

    /** 赠送金额（充值活动） */
    private BigDecimal giftAmount;

    /** 支付方式 */
    private String payMethod;

    /** 关联订单（可选） */
    private Long orderId;

    /** 操作人 */
    private Long operatorId;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    /** 类型描述（不存库） */
    @TableField(exist = false)
    private String typeLabel;

    /** 会员姓名（不存库） */
    @TableField(exist = false)
    private String memberName;
}
