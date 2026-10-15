package com.meiyetec.dcep.ota;

import lombok.Builder;
import lombok.Data;

/**
 * DCEP-04 阶段 C：设备注册响应（后端 → 终端）
 */
@Data
@Builder
public class DeviceRegistrationResponse {
    private boolean success;
    private String errorCode;
    private String errorMsg;
    /** 注册成功后分配给设备的逻辑 ID */
    private Long deviceId;
    /** 设备状态 */
    private String status;
    /** 后端证书（用后端 SM2 私钥签发的设备证书，可选） */
    private String serverCertificate;
    /** 后续 OTA 通信使用的会话 token */
    private String sessionToken;
    /** 是否有可用固件升级 */
    private boolean firmwareUpdateAvailable;
    /** 升级包信息 */
    private FirmwareUpdateInfo firmwareUpdateInfo;
}
