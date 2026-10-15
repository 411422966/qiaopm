package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.LevelBenefit;
import java.util.List;
import java.util.Map;

public interface LevelBenefitService extends IService<LevelBenefit> {

    List<LevelBenefit> getByLevelId(Long levelId);

    List<Map<String, Object>> getWithLevel(Long levelId);

    boolean batchSave(Long levelId, List<LevelBenefit> benefits, Long tenantId);
}
