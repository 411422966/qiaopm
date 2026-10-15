package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CrmFollowUpPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CrmFollowUpPlanMapper extends BaseMapper<CrmFollowUpPlan> {

    @Select("SELECT COUNT(*) FROM crm_follow_up_plan WHERE deleted = 0 AND tenant_id = #{tenantId}")
    Long countTotal(Long tenantId);

    @Select("SELECT COUNT(*) FROM crm_follow_up_plan WHERE deleted = 0 AND tenant_id = #{tenantId} AND enabled = 1")
    Long countEnabled(Long tenantId);

    @Select("SELECT COUNT(*) FROM crm_follow_up_trigger_log WHERE tenant_id = #{tenantId} AND DATE(trigger_time) = CURDATE()")
    Long sumTodayGenerated(Long tenantId);

    @Select("SELECT COUNT(*) FROM crm_follow_up_trigger_log WHERE tenant_id = #{tenantId} AND DATE_FORMAT(trigger_time, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')")
    Long sumMonthGenerated(Long tenantId);

    @Select("SELECT DATE(trigger_time) AS dt, COUNT(*) AS cnt FROM crm_follow_up_trigger_log " +
            "WHERE tenant_id = #{tenantId} AND trigger_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(trigger_time) ORDER BY dt ASC")
    List<Map<String, Object>> weekTrend(Long tenantId);

    @Select("SELECT COUNT(*) FROM crm_follow_up_task WHERE deleted = 0 AND tenant_id = #{tenantId} AND plan_id = #{planId} AND source_id = #{sourceId}")
    Long countByPlanAndSourceInTenant(Long tenantId, Long planId, Long sourceId);
}
