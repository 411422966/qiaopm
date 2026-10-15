package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：固件 OTA 升级包
 *
 * <p>管理固件版本、签名、推送计划。
 */
@Data
@TableName("biz_dcep_firmware_package")
public class DcepFirmwarePackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 包名（厂商 + 设备型号 + 版本） */
    private String packageName;
    /** 版本号（语义化版本 semver） */
    private String version;
    /** 设备型号（用于匹配设备） */
    private String deviceModel;
    /** 文件路径 / OSS key */
    private String filePath;
    /** 文件 SHA-256 */
    private String sha256;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 数字签名（厂商私钥签名 → 设备用厂商公钥验签） */
    private String signature;
    /** 签名算法：RSA-SHA256 / SM2-SM3 */
    private String signAlgorithm;
    /** 最低硬件版本要求 */
    private String minHwVersion;
    /** 强制升级（true = 不可跳过） */
    private Integer mandatory;
    /** 发布状态：draft / staged / released / deprecated */
    private String releaseStatus;
    /** 发布时间 */
    private LocalDateTime releasedAt;
    private String releaseNotes;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
