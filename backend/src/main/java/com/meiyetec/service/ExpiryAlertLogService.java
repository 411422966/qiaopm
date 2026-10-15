package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ExpiryAlertLog;
import java.util.List;
import java.util.Map;
public interface ExpiryAlertLogService extends IService<ExpiryAlertLog> {    List<ExpiryAlertLog> queryList(String status, String alertLevel, Long storeId);    
/**    
 * 处理预警     
*/    boolean handleAlert(Long id, String handleMethod, String handlerName, String handleRemark);    
/**    
 * 标记为已忽略     
*/    boolean markIgnored(Long id);    
/**    
 * 看板统计     
*/    Map<String, Object> dashboardStatistics(Long storeId);}
