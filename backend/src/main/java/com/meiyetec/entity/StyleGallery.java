package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("style_gallery")
public class StyleGallery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String styleCode;
    private String styleName;
    private String category;
    private Integer difficulty;
    private Integer estimatedMinutes;
    private BigDecimal basePrice;
    private Long serviceId;
    private String description;
    private String coverImage;
    private String imagesJson;
    private String tags;
    private Integer viewCount;
    private Integer orderCount;
    private Integer likeCount;
    private Integer isHot;
    private Integer isNew;
    private Integer isRecommend;
    private String season;
    private String gender;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}