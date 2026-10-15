package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.HairDetection;
import com.meiyetec.mapper.HairDetectionMapper;
import com.meiyetec.service.HairDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class HairDetectionServiceImpl extends ServiceImpl<HairDetectionMapper, HairDetection>
        implements HairDetectionService {
    private final HairDetectionMapper hairDetectionMapper;
    @Override
    public List<HairDetection> queryList(Long tenantId, Long memberId, Long storeId, String hairType) {
        return hairDetectionMapper.queryList(tenantId, memberId, storeId, hairType);
    }
    @Override
    public HairDetection findLatestByMember(Long tenantId, Long memberId) {
        return hairDetectionMapper.findLatestByMember(tenantId, memberId);
    }
    @Override
    public List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId) {
        return hairDetectionMapper.statisticsByType(tenantId, storeId);
    }
}