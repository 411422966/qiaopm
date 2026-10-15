package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * FISCAL-001：财税开票渠道配置（参考 DcepConfig 设计）
 *
 * <p>一行 = 一组百望/航天开票凭据 + 销方信息。运行时由 FiscalGateway 选择对应实现。
 *
 * <p>凭据列（appKey / appSecret）生产环境建议 KMS / 应用层加密。
 */
@Data
@TableName("fiscal_config")
public class FiscalConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** BAI_WANG / YIN_TIAN / MOCK */
    private String channel;
    /** 百望 appKey */
    private String appKey;
    /** 百望 appSecret */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String appSecret;
    /** 销方税号 */
    private String taxNo;
    /** GENERAL=普票 / SPECIAL=专票 */
    private String invoiceType;
    private String merchantName;
    /** 覆盖 SDK 默认 URL */
    private String platformUrl;
    private String notifyUrl;
    /** sandbox / production */
    private String environment;
    private Integer enabled;
    /** 渠道额外参数 JSON */
    private String extraJson;
    private String description;

    /** 二次确认密码（不入库，仅供控制器校验） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String confirmPassword;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
