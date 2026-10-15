package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CareSuggestionRule;
import com.meiyetec.mapper.CareSuggestionRuleMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class CareSuggestionRuleServiceImpl extends ServiceImpl<CareSuggestionRuleMapper, CareSuggestionRule>
        implements CareSuggestionRuleService {
    private final CareSuggestionRuleMapper careSuggestionRuleMapper;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }
    @Override
    public List<CareSuggestionRule> getActiveRules() {
        return careSuggestionRuleMapper.findActiveRules(tid());
    }
    @Override
    public List<CareSuggestionRule> getAllWithTemplate() {
        return careSuggestionRuleMapper.findAllWithTemplate(tid());
    }
    @Override
    public List<CareSuggestionRule> getByConditionType(String type) {
        return careSuggestionRuleMapper.findByConditionType(tid(), type);
    }
}
