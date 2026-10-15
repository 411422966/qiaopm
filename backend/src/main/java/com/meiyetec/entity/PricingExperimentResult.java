package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M14 定价实验结果记录
 *
 * <p>每次 quote() 调用时，对命中实验的会员记录分组分配和价格结果。
 * 用于事后统计分析（置信度、提升幅度、结论）。
 *
 * <p>状态：
 * - RECORDED — 记录已落库
 * - CONVERTED — 后续产生了转化（从 POS order webhook 回调）
 */
@Data
@TableName("pricing_experiment_result")
public class PricingExperimentResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 实验 ID */
    private Long expId;
    /** 实验 key */
    private String expKey;
    /** 会员 ID */
    private Long memberId;
    /** 门店 ID */
    private Long storeId;
    /** 项目 ID */
    private Long projectId;
    /** 定价 quote 时间 */
    private LocalDateTime quoteAt;
    /** 被分配的组：A / B / C / D */
    private String variant;
    /** 是否控制组 */
    private Boolean isControl;
    /** 原始价格 */
    private BigDecimal originalPrice;
    /** 最终价格 */
    private BigDecimal finalPrice;
    /** 折扣金额 */
    private BigDecimal discountAmount;
    /** 实验规则 ID */
    private Long ruleId;
    /** 实验规则编码 */
    private String ruleCode;

    /** 转化状态：RECORDED / CONVERTED / EXPIRED */
    private String conversionStatus;
    /** 转化时间 */
    private LocalDateTime convertedAt;
    /** 关联订单 ID（转化时写入） */
    private Long orderId;
    /** 转化金额（用于 revenue 分析） */
    private BigDecimal conversionAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
