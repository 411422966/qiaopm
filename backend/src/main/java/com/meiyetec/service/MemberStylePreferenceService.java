package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.MemberStylePreference;
public interface MemberStylePreferenceService extends IService<MemberStylePreference> {    
/**    
 * 获取或创建客户偏好     
*/    MemberStylePreference getOrCreate(Long memberId);    
/**    
 * 记录浏览款式     
*/    void recordBrowse(Long memberId, Long styleId);    
/**    
 * 切换收藏     
*/    boolean toggleFavorite(Long memberId, Long styleId);}
