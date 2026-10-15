package com.meiyetec.dcep.hardware.ble;

/**
 * DCEP-04 阶段 C：BLE 配对状态机
 *
 * <pre>
 *   DISCONNECTED
 *       ↓  scan / connect
 *   CONNECTING
 *       ↓  GATT connect success
 *   CONNECTED
 *       ↓  service discovery
 *   SERVICE_DISCOVERED
 *       ↓  subscribe notify
 *   PAIRED              ← 已订阅，双向数据通道就绪
 *       ↓  (业务结束 / 设备离开 / 异常)
 *   DISCONNECTED
 * </pre>
 */
public enum PairingState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SERVICE_DISCOVERED,
    PAIRED;

    public boolean canInvokeApdu() {
        return this == PAIRED;
    }

    public boolean isActive() {
        return this != DISCONNECTED;
    }
}
