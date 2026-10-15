package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M02-04 多账户分账规则
 * <p>定义一笔订单收入按比例分配到服务商 / 分店 / 员工三方子钱包。
 *
 * @see DcepSubWallet 接收方账户（独立账本）
 */
@Data
@TableName("m02_split_rule")
public class MultiAccountSplitRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 规则名称 */
    private String ruleName;
    /** 规则范围：ALL=全单 / SERVICE=按服务 */
    private String ruleScope;
    /** serviceId（rule_scope=SERVICE 时必填） */
    private Long serviceId;
    /** 服务商子钱包ID */
    private Long providerWalletId;
    /** 服务商比例 (%) */
    private BigDecimal providerRatio;
    /** 分店子钱包ID */
    private Long branchWalletId;
    /** 分店比例 (%) */
    private BigDecimal branchRatio;
    /** 员工子钱包ID（可空，员工维度分账） */
    private Long employeeWalletId;
    /** 员工比例 (%) */
    private BigDecimal employeeRatio;
    /** 平台抽佣 (%) */
    private BigDecimal platformFeeRatio;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private Integer priority;
    /** 1启用 0停用 */
    private Integer status;
    private String remark;
    private Long createBy;
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
