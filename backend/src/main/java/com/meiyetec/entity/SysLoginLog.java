package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户ID（可空：登录失败时可能未知） */
    private Long userId;
    /** 用户名（登录尝试填写的） */
    private String username;
    /** 状态：1 成功 / 0 失败 */
    private Integer status;
    /** 失败原因：密码错误、用户不存在、账户停用、IP限制等 */
    private String message;
    /** 客户端IP */
    private String ip;
    /** User-Agent */
    private String userAgent;
    /** 登录方式：password / sms / wechat */
    private String loginType;
    /** 租户ID（登录日志也按租户归档） */
    private Long tenantId;
    private LocalDateTime createdAt;
}
