package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ServiceMaterialDeductLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface ServiceMaterialDeductLogService extends IService<ServiceMaterialDeductLog> {    List<ServiceMaterialDeductLog> queryList(Long orderId, String status, Long storeId);    
/**    
 * 总体统计     
*/    Map<String, Object> statistics(Long storeId, LocalDate startDate, LocalDate endDate);    
/**    
 * 某日成功率     
*/    Double successRate(Long storeId, LocalDate date);}
