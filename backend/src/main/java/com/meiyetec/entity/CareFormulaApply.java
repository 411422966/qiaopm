package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("care_formula_apply")
public class CareFormulaApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long formulaId;
    private Long memberId;
    private Long bookingId;
    private Long serviceId;
    private Long technicianId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime appliedAt;
    private String feedback;
    private Integer rating;
    private String effectJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}