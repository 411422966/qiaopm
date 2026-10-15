package com.meiyetec.dcep.hardware.ble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-04 阶段 C：StubBleGattClient 测试
 */
@DisplayName("DCEP-04 阶段 C StubBleGattClient")
class StubBleGattClientTest {

    @Test
    @DisplayName("connect → 状态自动推进到 PAIRED")
    void connect_proceedToPaired() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        BleDevice dev = stub.connect("AA:BB:CC:DD:EE:FF", 5000).get();
        assertNotNull(dev);
        assertEquals("AA:BB:CC:DD:EE:FF", dev.getAddress());
        assertEquals(PairingState.PAIRED, stub.getState("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    @DisplayName("disconnect → 状态回到 DISCONNECTED")
    void disconnect_backToDisconnected() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        stub.connect("AA:BB:CC:DD:EE:FF", 5000).get();
        stub.disconnect("AA:BB:CC:DD:EE:FF");
        assertEquals(PairingState.DISCONNECTED, stub.getState("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    @DisplayName("getState 未注册地址返回 DISCONNECTED")
    void getState_unknownAddress() {
        StubBleGattClient stub = new StubBleGattClient();
        assertEquals(PairingState.DISCONNECTED, stub.getState("XX:XX:XX"));
    }

    @Test
    @DisplayName("subscribe 返回 CompletableFuture<Void>")
    void subscribe_completes() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        stub.connect("AA:BB:CC:DD:EE:FF", 1000).get();
        CompletableFuture<Void> future = stub.subscribe("AA:BB:CC:DD:EE:FF",
                BleGattProtocol.CHAR_APDU_RESPONSE, BleGattClient.DefaultListener.builder().build());
        assertNotNull(future);
        assertNull(future.get());
    }

    @Test
    @DisplayName("writeApdu 返回写入字节数")
    void writeApdu_returnsLength() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        stub.connect("AA:BB:CC:DD:EE:FF", 1000).get();
        byte[] apdu = new byte[]{0x00, 0x12, 0x34, 0x56};
        Integer written = stub.writeApdu("AA:BB:CC:DD:EE:FF", apdu).get();
        assertEquals(4, written);
    }

    @Test
    @DisplayName("read 返回默认空字节（未注入）")
    void read_emptyDefault() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        byte[] data = stub.read("AA:BB:CC:DD:EE:FF", BleGattProtocol.CHAR_FW_VERSION).get();
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("injectCharacteristic → read 返回注入值")
    void injectCharacteristic_returnValue() throws Exception {
        StubBleGattClient stub = new StubBleGattClient();
        stub.connect("AA:BB:CC:DD:EE:FF", 1000).get();
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        stub.injectCharacteristic("AA:BB:CC:DD:EE:FF", BleGattProtocol.CHAR_FW_VERSION, payload);
        byte[] read = stub.read("AA:BB:CC:DD:EE:FF", BleGattProtocol.CHAR_FW_VERSION).get();
        assertArrayEquals(payload, read);
    }
}
