package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("complaint_category")
public class ComplaintCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    /** P1 / P2 / P3 / P4 */
    private String defaultPriority;
    /** 默认 SLA（分钟） */
    private Integer defaultSlaMinutes;
    private Integer enabled;
    private Integer sortOrder;
}