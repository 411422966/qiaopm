package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ServiceMaterialCost;
import java.util.List;
import java.util.Map;
public interface ServiceMaterialCostService extends IService<ServiceMaterialCost> {    
/**    
 * 获取所有服务的耗材成本分析     
*/    List<Map<String, Object>> getServiceCostAnalysis();    
/**    
 * 获取单个服务的成本分析     
*/    Map<String, Object> getServiceCost(Long serviceId);}
