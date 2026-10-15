package com.meiyetec.service;
import com.meiyetec.entity.CareSuggestionLog;
import java.util.List;
public interface CareSuggestionEngineService {    
/**    
 * 为单个客户生成护理建议    
 * @param memberId 客户ID    
 * @param operatorId 操作人ID（手动触发时）    
 * @param operatorName 操作人姓名    
 * @param triggerType 触发类型: auto(自动) / manual(手动)    
 * @return 生成的建议记录列表     
*/    List<CareSuggestionLog> generateForMember(Long memberId, Long operatorId, String operatorName, String triggerType);    
/**    
 * 批量为多个客户生成建议    
 * @param memberIds 客户ID列表    
 * @param operatorId 操作人ID    
 * @param operatorName 操作人姓名    
 * @return 生成的建议总数     
*/    int generateForMembers(List<Long> memberIds, Long operatorId, String operatorName);}
