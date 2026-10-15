package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mini_program_version")
public class MiniProgramVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** MP_WEIXIN / MP_ALIPAY / MP_BYTEDANCE */
    private String platformCode;
    private String versionCode;
    private String versionName;
    private String downloadUrl;
    private String qrCodeUrl;
    private Integer forceUpgrade;
    private String minClient;
    /** 灰度比例 0-100 */
    private Integer grayscale;
    /** DRAFT / TESTING / RELEASED / ROLLBACK */
    private String status;
    private LocalDateTime releasedAt;
    private String changelog;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}