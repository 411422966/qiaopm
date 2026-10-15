package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户分桶（A/B 实验分配记录）
 *
 * <p>AI-007 扩展：增加转化追踪字段，使 {@link AbExperiment#computeSignificance(java.util.Map)}
 * 能基于真实的转化数据计算卡方显著性（不再是空假设的均匀分布）。
 */
@Data
@TableName("ab_assignment")
public class AbAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String expKey;
    private Long userId;
    private String variantKey;
    private LocalDateTime assignedAt;

    // ============= AI-007：转化追踪 =============

    /** RECORDED / CONVERTED / EXPIRED */
    private String conversionStatus;
    /** 转化时间 */
    private LocalDateTime convertedAt;
    /** 转化金额 */
    private BigDecimal conversionAmount;
    /** 通用指标值（AOV / GMV） */
    private BigDecimal metricValue;
    /** 指标来源：CN_ORDER / GMV / RETENTION */
    private String metricSource;
}
