package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CommissionCondition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommissionConditionMapper extends BaseMapper<CommissionCondition> {

    @Select("SELECT * FROM biz_commission_condition WHERE rule_id = #{ruleId} AND deleted = 0 ORDER BY sort_order ASC")
    List<CommissionCondition> findByRuleId(@Param("ruleId") Long ruleId);
}
