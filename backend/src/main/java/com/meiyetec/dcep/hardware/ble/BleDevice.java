package com.meiyetec.dcep.hardware.ble;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：BLE 设备扫描结果
 *
 * <p>对接真实硬件时由蓝牙 SDK（如 bluez-dbus / Nordic BLE / 小程序 BLE）填充。
 */
@Data
@Builder
public class BleDevice {

    /** 设备地址（mac 地址 / iOS peripheral id / Android BLE address） */
    private String address;

    /** 设备名称（advertise 中） */
    private String name;

    /** 信号强度（dBm） */
    private Integer rssi;

    /** 制造商数据 */
    private byte[] manufacturerData;

    /** 服务 UUID 列表（advertise 中广播） */
    private java.util.List<String> serviceUuids;

    /** 配对状态 */
    private PairingState pairingState;

    /** 最后一次扫描到的时间 */
    private LocalDateTime lastSeen;
}
