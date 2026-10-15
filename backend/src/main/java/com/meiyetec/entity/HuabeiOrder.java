package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P2-15 花呗/芝麻信用分期
 * 支持花呗分期（3/6/12期）和芝麻信用预授权
 */
@Data
@TableName("biz_huabei_order")
public class HuabeiOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 关联业务订单号 */
    private String bizOrderNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    /** 芝麻信用分（查询时填入） */
    private Integer zmScore;
    /** 授信额度 */
    private BigDecimal creditLimit;
    /** 已用额度 */
    private BigDecimal usedLimit;
    /** 分期期数：3/6/12 */
    private Integer installmentPeriod;
    /** 分期利率 */
    private BigDecimal installmentRate;
    /** 每期还款额 */
    private BigDecimal monthlyPayment;
    /** 订单总额 */
    private BigDecimal amount;
    /** 手续费总额 */
    private BigDecimal fee;
    /** 实际到账 */
    private BigDecimal netAmount;
    /** 状态：1待授权 2授权成功 3已支付 4已取消 5逾期 6已还清 */
    private Integer status;
    /** 首期还款日 */
    private LocalDate firstRepayDate;
    /** 签约ID（芝麻返回） */
    private String agreementId;
    /** 预授权号（冻结额度用） */
    private String authNo;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
