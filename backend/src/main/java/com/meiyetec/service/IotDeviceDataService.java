package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.IotDeviceData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public interface IotDeviceDataService extends IService<IotDeviceData> {
    List<IotDeviceData> queryList(Long tenantId, Long deviceId, Long memberId, String dataType,
                                    LocalDateTime startTime, LocalDateTime endTime);
    List<IotDeviceData> recentByMember(Long tenantId, Long memberId);
    /** 客户检测趋势数据  */
    List<Map<String, Object>> trendByMember(Long tenantId, Long memberId, String dataType,
                                              LocalDateTime startTime, LocalDateTime endTime);
}