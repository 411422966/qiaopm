package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CareSuggestionLog;
import java.util.List;
public interface CareSuggestionLogService extends IService<CareSuggestionLog> {    List<CareSuggestionLog> queryList(Long memberId, String status, String suggestionType);    
/**    
 * 更新建议状态     
*/    boolean updateStatus(Long id, String status);    
/**    
 * 标记为已采纳     
*/    boolean markAccepted(Long id);    
/**    
 * 标记为已查看     
*/    boolean markViewed(Long id);    
/**    
 * 标记为已拒绝     
*/    boolean markRejected(Long id);}
