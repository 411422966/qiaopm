package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DetectionSolution;
import com.meiyetec.mapper.DetectionSolutionMapper;
import com.meiyetec.service.DetectionSolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class DetectionSolutionServiceImpl extends ServiceImpl<DetectionSolutionMapper, DetectionSolution>
        implements DetectionSolutionService {
    private final DetectionSolutionMapper detectionSolutionMapper;
    @Override
    public List<DetectionSolution> queryList(Long tenantId, String applyType, Integer status) {
        return detectionSolutionMapper.queryList(tenantId, applyType, status);
    }
    @Override
    public List<DetectionSolution> matchByScore(Long tenantId, String applyType, Integer score) {
        return detectionSolutionMapper.matchByScore(tenantId, applyType, score);
    }
}