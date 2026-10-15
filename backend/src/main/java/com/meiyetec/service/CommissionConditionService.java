package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CommissionCondition;
import java.util.List;
public interface CommissionConditionService extends IService<CommissionCondition> {    
/**    
 * 获取规则的所有条件     
*/    List<CommissionCondition> getConditionsByRuleId(Long ruleId);    
/**    
 * 批量保存条件     
*/    boolean batchSaveConditions(Long ruleId, List<CommissionCondition> conditions);}
