package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CrmFollowUpTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrmFollowUpTaskMapper extends BaseMapper<CrmFollowUpTask> {

    @Select("SELECT COUNT(*) FROM crm_follow_up_task WHERE deleted = 0 AND plan_id = #{planId} AND source_id = #{sourceId}")
    Long countByPlanAndSource(Long planId, Long sourceId);
}
