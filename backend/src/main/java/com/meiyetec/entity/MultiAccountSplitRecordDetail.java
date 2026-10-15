package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M02-04 分账明细（一笔订单分给多方的每行记录）
 */
@Data
@TableName("m02_split_record_detail")
public class MultiAccountSplitRecordDetail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long splitRecordId;
    /** PROVIDER / BRANCH / EMPLOYEE / PLATFORM */
    private String receiverRole;
    /** 子钱包类型（store_fund / product_fund / project_fund / brand_share / settlement） */
    private String receiverType;
    private Long walletId;
    private String walletCode;
    private String walletName;
    private Long refId;
    private String refName;
    /** 比例 (%) */
    private BigDecimal ratio;
    /** 应分金额 */
    private BigDecimal amount;
    /** 实际入账金额（扣手续费后） */
    private BigDecimal actualAmount;
    /** 1 SUCCESS / 2 FAILED */
    private Integer status;
    private String errorMsg;
    /** 子钱包账本流水ID */
    private Long ledgerId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
