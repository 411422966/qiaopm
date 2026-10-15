package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("style_view_log")
public class StyleViewLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long styleId;
    private Long memberId;
    private String openId;
    private String source;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}