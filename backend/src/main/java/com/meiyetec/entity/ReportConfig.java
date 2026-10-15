package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("report_config")
public class ReportConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID */
    private Long tenantId;

    private Long storeId;
    private String name;
    private Integer type;          // 1营业日报 2营业月报 3会员分析 4员工绩效 5商品销售 6卡项销售
    private String dimension;
    private String dateRange;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer isPinned;
    private Integer sortOrder;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}