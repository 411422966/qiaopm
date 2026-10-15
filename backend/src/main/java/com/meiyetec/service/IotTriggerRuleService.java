package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.IotTriggerRule;
import java.util.List;
public interface IotTriggerRuleService extends IService<IotTriggerRule> {
    List<IotTriggerRule> findActive(Long tenantId, String deviceType, String dataType);
    /** 执行规则评估  */
    void evaluateRules(Long tenantId, Long deviceId, Long dataId);
}