package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 技师等级权益配置
 */
@Data
@TableName("biz_level_benefit")
public class LevelBenefit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long levelId;
    private String benefitType;
    private String benefitName;
    private String benefitValue;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private Integer deleted;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String levelName;

    @TableField(exist = false)
    private String levelCode;
}
