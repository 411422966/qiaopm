package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.HairDetection;
import java.util.List;
import java.util.Map;
public interface HairDetectionService extends IService<HairDetection> {
    List<HairDetection> queryList(Long tenantId, Long memberId, Long storeId, String hairType);
    HairDetection findLatestByMember(Long tenantId, Long memberId);
    List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId);
}