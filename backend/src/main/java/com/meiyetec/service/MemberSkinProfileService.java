package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.MemberSkinProfile;
public interface MemberSkinProfileService extends IService<MemberSkinProfile> {    
/**    
 * 获取客户最新肌肤档案     
*/    MemberSkinProfile getLatestByMember(Long memberId);    
/**    
 * 保存或更新肌肤档案     
*/    boolean saveOrUpdate(MemberSkinProfile profile);}
