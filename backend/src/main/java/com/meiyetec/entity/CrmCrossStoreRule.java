package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_cross_store_rule")
public class CrmCrossStoreRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** NULL=租户级通用规则 */
    private Long brandId;
    private String ruleName;
    /** 规则说明/备注 */
    private String remark;
    /** 规则生效开始时间 NULL=立即生效 */
    private LocalDateTime startTime;
    /** 规则生效结束时间 NULL=永久生效 */
    private LocalDateTime endTime;
    /** 跨店识别（手机号匹配） */
    private Integer memberCrossEnabled;
    /** 跨店核销套餐 */
    private Integer packageCrossEnabled;
    /** BRAND品牌内 / CHAIN链内 / ANY任意门店 / DISABLED禁止 */
    private String packageCrossMode;
    private Integer pointsCrossEnabled;
    private Integer balanceCrossEnabled;
    /** BRAND品牌内 / STORE仅本门店 / DISABLED禁止 */
    private String balanceCrossMode;
    private Integer discountCrossEnabled;
    private Integer couponCrossEnabled;
    /** 跨店手续费比例% 0=不收 */
    private BigDecimal crossFeeRate;
    /** NONE不收 / PERCENT按比例 / FIXED固定 */
    private String crossFeeType;
    /** 跨店单笔限额 0=不限 */
    private BigDecimal crossMaxAmount;
    private Integer enabled;
    private Integer priority;
    /** 被命中次数（仅统计，不可写） */
    private Long hitCount;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
