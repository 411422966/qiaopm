package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ResourceMatchRule;
import com.meiyetec.mapper.ResourceMatchRuleMapper;
import com.meiyetec.service.ResourceMatchRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class ResourceMatchRuleServiceImpl extends ServiceImpl<ResourceMatchRuleMapper, ResourceMatchRule>        implements ResourceMatchRuleService {    
private final ResourceMatchRuleMapper resourceMatchRuleMapper;    
@Override    public ResourceMatchRule findByServiceId(Long serviceId) {        return resourceMatchRuleMapper.findByServiceId(serviceId);    }}
