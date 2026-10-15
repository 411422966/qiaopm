package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ExpiryHandleLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface ExpiryHandleLogService extends IService<ExpiryHandleLog> {    List<ExpiryHandleLog> queryList(String handleMethod, Long storeId);    
/**    
 * 记录处理操作     
*/    boolean logHandle(ExpiryHandleLog log);    
/**    
 * 按处理方式统计     
*/    List<Map<String, Object>> statisticsByMethod(Long storeId, LocalDate startDate, LocalDate endDate);    
/**    
 * 累计损失     
*/    java.math.BigDecimal totalLoss(Long storeId);}
