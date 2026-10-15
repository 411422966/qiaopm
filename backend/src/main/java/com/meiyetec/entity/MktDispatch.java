package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-F P1-3：总部统一下发策略
 *
 * <p>M12-05 增强：增加 dispatch_subtype（PRICING_RULE/ACTIVITY/COUPON_BATCH/EXPERIMENT）、
 * source_id（源策略ID）、source_version（源策略版本号）、published_version（发布版本号）
 * 以及 success_count / fail_count / total_count（下发统计）。
 */
@Data
@TableName("mkt_dispatch")
public class MktDispatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String dispatchNo;
    /** COUPON/PACKAGE/DISCOUNT/ACTIVITY/PRICING_RULE */
    private String dispatchType;
    /** PRICING_RULE/ACTIVITY/COUPON_BATCH/EXPERIMENT（M12-05） */
    private String dispatchSubtype;
    /** 源策略 ID（M12-05：价格规则/活动/批次/实验 的 ID） */
    private Long sourceId;
    /** 源策略版本号 */
    private String sourceVersion;
    /** 发布版本号（每次重新发布 +1，M12-05） */
    private Integer publishedVersion;
    private String title;
    private String payload;
    /** ALL/BRAND/SPECIFIC */
    private String targetType;
    private String targetStoreIds;
    /** DRAFT/PUBLISHED/EXECUTING/DONE/CANCELLED */
    private String status;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime publishedAt;
    /** 成功/失败/总数（M12-05） */
    private Integer successCount;
    private Integer failCount;
    private Integer totalCount;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}