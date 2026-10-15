package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.IotDeviceAlert;
import com.meiyetec.mapper.IotDeviceAlertMapper;
import com.meiyetec.service.IotDeviceAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IotDeviceAlertServiceImpl extends ServiceImpl<IotDeviceAlertMapper, IotDeviceAlert>
        implements IotDeviceAlertService {

    private final IotDeviceAlertMapper alertMapper;

    @Override
    public List<IotDeviceAlert> queryList(Long tenantId, Long deviceId, String alertLevel, String status, Long storeId) {
        return alertMapper.queryList(tenantId, deviceId, alertLevel, status, storeId);
    }

    @Override
    public IotDeviceAlert createAlert(Long tenantId, Long deviceId, String alertLevel, String alertType,
                                      String title, String message) {
        IotDeviceAlert alert = new IotDeviceAlert();
        alert.setTenantId(tenantId);
        alert.setDeviceId(deviceId);
        alert.setAlertLevel(alertLevel);
        alert.setAlertType(alertType);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setStatus("pending");
        alert.setAlertTime(LocalDateTime.now());
        save(alert);
        return alert;
    }
}