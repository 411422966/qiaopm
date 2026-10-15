package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.LevelEvaluation;
import java.util.List;
import java.util.Map;
public interface LevelEvaluationService extends IService<LevelEvaluation> {    
/**    
 * 获取员工的评定历史     
*/    List<LevelEvaluation> getByEmployeeId(Long employeeId);    
/**    
 * 查询评定记录     
*/    List<Map<String, Object>> queryRecords(Long employeeId, Long storeId, String status);    
/**    
 * 执行等级评定（自动）     
*/    LevelEvaluation evaluate(Long employeeId, Long operatorId, String operatorName, Long storeId);    
/**    
 * 手动调整等级     
*/    LevelEvaluation adjustLevel(Long employeeId, Long toLevelId, String note, Long operatorId, String operatorName, Long storeId);    
/**    
 * 撤销评定     
*/    boolean revert(Long evaluationId);    
/**    
 * 计算技师当前等级（自动评估用）     
*/    Map<String, Object> calculateEmployeeMetrics(Long employeeId);}
