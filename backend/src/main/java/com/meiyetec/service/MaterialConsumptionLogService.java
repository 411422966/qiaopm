package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.MaterialConsumptionLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface MaterialConsumptionLogService extends IService<MaterialConsumptionLog> {    
/**    
 * 记录耗材消耗     
*/    boolean logConsumption(MaterialConsumptionLog log);    
/**    
 * 获取订单的耗材消耗     
*/    List<MaterialConsumptionLog> findByOrderId(Long orderId);    
/**    
 * 按耗材统计     
*/    List<Map<String, Object>> statisticsByProduct(Long storeId, LocalDate startDate, LocalDate endDate);    
/**    
 * 按服务统计     
*/    List<Map<String, Object>> statisticsByService(Long storeId, LocalDate startDate, LocalDate endDate);}
