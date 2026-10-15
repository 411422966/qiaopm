package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ExpiryHandleLog;
import com.meiyetec.mapper.ExpiryHandleLogMapper;
import com.meiyetec.service.ExpiryHandleLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpiryHandleLogServiceImpl extends ServiceImpl<ExpiryHandleLogMapper, ExpiryHandleLog>
        implements ExpiryHandleLogService {

    private final ExpiryHandleLogMapper expiryHandleLogMapper;

    @Override
    public List<ExpiryHandleLog> queryList(String handleMethod, Long storeId) {
        return expiryHandleLogMapper.queryList(handleMethod, storeId);
    }

    @Override
    public boolean logHandle(ExpiryHandleLog log) {
        if (log.getHandleDate() == null) {
            log.setHandleDate(LocalDate.now());
        }
        if (log.getLossAmount() == null) {
            BigDecimal price = log.getUnitPrice() == null ? BigDecimal.ZERO : log.getUnitPrice();
            long q = log.getHandleQuantity() == null ? 0L : log.getHandleQuantity();
            BigDecimal qty = BigDecimal.valueOf(q);
            log.setLossAmount(price.multiply(qty));
        }
        return save(log);
    }

    @Override
    public List<Map<String, Object>> statisticsByMethod(Long storeId, LocalDate startDate, LocalDate endDate) {
        return expiryHandleLogMapper.statisticsByMethod(storeId, startDate, endDate);
    }

    @Override
    public BigDecimal totalLoss(Long storeId) {
        return expiryHandleLogMapper.totalLoss(storeId);
    }
}
