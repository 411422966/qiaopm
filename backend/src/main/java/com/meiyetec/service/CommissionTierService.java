package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CommissionTier;
import java.util.List;
public interface CommissionTierService extends IService<CommissionTier> {    
/**    
 * 获取规则的所有阶梯     
*/    List<CommissionTier> getTiersByRuleId(Long ruleId);    
/**    
 * 批量保存阶梯     
*/    boolean batchSaveTiers(Long ruleId, List<CommissionTier> tiers);}
