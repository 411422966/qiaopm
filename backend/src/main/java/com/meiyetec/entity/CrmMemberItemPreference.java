package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * M15 项目偏好（crm_member_item_preference）
 *
 * <p>记录会员对服务/项目的偏好（行为统计，不依赖显式评分）。
 * 数据来源：消费历史中各项目的出现频次、金额贡献。
 *
 * <p>主键：(member_id, item_type, item_id, tenant_id)
 */
@Data
@TableName("crm_member_item_preference")
public class CrmMemberItemPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    /** 会员 ID */
    private Long memberId;

    /**
     * 项目类型：
     * <ul>
     *   <li>SERVICE — 服务</li>
     *   <li>PRODUCT — 商品/产品</li>
     *   <li>CATEGORY — 服务分类</li>
     *   <li>BRAND — 品牌</li>
     * </ul>
     */
    private String itemType;

    /** 关联项目 ID（SERVICE/PRODUCT/CATEGORY/BRAND 表的主键） */
    private Long itemId;

    /** 项目名称（冗余存储，便于查询展示） */
    private String itemName;

    /**
     * 偏好分（0-100）：
     * <ul>
     *   <li>基于消费次数 × 最近一次权重 + 消费金额归一化</li>
     *   <li>50 = 一般偏好</li>
     *   <li>80+ = 强烈偏好（Top 20%）</li>
     * </ul>
     */
    private Integer preferenceScore;

    /** 累计消费次数 */
    private Integer totalOrderCount;

    /** 累计消费金额（单位：分，避免浮点精度） */
    private Long totalAmountFen;

    /** 最后一次消费时间 */
    private LocalDateTime lastOrderAt;

    /** 偏好标签（逗号分隔，如：高频,高消费） */
    private String preferenceTags;

    /** 计算周期（天）：统计多久内的消费历史，默认 365 */
    private Integer calcWindowDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
