package com.meiyetec.dcep.hardware.ble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-04 阶段 C：BLE GATT 协议常量 + PairingState 状态机测试
 */
@DisplayName("DCEP-04 阶段 C BLE GATT Protocol")
class BleGattProtocolTest {

    @Test
    @DisplayName("BLE UUID 包含 DCEP / 钱包特征字")
    void serviceUuids_namingConvention() {
        assertTrue(BleGattProtocol.SERVICE_DCEP_WALLET.contains("DCEP"));
        assertTrue(BleGattProtocol.CHAR_APDU_REQUEST.contains("DC01"));
        assertTrue(BleGattProtocol.CHAR_APDU_RESPONSE.contains("DC02"));
        assertTrue(BleGattProtocol.CHAR_HEARTBEAT.contains("DC04"));
        assertTrue(BleGattProtocol.CHAR_FW_VERSION.contains("DC05"));
        assertTrue(BleGattProtocol.CHAR_OTA_CONTROL.contains("DC06"));
        assertTrue(BleGattProtocol.CHAR_CERT_CHAIN.contains("DC07"));
    }

    @Test
    @DisplayName("所有 UUID 都是 128-bit 格式")
    void uuids_128bit() {
        String[] uuids = {
                BleGattProtocol.SERVICE_DCEP_WALLET,
                BleGattProtocol.CHAR_APDU_REQUEST,
                BleGattProtocol.CHAR_APDU_RESPONSE,
                BleGattProtocol.CHAR_HEARTBEAT,
                BleGattProtocol.CHAR_FW_VERSION,
                BleGattProtocol.CHAR_OTA_CONTROL,
                BleGattProtocol.CHAR_CERT_CHAIN
        };
        for (String u : uuids) {
            assertEquals(36, u.length(), u + " 应为 36 字符（含 4 个 -）");
            assertTrue(u.contains("-"), u + " 应为带连字符的 UUID 格式");
        }
    }

    @Test
    @DisplayName("DEFAULT_MTU = 23, MAX_APDU_LENGTH = 20")
    void mtuConstants() {
        assertEquals(23, BleGattProtocol.DEFAULT_MTU);
        assertEquals(20, BleGattProtocol.MAX_APDU_LENGTH);
    }

    @Test
    @DisplayName("心跳超时 30s, 连接超时 15s")
    void timeoutConstants() {
        assertEquals(30, BleGattProtocol.HEARTBEAT_TIMEOUT_SECONDS);
        assertEquals(15, BleGattProtocol.CONNECT_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("PairingState 状态机")
    void pairingState_transitions() {
        // 只有 PAIRED 可以 invoke APDU
        for (PairingState s : PairingState.values()) {
            if (s == PairingState.PAIRED) {
                assertTrue(s.canInvokeApdu());
                assertTrue(s.isActive());
            } else if (s == PairingState.DISCONNECTED) {
                assertFalse(s.canInvokeApdu());
                assertFalse(s.isActive());
            } else {
                assertFalse(s.canInvokeApdu(), s + " 不应允许 APDU");
                assertTrue(s.isActive(), s + " 应为活跃");
            }
        }
    }

    @Test
    @DisplayName("PairingState.values() 应包含 5 个状态")
    void pairingState_count() {
        assertEquals(5, PairingState.values().length);
    }
}
