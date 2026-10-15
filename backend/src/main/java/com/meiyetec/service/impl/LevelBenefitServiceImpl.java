package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.LevelBenefit;
import com.meiyetec.mapper.LevelBenefitMapper;
import com.meiyetec.service.LevelBenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LevelBenefitServiceImpl extends ServiceImpl<LevelBenefitMapper, LevelBenefit>
        implements LevelBenefitService {

    private final LevelBenefitMapper levelBenefitMapper;

    @Override
    public List<LevelBenefit> getByLevelId(Long levelId) {
        return levelBenefitMapper.findByLevelId(levelId);
    }

    @Override
    public List<Map<String, Object>> getWithLevel(Long levelId) {
        return levelBenefitMapper.findWithLevel(levelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSave(Long levelId, List<LevelBenefit> benefits, Long tenantId) {
        LambdaQueryWrapper<LevelBenefit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LevelBenefit::getLevelId, levelId);
        remove(wrapper);
        if (benefits == null || benefits.isEmpty()) {
            return true;
        }
        List<LevelBenefit> toSave = new ArrayList<>();
        for (LevelBenefit benefit : benefits) {
            benefit.setId(null);
            benefit.setLevelId(levelId);
            benefit.setTenantId(tenantId);
            toSave.add(benefit);
        }
        return saveBatch(toSave);
    }
}

