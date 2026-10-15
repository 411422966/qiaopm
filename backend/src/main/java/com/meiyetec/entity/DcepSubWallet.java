package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DCEP-05 数字人民币子钱包（独立账本）
 *
 * <p>子钱包是主钱包下的独立账户单元：
 * <ul>
 *   <li>每个子钱包有独立的 balance / total_in / total_out / frozen_balance</li>
 *   <li>所有变动通过账本流水 {@link DcepSubWalletLedger} 记录，不可篡改</li>
 *   <li>支持资金归集（auto_sweep）、分账结算（settlement_mode）</li>
 * </ul>
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>总部统一管理各门店子钱包（多门店资金归集）</li>
 *   <li>按服务类型建立独立账本（美甲/美容/产品）</li>
 *   <li>品牌方分账账户（settlement 分账结算）</li>
 * </ul>
 */
@Data
@TableName("biz_dcep_sub_wallet")
public class DcepSubWallet {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 所属门店（null=总部统一账户） */
    private Long storeId;
    /** 所属主钱包ID */
    private Long parentWalletId;
    /** 主钱包类型: hardware / soft */
    private String parentWalletType;
    /** 子钱包编号（全局唯一） */
    private String subWalletCode;
    /** 子钱包名称 */
    private String subWalletName;
    /**
     * 子钱包类型：
     * store_fund  — 门店资金账户
     * product_fund — 产品购买账户
     * project_fund — 项目服务账户
     * brand_share  — 品牌分账账户
     * settlement   — 结算账户
     */
    private String walletType;
    private String currency = "CNY";
    /** 独立账户余额 */
    private BigDecimal balance;
    /** 累计转入 */
    private BigDecimal totalIn;
    /** 累计转出 */
    private BigDecimal totalOut;
    /** 冻结金额 */
    private BigDecimal frozenBalance;
    /** 可用余额（balance - frozenBalance，由 DB 计算列支撑） */
    private BigDecimal availableBalance;
    /** 单钱包余额上限（null=无上限） */
    private BigDecimal maxBalance;
    /** 保底余额 */
    private BigDecimal minBalance;
    /** 转入手续费率（null=免手续费） */
    private BigDecimal transferInRatio;
    /** 转出手续费率 */
    private BigDecimal transferOutRatio;
    /** 自动归集: 0-否 1-是 */
    private Integer autoSweepEnabled;
    /** 自动归集触发阈值 */
    private BigDecimal sweepThreshold;
    /** 自动归集目标子钱包ID */
    private Long sweepTargetId;
    /** 结算模式: daily / weekly / monthly */
    private String settlementMode;
    /** 状态: active / frozen / closed */
    private String status;
    /** 优先级 */
    private Integer priority;
    /** 标签JSON */
    private String tags;
    private String remark;
    private LocalDateTime activatedTime;
    private LocalDateTime closedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
