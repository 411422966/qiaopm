package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.SkinDetection;
import java.util.List;
import java.util.Map;
public interface SkinDetectionService extends IService<SkinDetection> {
    List<SkinDetection> queryList(Long tenantId, Long memberId, Long storeId, String skinType);
    SkinDetection findLatestByMember(Long tenantId, Long memberId);
    List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId);
}
