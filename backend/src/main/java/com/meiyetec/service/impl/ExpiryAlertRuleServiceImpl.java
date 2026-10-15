package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ExpiryAlertRule;
import com.meiyetec.mapper.ExpiryAlertRuleMapper;
import com.meiyetec.service.ExpiryAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class ExpiryAlertRuleServiceImpl extends ServiceImpl<ExpiryAlertRuleMapper, ExpiryAlertRule>        implements ExpiryAlertRuleService {    
private final ExpiryAlertRuleMapper expiryAlertRuleMapper;    
@Override    
public List<ExpiryAlertRule> list(Integer status) {        return expiryAlertRuleMapper.queryList(status);    }    
@Override    
public List<ExpiryAlertRule> getActiveRules() {        return expiryAlertRuleMapper.findActiveRules();    }}
