package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("i18n_translation")
public class I18nTranslation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String localeCode;
    private String messageKey;
    private String messageValue;
    private String module;
    private String description;
    private Integer enabled;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}