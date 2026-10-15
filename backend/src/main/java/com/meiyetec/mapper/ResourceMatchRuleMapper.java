package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ResourceMatchRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceMatchRuleMapper extends BaseMapper<ResourceMatchRule> {

    @Select("SELECT * FROM biz_resource_match_rule WHERE deleted = 0 AND service_id = #{serviceId} LIMIT 1")
    ResourceMatchRule findByServiceId(@Param("serviceId") Long serviceId);
}
