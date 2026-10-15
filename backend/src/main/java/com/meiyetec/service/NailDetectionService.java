package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.NailDetection;
import java.util.List;
import java.util.Map;
public interface NailDetectionService extends IService<NailDetection> {
    List<NailDetection> queryList(Long tenantId, Long memberId, Long storeId, String nailType);
    NailDetection findLatestByMember(Long tenantId, Long memberId);
    List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId);
}