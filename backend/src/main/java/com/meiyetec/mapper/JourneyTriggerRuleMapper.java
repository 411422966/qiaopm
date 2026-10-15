package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.JourneyTriggerRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface JourneyTriggerRuleMapper extends BaseMapper<JourneyTriggerRule> {

    @Update("UPDATE mkt_journey_trigger SET match_count = match_count + 1, " +
            "last_trigger_at = NOW() WHERE id = #{id}")
    int incrementMatch(@Param("id") Long id);

    @Update("UPDATE mkt_journey_trigger SET enter_count = enter_count + 1, " +
            "last_trigger_at = NOW() WHERE id = #{id}")
    int incrementEnter(@Param("id") Long id);

    @Select("SELECT * FROM mkt_journey_trigger WHERE deleted = 0 AND status = 2 " +
            "AND event_type = #{eventType} AND tenant_id = #{tenantId} " +
            "ORDER BY priority ASC, id ASC")
    List<JourneyTriggerRule> selectActiveByEventType(@Param("eventType") String eventType,
                                                      @Param("tenantId") Long tenantId);
}
