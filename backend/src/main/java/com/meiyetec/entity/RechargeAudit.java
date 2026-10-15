package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M05 储值充值审核申请单
 * 状态：1待审核 2审核通过 3审核拒绝 4已取消
 */
@Data
@TableName("crm_recharge_audit")
public class RechargeAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 审核单号 */
    private String auditNo;
    /** 关联会员 id */
    private Long memberId;
    private String memberName;
    private String memberPhone;
    /** 充值金额 */
    private BigDecimal amount;
    /** 赠送金额 */
    private BigDecimal giftAmount;
    /** 实付金额（含赠送） */
    private BigDecimal totalAmount;
    /** 支付方式 */
    private String payMethod;
    /** 第三方支付流水号（微信/支付宝） */
    private String tradeNo;
    /** 状态：1待审核 2审核通过 3审核拒绝 4已取消 */
    private Integer status;
    /** 申请人 id */
    private Long applyUserId;
    private String applyUserName;
    /** 审核人 id */
    private Long auditUserId;
    private String auditUserName;
    private LocalDateTime auditTime;
    /** 审核意见 */
    private String auditRemark;
    /** 来源：1后台代充 2会员自助 3批量导入 */
    private Integer source;
    private String remark;
    /** 乐观锁版本 */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
