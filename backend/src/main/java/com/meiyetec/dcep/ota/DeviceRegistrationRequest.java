package com.meiyetec.dcep.ota;

import com.meiyetec.dcep.crypto.SmCryptoProvider;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：设备注册请求（终端 → 后端）
 */
@Data
@Builder
public class DeviceRegistrationRequest {
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
    /** 当前固件版本 */
    private String fwVersion;
    /** 硬件版本 */
    private String hwVersion;
    /** 设备 SM2 公钥（hex） */
    private String devicePublicKeyHex;
    /** 设备证书（PEM 或 base64 DER） */
    private String deviceCertificate;
    /** 颁发证书的 CA 标识 */
    private String issuerCa;
    /** 证书有效期起 */
    private LocalDateTime certValidFrom;
    /** 证书有效期止 */
    private LocalDateTime certValidUntil;
    /** 注册请求签名（设备私钥 SM2 签名，用于后端验签） */
    private String requestSignature;
    /** 当前时间戳（防重放） */
    private Long timestampMs;
}

