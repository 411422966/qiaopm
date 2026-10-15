package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.NailDetection;
import com.meiyetec.mapper.NailDetectionMapper;
import com.meiyetec.service.NailDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class NailDetectionServiceImpl extends ServiceImpl<NailDetectionMapper, NailDetection>
        implements NailDetectionService {
    private final NailDetectionMapper nailDetectionMapper;
    @Override
    public List<NailDetection> queryList(Long tenantId, Long memberId, Long storeId, String nailType) {
        return nailDetectionMapper.queryList(tenantId, memberId, storeId, nailType);
    }
    @Override
    public NailDetection findLatestByMember(Long tenantId, Long memberId) {
        return nailDetectionMapper.findLatestByMember(tenantId, memberId);
    }
    @Override
    public List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId) {
        return nailDetectionMapper.statisticsByType(tenantId, storeId);
    }
}