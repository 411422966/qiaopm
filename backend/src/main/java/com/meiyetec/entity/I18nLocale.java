package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("i18n_locale")
public class I18nLocale {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** zh-CN / en-US / ja-JP */
    private String localeCode;
    private String localeName;
    private String nativeName;
    private Integer isDefault;
    private Integer enabled;
    private Integer rtl;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}