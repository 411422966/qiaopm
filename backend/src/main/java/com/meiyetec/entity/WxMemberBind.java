package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-1：微信小程序会员绑定
 */
@Data
@TableName("wx_member_bind")
public class WxMemberBind {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String appid;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String sessionKey;
    private LocalDateTime bindAt;
    private LocalDateTime lastLoginAt;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}