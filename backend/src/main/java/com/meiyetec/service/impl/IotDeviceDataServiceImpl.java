package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.IotDeviceData;
import com.meiyetec.mapper.IotDeviceDataMapper;
import com.meiyetec.service.IotDeviceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class IotDeviceDataServiceImpl extends ServiceImpl<IotDeviceDataMapper, IotDeviceData>
        implements IotDeviceDataService {
    private final IotDeviceDataMapper dataMapper;

    @Override
    public List<IotDeviceData> queryList(Long tenantId, Long deviceId, Long memberId, String dataType,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        return dataMapper.queryList(tenantId, deviceId, memberId, dataType, startTime, endTime);
    }

    @Override
    public List<IotDeviceData> recentByMember(Long tenantId, Long memberId) {
        return dataMapper.recentByMember(tenantId, memberId);
    }

    @Override
    public List<Map<String, Object>> trendByMember(Long tenantId, Long memberId, String dataType,
                                                    LocalDateTime startTime, LocalDateTime endTime) {
        return dataMapper.trendByMember(tenantId, memberId, dataType, startTime, endTime);
    }
}