package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.mapper.CareSuggestionLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareSuggestionLogServiceImpl extends ServiceImpl<CareSuggestionLogMapper, CareSuggestionLog>
        implements CareSuggestionLogService {

    private final CareSuggestionLogMapper careSuggestionLogMapper;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @Override
    public List<CareSuggestionLog> queryList(Long memberId, String status, String suggestionType) {
        return careSuggestionLogMapper.queryList(tid(), memberId, status, suggestionType);
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        return careSuggestionLogMapper.updateStatus(tid(), id, status) > 0;
    }

    @Override
    public boolean markAccepted(Long id) {
        CareSuggestionLog log = getById(id);
        if (log == null || !tid().equals(log.getTenantId())) return false;
        log.setStatus("accepted");
        log.setAcceptTime(LocalDateTime.now());
        return updateById(log);
    }

    @Override
    public boolean markViewed(Long id) {
        CareSuggestionLog log = getById(id);
        if (log == null || !tid().equals(log.getTenantId())) return false;
        log.setStatus("viewed");
        log.setViewTime(LocalDateTime.now());
        return updateById(log);
    }

    @Override
    public boolean markRejected(Long id) {
        CareSuggestionLog log = getById(id);
        if (log == null || !tid().equals(log.getTenantId())) return false;
        log.setStatus("rejected");
        return updateById(log);
    }
}
