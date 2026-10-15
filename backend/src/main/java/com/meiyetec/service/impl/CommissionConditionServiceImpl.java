package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CommissionCondition;
import com.meiyetec.mapper.CommissionConditionMapper;
import com.meiyetec.service.CommissionConditionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
@Service
@RequiredArgsConstructor
public class CommissionConditionServiceImpl extends ServiceImpl<CommissionConditionMapper, CommissionCondition>        implements CommissionConditionService {    
private final CommissionConditionMapper commissionConditionMapper;    
@Override    
public List<CommissionCondition> getConditionsByRuleId(Long ruleId) {        return commissionConditionMapper.findByRuleId(ruleId);    }    
@Override    
@Transactional(rollbackFor = Exception.class)
public boolean batchSaveConditions(Long ruleId, List<CommissionCondition> conditions) {        LambdaQueryWrapper<CommissionCondition> wrapper = new LambdaQueryWrapper<>();        wrapper.eq(CommissionCondition::getRuleId, ruleId);        remove(wrapper);        if (conditions == null || conditions.isEmpty()) {            return true;        }        List<CommissionCondition> toSave = new ArrayList<>();        for (CommissionCondition condition : conditions) {            condition.setId(null);            condition.setRuleId(ruleId);            toSave.add(condition);        }        return saveBatch(toSave);    }}
