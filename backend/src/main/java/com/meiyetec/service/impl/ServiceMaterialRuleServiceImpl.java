package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ServiceMaterialRule;
import com.meiyetec.mapper.ServiceMaterialRuleMapper;
import com.meiyetec.service.ServiceMaterialRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceMaterialRuleServiceImpl extends ServiceImpl<ServiceMaterialRuleMapper, ServiceMaterialRule>
        implements ServiceMaterialRuleService {

    private final ServiceMaterialRuleMapper serviceMaterialRuleMapper;

    @Override
    public List<ServiceMaterialRule> queryList(Long serviceId, Long productId) {
        return serviceMaterialRuleMapper.queryList(serviceId, productId);
    }

    @Override
    public List<ServiceMaterialRule> findByServiceId(Long serviceId) {
        return serviceMaterialRuleMapper.findByServiceId(serviceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceServiceBindings(Long serviceId, List<ServiceMaterialRule> rules) {
        // 逻辑删除旧绑定
        List<ServiceMaterialRule> existing = serviceMaterialRuleMapper.findByServiceId(serviceId);
        for (ServiceMaterialRule r : existing) {
            removeById(r.getId());
        }
        // 批量新增
        if (rules != null && !rules.isEmpty()) {
            for (ServiceMaterialRule r : rules) {
                r.setId(null);
                r.setServiceId(serviceId);
                save(r);
            }
        }
        return true;
    }
}

