package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DetectionSolution;
import java.util.List;
public interface DetectionSolutionService extends IService<DetectionSolution> {
    List<DetectionSolution> queryList(Long tenantId, String applyType, Integer status);
    /**
     * 根据分数与条件匹配方案
     */
    List<DetectionSolution> matchByScore(Long tenantId, String applyType, Integer score);
}