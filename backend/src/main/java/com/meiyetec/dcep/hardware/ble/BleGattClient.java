package com.meiyetec.dcep.hardware.ble;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.CompletableFuture;

/**
 * DCEP-04 阶段 C：BLE GATT 客户端抽象
 *
 * <p>对上层提供异步 API，底层可替换为：
 * <ul>
 *   <li>Linux bluez-dbus</li>
 *   <li>Nordic nRF52 SDK</li>
 *   <li>Android BluetoothGatt（间接，JVM 进程间 RPC）</li>
 *   <li>iOS CoreBluetooth（间接）</li>
 * </ul>
 */
public interface BleGattClient {

    /**
     * 异步连接 + 发现服务
     */
    CompletableFuture<BleDevice> connect(String deviceAddress, int timeoutMs);

    /**
     * 订阅 Notify characteristic
     */
    CompletableFuture<Void> subscribe(String deviceAddress, String characteristicUuid,
                                       NotificationListener listener);

    /**
     * 写入 APDU 命令（Write Without Response）
     *
     * @return 写入成功的字节数（实际无响应，需通过 NotificationListener 拿异步响应）
     */
    CompletableFuture<Integer> writeApdu(String deviceAddress, byte[] apdu);

    /**
     * 读取 characteristic
     */
    CompletableFuture<byte[]> read(String deviceAddress, String characteristicUuid);

    /** 断开连接 */
    void disconnect(String deviceAddress);

    /** 查询连接状态 */
    PairingState getState(String deviceAddress);

    /**
     * Notify 监听器
     */
    interface NotificationListener {
        /**
         * 设备推送过来的数据（APDU 响应 / 心跳 / 状态变更）
         *
         * @param deviceAddress 设备地址
         * @param characteristicUuid characteristic UUID
         * @param data 数据（可能为分片后的 APDU）
         */
        void onData(String deviceAddress, String characteristicUuid, byte[] data);

        /** 链路异常 */
        void onError(String deviceAddress, Throwable error);
    }

    /** 默认空监听器（占位用） */
    @Data
    @Builder
    class DefaultListener implements NotificationListener {
        @Override
        public void onData(String deviceAddress, String characteristicUuid, byte[] data) {
            // 占位
        }
        @Override
        public void onError(String deviceAddress, Throwable error) {
            // 占位
        }
    }
}
