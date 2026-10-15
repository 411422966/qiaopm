package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DCEP-05 子钱包账本流水（不可篡改的复式记账）
 *
 * <p>每个子钱包的每笔资金变动均记录此表，保证独立账本的可审计性。
 * 账本编号 ledger_no 格式：SWL-{yyyyMMdd}-{8位序号}
 */
@Data
@TableName("biz_dcep_sub_wallet_ledger")
public class DcepSubWalletLedger {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 子钱包ID */
    private Long subWalletId;
    /** 账本编号（流水号） */
    private String ledgerNo;
    /**
     * 交易类型：
     * transfer_in  — 转入
     * transfer_out — 转出
     * consume      — 消费扣款
     * refund       — 退款
     * freeze      — 冻结
     * unfreeze     — 解冻
     * settle       — 结算/分账
     * adjust       — 手工调整
     */
    private String txType;
    /** 交易金额（正数） */
    private BigDecimal amount;
    /** 变动前余额 */
    private BigDecimal balanceBefore;
    /** 变动后余额 */
    private BigDecimal balanceAfter;
    /** 手续费 */
    private BigDecimal fee;
    /** 对方子钱包ID（转账时） */
    private Long oppositeWalletId;
    /** 对方钱包名称 */
    private String oppositeWalletName;
    /** 关联订单号 */
    private String orderNo;
    /** 商户号 */
    private String merchantId;
    /** 商户名称 */
    private String merchantName;
    /** 操作人ID */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
