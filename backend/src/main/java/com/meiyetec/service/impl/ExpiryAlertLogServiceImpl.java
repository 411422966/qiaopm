package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ExpiryAlertLog;
import com.meiyetec.mapper.ExpiryAlertLogMapper;
import com.meiyetec.service.ExpiryAlertLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpiryAlertLogServiceImpl extends ServiceImpl<ExpiryAlertLogMapper, ExpiryAlertLog>
        implements ExpiryAlertLogService {

    private final ExpiryAlertLogMapper expiryAlertLogMapper;

    @Override
    public List<ExpiryAlertLog> queryList(String status, String alertLevel, Long storeId) {
        return expiryAlertLogMapper.queryList(status, alertLevel, storeId);
    }

    @Override
    public boolean handleAlert(Long id, String handleMethod, String handlerName, String handleRemark) {
        ExpiryAlertLog log = getById(id);
        if (log == null) return false;
        log.setStatus("resolved");
        log.setHandleMethod(handleMethod);
        log.setHandlerName(handlerName);
        log.setHandleTime(LocalDateTime.now());
        log.setHandleRemark(handleRemark);
        return updateById(log);
    }

    @Override
    public boolean markIgnored(Long id) {
        return expiryAlertLogMapper.updateStatus(id, "ignored") > 0;
    }

    @Override
    public Map<String, Object> dashboardStatistics(Long storeId) {
        return expiryAlertLogMapper.dashboardStatistics(storeId);
    }
}
