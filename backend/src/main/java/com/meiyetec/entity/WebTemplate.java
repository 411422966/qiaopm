package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("web_template")
public class WebTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private String platformCode;
    /** vue / react / uniapp / taro */
    private String framework;
    private String description;
    /** JSON pages 定义 */
    private String pagesJson;
    private String theme;
    private String version;
    private Integer enabled;
    private String downloadUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}