package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.IotDeviceAlert;
import java.util.List;
public interface IotDeviceAlertService extends IService<IotDeviceAlert> {
    List<IotDeviceAlert> queryList(Long tenantId, Long deviceId, String alertLevel, String status, Long storeId);
    /** 创建告警 */
    IotDeviceAlert createAlert(Long tenantId, Long deviceId, String alertLevel, String alertType, String title, String message);
}