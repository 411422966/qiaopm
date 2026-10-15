package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ServiceMaterialRule;
import java.util.List;
public interface ServiceMaterialRuleService extends IService<ServiceMaterialRule> {    List<ServiceMaterialRule> queryList(Long serviceId, Long productId);    
/**    
 * 根据服务ID查询耗材绑定规则     
*/    List<ServiceMaterialRule> findByServiceId(Long serviceId);    
/**    
 * 批量保存（替换该服务的全部耗材配置）     
*/    boolean replaceServiceBindings(Long serviceId, List<ServiceMaterialRule> rules);}
