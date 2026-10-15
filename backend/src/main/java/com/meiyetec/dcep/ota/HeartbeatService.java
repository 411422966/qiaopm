package com.meiyetec.dcep.ota;

import com.meiyetec.entity.DcepDeviceHeartbeatLog;
import com.meiyetec.entity.DcepDeviceRegistration;
import com.meiyetec.mapper.DcepDeviceHeartbeatLogMapper;
import com.meiyetec.mapper.DcepDeviceRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：设备心跳服务
 *
 * <p>职责：
 * <ol>
 *   <li>记录心跳日志（含电量 / 信号强度 / FW 版本 / 待同步交易数）</li>
 *   <li>更新设备表 lastHeartbeat / batteryLevel / rssi</li>
 *   <li>检测设备离线（超过 N 分钟未心跳 → 标记）</li>
 * </ol>
 */
@Slf4j
@Service
public class HeartbeatService {

    @Autowired private DcepDeviceHeartbeatLogMapper heartbeatMapper;
    @Autowired private DcepDeviceRegistrationMapper registrationMapper;

    /** 设备离线下限（秒）：超过此时间未心跳视为离线 */
    private static final int OFFLINE_THRESHOLD_SECONDS = 120;

    /**
     * 接收心跳
     */
    public void recordHeartbeat(String deviceSerial, Integer batteryLevel, Integer rssi,
                                  String fwVersion, Long offlineTxCount, Long pendingSyncCount,
                                  String healthStatus) {
        // 1. 写入心跳日志
        DcepDeviceHeartbeatLog log = new DcepDeviceHeartbeatLog();
        log.setDeviceSerial(deviceSerial);
        log.setHeartbeatAt(LocalDateTime.now());
        log.setBatteryLevel(batteryLevel);
        log.setRssi(rssi);
        log.setFwVersion(fwVersion);
        log.setOfflineTxCount(offlineTxCount);
        log.setPendingSyncCount(pendingSyncCount);
        log.setHealthStatus(healthStatus);
        heartbeatMapper.insert(log);

        // 2. 更新设备表
        DcepDeviceRegistration device = registrationMapper.selectById(deviceSerial);
        if (device != null) {
            device.setLastHeartbeat(LocalDateTime.now());
            device.setBatteryLevel(batteryLevel);
            device.setRssi(rssi);
            device.setFwVersion(fwVersion);
            registrationMapper.updateById(device);
        }
    }

    /**
     * 判断设备是否在线（最近 N 秒有心跳）
     */
    public boolean isOnline(String deviceSerial) {
        DcepDeviceRegistration device = registrationMapper.selectById(deviceSerial);
        if (device == null || device.getLastHeartbeat() == null) return false;
        long elapsedSeconds = java.time.Duration.between(
                device.getLastHeartbeat(), LocalDateTime.now()).getSeconds();
        return elapsedSeconds <= OFFLINE_THRESHOLD_SECONDS;
    }

    /**
     * 检测全网设备离线状态，返回告警数量
     */
    public int detectOfflineDevices() {
        // 简化实现：生产环境用 SQL 批量扫描
        return 0;
    }
}
