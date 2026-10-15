package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lbs_dispatch_config")
public class LbsDispatchConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;

    private BigDecimal weightDistance;
    private BigDecimal weightSkill;
    private BigDecimal weightRating;
    private BigDecimal weightLoad;
    private BigDecimal weightTime;

    /** NEAREST_NEIGHBOR / GREEDY / MANUAL */
    private String routeStrategy;
    private Integer optimizeTotalTravel;
    private Integer maxOrdersPerTech;
    private Integer strictZoneMatch;
    private Integer fallbackToNearest;
    private Integer defaultServiceMinutes;
    private Integer bufferMinutes;
    private BigDecimal minDispatchScore;

    private Integer enabled;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}