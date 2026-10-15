package com.meiyetec.dcep.hardware.ble;

/**
 * DCEP-04 阶段 C：BLE GATT 协议常量
 *
 * <h3>数字人民币钱包 BLE 服务（参考 PC/SC over BLE 草案）</h3>
 * <pre>
 *   Service UUID:       0000DCEP-0000-1000-8000-00805F9B34FB
 *   Characteristic:
 *     - APDU_REQUEST    0000DC01-...  (Write Without Response, 设备 ← 后端)
 *     - APDU_RESPONSE   0000DC02-...  (Notify, 设备 → 后端)
 *     - DEVICE_INFO     0000DC03-...  (Read, 设备元数据)
 *     - HEARTBEAT       0000DC04-...  (Notify, 心跳)
 *     - FW_VERSION      0000DC05-...  (Read, 固件版本)
 *     - OTA_CONTROL     0000DC06-...  (Write, OTA 升级控制)
 *     - CERT_CHAIN      0000DC07-...  (Read, 设备证书链)
 * </pre>
 *
 * <p>实际接入时，UUID 由厂商 SDK 文档给出（部分厂商会自定义 128-bit UUID）。
 */
public final class BleGattProtocol {

    private BleGattProtocol() {}

    /** DCEP 钱包服务 UUID（占位：实际使用厂商提供的 128-bit UUID） */
    public static final String SERVICE_DCEP_WALLET = "0000DCEP-0000-1000-8000-00805F9B34FB";

    /** APDU 命令（后端 → 设备） */
    public static final String CHAR_APDU_REQUEST = "0000DC01-0000-1000-8000-00805F9B34FB";
    /** APDU 响应（设备 → 后端） */
    public static final String CHAR_APDU_RESPONSE = "0000DC02-0000-1000-8000-00805F9B34FB";
    /** 设备信息（型号/SE 芯片 ID 等） */
    public static final String CHAR_DEVICE_INFO = "0000DC03-0000-1000-8000-00805F9B34FB";
    /** 心跳（设备 → 后端） */
    public static final String CHAR_HEARTBEAT = "0000DC04-0000-1000-8000-00805F9B34FB";
    /** 固件版本 */
    public static final String CHAR_FW_VERSION = "0000DC05-0000-1000-8000-00805F9B34FB";
    /** OTA 升级控制 */
    public static final String CHAR_OTA_CONTROL = "0000DC06-0000-1000-8000-00805F9B34FB";
    /** 设备证书链 */
    public static final String CHAR_CERT_CHAIN = "0000DC07-0000-1000-8000-00805F9B34FB";

    /** BLE MTU 默认值（实际可调到 247） */
    public static final int DEFAULT_MTU = 23;

    /** 单包 APDU 最大长度（受 MTU 限制） */
    public static final int MAX_APDU_LENGTH = DEFAULT_MTU - 3;

    /** 心跳超时（秒） */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 30;

    /** 连接超时（秒） */
    public static final int CONNECT_TIMEOUT_SECONDS = 15;
}
