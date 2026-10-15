package com.meiyetec.dcep.hardware.ble;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DCEP-04 阶段 C：BLE GATT 客户端占位实现（未对接真实硬件时使用）
 *
 * <p>所有写操作返回 NETWORK_ERROR，读操作返回空字节。
 * 真实硬件接入时只需提供 {@link BleGattClient} 的另一个实现并标注 {@code @Primary}。
 */
@Slf4j
public class StubBleGattClient implements BleGattClient {

    private final Map<String, PairingState> states = new ConcurrentHashMap<>();
    private final Map<String, Map<String, byte[]>> charStorages = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<BleDevice> connect(String deviceAddress, int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[BLE-Stub] connect: addr={} timeoutMs={}", deviceAddress, timeoutMs);
            states.put(deviceAddress, PairingState.CONNECTING);
            states.put(deviceAddress, PairingState.CONNECTED);
            states.put(deviceAddress, PairingState.SERVICE_DISCOVERED);
            states.put(deviceAddress, PairingState.PAIRED);
            return BleDevice.builder()
                    .address(deviceAddress)
                    .name("DCEP-Stub-Wallet")
                    .pairingState(states.get(deviceAddress))
                    .build();
        });
    }

    @Override
    public CompletableFuture<Void> subscribe(String deviceAddress, String characteristicUuid,
                                              NotificationListener listener) {
        log.info("[BLE-Stub] subscribe: addr={} char={}", deviceAddress, characteristicUuid);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> writeApdu(String deviceAddress, byte[] apdu) {
        log.debug("[BLE-Stub] writeApdu: addr={} len={}", deviceAddress, apdu.length);
        return CompletableFuture.completedFuture(apdu.length);
    }

    @Override
    public CompletableFuture<byte[]> read(String deviceAddress, String characteristicUuid) {
        Map<String, byte[]> storage = charStorages.computeIfAbsent(deviceAddress, k -> new HashMap<>());
        return CompletableFuture.completedFuture(storage.getOrDefault(characteristicUuid, new byte[0]));
    }

    @Override
    public void disconnect(String deviceAddress) {
        log.info("[BLE-Stub] disconnect: addr={}", deviceAddress);
        states.put(deviceAddress, PairingState.DISCONNECTED);
    }

    @Override
    public PairingState getState(String deviceAddress) {
        return states.getOrDefault(deviceAddress, PairingState.DISCONNECTED);
    }

    /** Stub 工具：让测试或其他组件注入模拟响应 */
    public void injectCharacteristic(String deviceAddress, String characteristicUuid, byte[] data) {
        charStorages.computeIfAbsent(deviceAddress, k -> new HashMap<>()).put(characteristicUuid, data);
    }

    @Configuration
    public static class StubConfig {
        @Bean
        @ConditionalOnMissingBean(BleGattClient.class)
        public BleGattClient bleGattClient() {
            log.warn("[DCEP-04] BleGattClient 未配置，使用 StubBleGattClient（仅 dev / 联调用）。"
                    + "真实硬件接入时需实现 BleGattClient 并标注 @Primary。");
            return new StubBleGattClient();
        }
    }
}
