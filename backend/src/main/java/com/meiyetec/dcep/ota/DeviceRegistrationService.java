package com.meiyetec.dcep.ota;

import com.meiyetec.dcep.crypto.SmCryptoProvider;
import com.meiyetec.entity.DcepDeviceRegistration;
import com.meiyetec.mapper.DcepDeviceRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DCEP-04 阶段 C：设备注册服务
 *
 * <p>职责：
 * <ol>
 *   <li>验证设备 SM2 证书（用设备公钥验签请求）</li>
 *   <li>写入设备表 {@code biz_dcep_device_registration}</li>
 *   <li>颁发会话 token</li>
 *   <li>检查是否有可用固件升级</li>
 * </ol>
 */
@Slf4j
@Service
public class DeviceRegistrationService {

    @Autowired private DcepDeviceRegistrationMapper registrationMapper;
    @Autowired(required = false) private SmCryptoProvider smCrypto;
    @Autowired(required = false) private FirmwareService firmwareService;

    /** 时间戳允许偏差（毫秒） */
    private static final long TIMESTAMP_TOLERANCE_MS = 60_000;

    /**
     * 注册 / 更新设备
     */
    public DeviceRegistrationResponse register(DeviceRegistrationRequest req, Long tenantId) {
        // 1. 时间戳校验
        if (req.getTimestampMs() == null
                || Math.abs(System.currentTimeMillis() - req.getTimestampMs()) > TIMESTAMP_TOLERANCE_MS) {
            return DeviceRegistrationResponse.builder()
                    .success(false).errorCode("TIMESTAMP_INVALID")
                    .errorMsg("请求时间戳不在允许偏差内")
                    .build();
        }
        // 2. SM2 验签（设备私钥签名 → 后端用设备公钥验签）
        if (!verifyDeviceSignature(req)) {
            return DeviceRegistrationResponse.builder()
                    .success(false).errorCode("SIGNATURE_INVALID")
                    .errorMsg("设备签名验证失败")
                    .build();
        }
        // 3. 证书有效期校验
        if (req.getCertValidUntil() != null && req.getCertValidUntil().isBefore(LocalDateTime.now())) {
            return DeviceRegistrationResponse.builder()
                    .success(false).errorCode("CERT_EXPIRED")
                    .errorMsg("设备证书已过期")
                    .build();
        }
        // 4. upsert 设备记录
        DcepDeviceRegistration existing = registrationMapper.selectById(req.getDeviceSerial());
        DcepDeviceRegistration device = existing == null ? new DcepDeviceRegistration() : existing;
        device.setTenantId(tenantId);
        device.setDeviceSerial(req.getDeviceSerial());
        device.setBleAddress(req.getBleAddress());
        device.setDeviceType(req.getDeviceType());
        device.setDeviceModel(req.getDeviceModel());
        device.setBrand(req.getBrand());
        device.setFwVersion(req.getFwVersion());
        device.setHwVersion(req.getHwVersion());
        device.setDevicePublicKey(req.getDevicePublicKeyHex());
        device.setDeviceCertificate(req.getDeviceCertificate());
        device.setIssuerCa(req.getIssuerCa());
        device.setCertValidFrom(req.getCertValidFrom());
        device.setCertValidUntil(req.getCertValidUntil());
        if (existing == null) {
            device.setStatus("active");
            device.setRegisteredAt(LocalDateTime.now());
            device.setActivatedAt(LocalDateTime.now());
            registrationMapper.insert(device);
        } else {
            registrationMapper.updateById(device);
        }

        // 5. 检查固件升级
        FirmwareUpdateInfo fwInfo = null;
        boolean fwAvailable = false;
        if (firmwareService != null && req.getFwVersion() != null) {
            fwInfo = firmwareService.findAvailableUpdate(req.getDeviceModel(), req.getFwVersion());
            fwAvailable = (fwInfo != null);
        }

        // 6. 颁发 session token（实际生产应该用 JWT，简化用 UUID）
        String token = UUID.randomUUID().toString().replace("-", "");

        log.info("[DCEP-OTA] device registered: serial={} model={} fw={}",
                req.getDeviceSerial(), req.getDeviceModel(), req.getFwVersion());

        return DeviceRegistrationResponse.builder()
                .success(true)
                .deviceId(device.getId())
                .status(device.getStatus())
                .sessionToken(token)
                .firmwareUpdateAvailable(fwAvailable)
                .firmwareUpdateInfo(fwInfo)
                .build();
    }

    private boolean verifyDeviceSignature(DeviceRegistrationRequest req) {
        if (smCrypto == null) {
            log.warn("[DCEP-OTA] SmCryptoProvider 未配置，跳过签名验证（仅 dev）");
            return true;
        }
        if (req.getRequestSignature() == null || req.getRequestSignature().isEmpty()) {
            log.warn("[DCEP-OTA] 设备未提供签名：serial={}", req.getDeviceSerial());
            return false;
        }
        if (req.getDevicePublicKeyHex() == null) {
            log.warn("[DCEP-OTA] 设备公钥为空：serial={}", req.getDeviceSerial());
            return false;
        }
        try {
            byte[] pubKey = hexToBytes(req.getDevicePublicKeyHex());
            byte[] payload = buildSignPayload(req);
            byte[] sig = hexToBytes(req.getRequestSignature());
            return smCrypto.sm2Verify(pubKey, payload,
                    com.meiyetec.dcep.crypto.Sm2Signature.fromConcat(sig));
        } catch (Exception e) {
            log.warn("[DCEP-OTA] 验签异常：{}，拒绝注册", e.getMessage());
            return false;
        }
    }

    private byte[] buildSignPayload(DeviceRegistrationRequest req) {
        String s = String.join("|",
                safe(req.getDeviceSerial()),
                safe(req.getBleAddress()),
                safe(req.getDeviceModel()),
                safe(req.getFwVersion()),
                safe(req.getDevicePublicKeyHex()),
                String.valueOf(req.getTimestampMs()));
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
