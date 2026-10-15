package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：硬件设备注册
 *
 * <p>终端 → 后端注册时填写，用于 OTA 通道的身份识别。
 *
 * <p>关键字段：
 * <ul>
 *   <li>{@code deviceSerial}：设备序列号（厂商分配）</li>
 *   <li>{@code bleAddress}：BLE MAC 地址</li>
 *   <li>{@code devicePublicKey}：SM2 公钥 hex（用于验签设备响应）</li>
 *   <li>{@code fwVersion} / {@code hwVersion}：固件 / 硬件版本</li>
 *   <li>{@code status}：active / lost / stolen / frozen / decommissioned</li>
 * </ul>
 */
@Data
@TableName("biz_dcep_device_registration")
public class DcepDeviceRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 设备序列号（厂商分配） */
    private String deviceSerial;
    /** BLE MAC 地址 */
    private String bleAddress;
    /** 设备类型：nfc_sim / se_chip / iot_wallet / biometric */
    private String deviceType;
    /** 设备型号 */
    private String deviceModel;
    /** 品牌 */
    private String brand;
    /** 固件版本 */
    private String fwVersion;
    /** 硬件版本 */
    private String hwVersion;
    /** SM2 设备公钥（hex） */
    private String devicePublicKey;
    /** SM2 设备证书（PEM 或 base64 DER） */
    private String deviceCertificate;
    /** 颁发证书的 CA 标识 */
    private String issuerCa;
    /** 证书有效期起 */
    private LocalDateTime certValidFrom;
    /** 证书有效期止 */
    private LocalDateTime certValidUntil;
    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;
    /** 最后上报电量（百分比 0~100） */
    private Integer batteryLevel;
    /** 信号强度（dBm） */
    private Integer rssi;
    /** 状态：active / lost / stolen / frozen / decommissioned */
    private String status;
    /** 注册时间 */
    private LocalDateTime registeredAt;
    /** 激活时间 */
    private LocalDateTime activatedAt;
    /** 解绑时间 */
    private LocalDateTime decommissionedAt;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
