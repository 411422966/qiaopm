package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("plugin_review")
public class PluginReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long pluginId;
    private Long memberId;
    private Integer rating;
    private String title;
    private String content;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}