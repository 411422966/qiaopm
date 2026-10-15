package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R3.0-H P1.4：疗程阶段步骤（含并行/串行）
 *
 * <p>颗粒度比 phase 更细。一次到店可能涉及多个步骤，按 step_group 控制：
 * <ul>
 *   <li>不同 group 之间：串行（G1 → G2 → G3...）</li>
 *   <li>同 group 内：并行（同时做，如：左脸按摩 + 右脸面膜）</li>
 * </ul>
 */
@Data
@TableName("biz_treatment_phase_step")
public class BizTreatmentPhaseStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long phaseId;
    private Long planId;
    private Integer stepNo;
    /** 步骤分组，相同 group 即并行，不同 group 即串行 */
    private String stepGroup;
    /** SERIAL=串行 / PARALLEL=并行（语义标签，实际由 group 决定） */
    private String stepType;
    private String stepName;
    private Integer durationMinutes;
    private Long productId;
    private BigDecimal productQty;
    private String toolRequired;
    private String recipeText;
    private String remark;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}