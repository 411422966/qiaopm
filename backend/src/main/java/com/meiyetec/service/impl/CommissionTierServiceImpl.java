package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CommissionTier;
import com.meiyetec.mapper.CommissionTierMapper;
import com.meiyetec.service.CommissionTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionTierServiceImpl extends ServiceImpl<CommissionTierMapper, CommissionTier>
        implements CommissionTierService {

    private final CommissionTierMapper commissionTierMapper;

    @Override
    public List<CommissionTier> getTiersByRuleId(Long ruleId) {
        return commissionTierMapper.findByRuleId(ruleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveTiers(Long ruleId, List<CommissionTier> tiers) {
        // 删除旧阶梯
        LambdaQueryWrapper<CommissionTier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommissionTier::getRuleId, ruleId);
        remove(wrapper);
        if (tiers == null || tiers.isEmpty()) {
            return true;
        }
        List<CommissionTier> toSave = new ArrayList<>();
        for (CommissionTier tier : tiers) {
            tier.setId(null);
            tier.setRuleId(ruleId);
            toSave.add(tier);
        }
        return saveBatch(toSave);
    }
}
