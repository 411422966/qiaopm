package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("shared_package")
public class SharedPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String packageCode;
    private String packageName;
    private Long treatmentPlanId;
    private Integer totalSessions;
    private Integer usedSessions;
    private Integer shareMemberLimit;

    private BigDecimal originalPrice;
    private BigDecimal sharedPrice;
    private BigDecimal perMemberPrice;

    private Long ownerMemberId;

    private LocalDate validStartDate;
    private LocalDate validEndDate;
    private Integer expireDays;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}