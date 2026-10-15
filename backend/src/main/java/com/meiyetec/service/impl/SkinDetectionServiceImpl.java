package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.SkinDetection;
import com.meiyetec.mapper.SkinDetectionMapper;
import com.meiyetec.service.SkinDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class SkinDetectionServiceImpl extends ServiceImpl<SkinDetectionMapper, SkinDetection>
        implements SkinDetectionService {
    private final SkinDetectionMapper skinDetectionMapper;
    @Override
    public List<SkinDetection> queryList(Long tenantId, Long memberId, Long storeId, String skinType) {
        return skinDetectionMapper.queryList(tenantId, memberId, storeId, skinType);
    }
    @Override
    public SkinDetection findLatestByMember(Long tenantId, Long memberId) {
        return skinDetectionMapper.findLatestByMember(tenantId, memberId);
    }
    @Override
    public List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId) {
        return skinDetectionMapper.statisticsByType(tenantId, storeId);
    }
}