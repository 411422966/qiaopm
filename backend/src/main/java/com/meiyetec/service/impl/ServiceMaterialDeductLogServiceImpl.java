package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ServiceMaterialDeductLog;
import com.meiyetec.mapper.ServiceMaterialDeductLogMapper;
import com.meiyetec.service.ServiceMaterialDeductLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class ServiceMaterialDeductLogServiceImpl extends ServiceImpl<ServiceMaterialDeductLogMapper, ServiceMaterialDeductLog>        implements ServiceMaterialDeductLogService {    
private final ServiceMaterialDeductLogMapper serviceMaterialDeductLogMapper;    
@Override    
public List<ServiceMaterialDeductLog> queryList(Long orderId, String status, Long storeId) {        return serviceMaterialDeductLogMapper.queryList(orderId, status, storeId);    }    
@Override    
public Map<String, Object> statistics(Long storeId, LocalDate startDate, LocalDate endDate) {        return serviceMaterialDeductLogMapper.statistics(storeId, startDate, endDate);    }    
@Override    public Double successRate(Long storeId, LocalDate date) {        return serviceMaterialDeductLogMapper.successRate(storeId, date);    }}
