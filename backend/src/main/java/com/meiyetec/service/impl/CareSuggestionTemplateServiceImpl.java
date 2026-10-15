package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CareSuggestionTemplate;
import com.meiyetec.mapper.CareSuggestionTemplateMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class CareSuggestionTemplateServiceImpl extends ServiceImpl<CareSuggestionTemplateMapper, CareSuggestionTemplate>
        implements CareSuggestionTemplateService {
    private final CareSuggestionTemplateMapper careSuggestionTemplateMapper;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }
    @Override
    public List<CareSuggestionTemplate> list(String templateType, Integer status) {
        return careSuggestionTemplateMapper.queryList(tid(), templateType, status);
    }
    @Override
    public CareSuggestionTemplate getByCode(String code) {
        return careSuggestionTemplateMapper.findByCode(tid(), code);
    }
}
