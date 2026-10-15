package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ExpiryAlertRule;
import java.util.List;
public interface ExpiryAlertRuleService extends IService<ExpiryAlertRule> {    List<ExpiryAlertRule> list(Integer status);    List<ExpiryAlertRule> getActiveRules();}
