package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CommissionTier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommissionTierMapper extends BaseMapper<CommissionTier> {

    @Select("SELECT * FROM biz_commission_tier WHERE rule_id = #{ruleId} AND deleted = 0 ORDER BY sort_order ASC")
    List<CommissionTier> findByRuleId(@Param("ruleId") Long ruleId);
}
