package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ResourceMatchRule;
public interface ResourceMatchRuleService extends IService<ResourceMatchRule> {    ResourceMatchRule findByServiceId(Long serviceId);}
