package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CareSuggestionRule;
import java.util.List;
public interface CareSuggestionRuleService extends IService<CareSuggestionRule> {    
/**    
 * 获取所有启用的规则     
*/    List<CareSuggestionRule> getActiveRules();    
/**    
 * 获取所有规则(带模板名)     
*/    List<CareSuggestionRule> getAllWithTemplate();    
/**    
 * 按条件类型查询     
*/    List<CareSuggestionRule> getByConditionType(String type);}
