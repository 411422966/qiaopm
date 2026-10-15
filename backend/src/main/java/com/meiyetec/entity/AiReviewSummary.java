package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_review_summary")
public class AiReviewSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String summaryKey;
    /** DAILY / WEEKLY / MONTHLY / RANGE */
    private String summaryType;
    private Integer totalReviews;
    private Integer positiveCount;
    private Integer neutralCount;
    private Integer negativeCount;
    private BigDecimal avgRating;
    private BigDecimal nps;
    private String positiveKeywords;
    private String negativeKeywords;
    private String summaryText;
    /** -1.0 ~ 1.0 */
    private BigDecimal sentimentScore;
    private String algorithm;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}