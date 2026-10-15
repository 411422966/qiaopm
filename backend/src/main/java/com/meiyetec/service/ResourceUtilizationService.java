package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ResourceUtilization;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface ResourceUtilizationService extends IService<ResourceUtilization> {    List<ResourceUtilization> queryList(Long storeId, String resourceType, Long resourceId,                                          LocalDate startDate, LocalDate endDate);    List<Map<String, Object>> topUtilization(Long storeId, LocalDate startDate, LocalDate endDate);    
/**    
 * 重新计算某日资源利用率     
*/    void recalculate(Long storeId, LocalDate date);}
