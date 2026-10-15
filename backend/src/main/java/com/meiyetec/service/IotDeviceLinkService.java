package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.IotDeviceLink;
import com.meiyetec.entity.IotDeviceLinkLog;

import java.util.List;
import java.util.Map;

/**
 * IOT-006：设备联动服务接口
 */
public interface IotDeviceLinkService extends IService<IotDeviceLink> {

    /**
     * 执行单个联动
     *
     * @param linkId      联动关系 ID
     * @param triggerData 触发数据快照
     * @return 联动执行日志（包含 success / failed / skipped 状态）
     */
    IotDeviceLinkLog executeLink(Long linkId, Map<String, Object> triggerData);

    /**
     * 批量执行（主设备的所有启用联动）
     *
     * @param masterDeviceId 主设备 ID
     * @param triggerType    触发类型（可空 = 全部）
     * @param triggerData    触发数据
     * @return 全部执行日志
     */
    List<IotDeviceLinkLog> executeLinksByMaster(Long masterDeviceId, String triggerType,
                                                 Map<String, Object> triggerData);
}
