package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M14 动态定价 A/B 实验配置
 *
 * <p>定义一个完整的定价 A/B 测试实验：
 * <ul>
 *   <li>实验 key（全局唯一）</li>
 *   <li>控制组 vs 变异组（每人独立分配）</li>
 *   <li>控制组：A 组 = 原价 / B 组 = 原价 - X%</li>
 *   <li>变异组：C 组 = 原价 - Y% / D 组 = 原价 - Z%</li>
 *   <li>样本分配比例（traffic allocation）</li>
 *   <li>目标指标（conversion_rate / revenue_per_session / margin）</li>
 *   <li>最少样本量（statistical significance 计算）</li>
 * </ul>
 *
 * <p>状态机：DRAFT → RUNNING → PAUSED → COMPLETED
 */
@Data
@TableName("pricing_experiment")
public class PricingExperiment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 实验 key（URL-safe，英文） */
    private String expKey;
    /** 实验名称 */
    private String expName;
    /** 状态：DRAFT/RUNNING/PAUSED/COMPLETED */
    private String status;

    // ---- 控制组（基准）----
    /** 控制组 A 名称 */
    private String groupAName;
    /** 控制组 A 折扣模式：ORIGINAL（原价）/ PERCENT / FIXED */
    private String groupAMode;
    /** 控制组 A 折扣值（PERCENT=90 表示打9折，FIXED=50 表示减50元） */
    private BigDecimal groupAValue;

    /** 控制组 B 名称 */
    private String groupBName;
    private String groupBMode;
    private BigDecimal groupBValue;

    // ---- 变异组（测试）----
    /** 变异组 C 名称 */
    private String groupCName;
    private String groupCMode;
    private BigDecimal groupCValue;

    /** 变异组 D 名称 */
    private String groupDName;
    private String groupDMode;
    private BigDecimal groupDValue;

    /** 实验组数量（2/3/4） */
    private Integer groupCount;

    /** 流量分配：控制组总占比（0.0~1.0），剩余给变异组 */
    private BigDecimal controlTrafficRatio;
    /** 控制组内 A:B 分配比例（默认 1:1） */
    private BigDecimal controlABRatio;
    /** 变异组内 C:D 分配比例（默认 1:1） */
    private BigDecimal variantCDRatio;

    // ---- 适用条件----
    /** 适用项目（逗号分隔 project_id，或 ALL） */
    private String applicableProjects;
    /** 适用门店（逗号分隔 store_id，或 ALL） */
    private String applicableStores;
    /** 适用会员等级（逗号分隔 level_id，或 ALL） */
    private String applicableMemberLevels;
    /** 最低订单金额 */
    private BigDecimal minOrderAmount;

    // ---- 指标----
    /** 目标指标：conversion_rate / revenue_per_session / avg_order_value */
    private String metricType;
    /** 统计显著性阈值（默认 0.95） */
    private BigDecimal significanceThreshold;
    /** 最少样本量（默认 100） */
    private Integer minSampleSize;

    // ---- 统计（实时更新）----
    /** 控制组样本数 */
    private Integer controlSampleSize;
    /** 变异组样本数 */
    private Integer variantSampleSize;
    /** 控制组转化数 */
    private Integer controlConversions;
    /** 变异组转化数 */
    private Integer variantConversions;
    /** 控制组均值 */
    private BigDecimal controlMetricValue;
    /** 变异组均值 */
    private BigDecimal variantMetricValue;
    /** 置信度（0.0~1.0） */
    private BigDecimal confidenceLevel;
    /** 提升幅度（%） */
    private BigDecimal liftPercent;
    /** 结论：WIN_CONTROL / WIN_VARIANT / NO_SIGNIFICANCE */
    private String conclusion;

    /** 实验开始日期 */
    private LocalDate startDate;
    /** 实验结束日期（null=无限期） */
    private LocalDate endDate;
    /** 备注 */
    private String description;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
