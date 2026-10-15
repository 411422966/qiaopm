package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MaterialConsumptionLog;
import com.meiyetec.mapper.MaterialConsumptionLogMapper;
import com.meiyetec.service.MaterialConsumptionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaterialConsumptionLogServiceImpl extends ServiceImpl<MaterialConsumptionLogMapper, MaterialConsumptionLog>
        implements MaterialConsumptionLogService {

    private final MaterialConsumptionLogMapper materialConsumptionLogMapper;

    @Override
    public boolean logConsumption(MaterialConsumptionLog log) {
        if (log.getConsumptionDate() == null) {
            log.setConsumptionDate(LocalDate.now());
        }
        return save(log);
    }

    @Override
    public List<MaterialConsumptionLog> findByOrderId(Long orderId) {
        return materialConsumptionLogMapper.findByOrderId(orderId);
    }

    @Override
    public List<Map<String, Object>> statisticsByProduct(Long storeId, LocalDate startDate, LocalDate endDate) {
        return materialConsumptionLogMapper.statisticsByProduct(storeId, startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> statisticsByService(Long storeId, LocalDate startDate, LocalDate endDate) {
        return materialConsumptionLogMapper.statisticsByService(storeId, startDate, endDate);
    }
}
