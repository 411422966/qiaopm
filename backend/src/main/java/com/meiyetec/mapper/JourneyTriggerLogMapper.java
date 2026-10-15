package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.JourneyTriggerLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface JourneyTriggerLogMapper extends BaseMapper<JourneyTriggerLog> {

    @Select("SELECT * FROM mkt_journey_trigger_log WHERE trigger_id = #{triggerId} " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<JourneyTriggerLog> queryByTrigger(
            @Param("triggerId") Long triggerId,
            @Param("status") String status,
            @Param("limit") int limit);

    @Select("SELECT * FROM mkt_journey_trigger_log WHERE member_id = #{memberId} " +
            "AND trigger_id = #{triggerId} " +
            "AND DATE(create_time) = CURDATE() ORDER BY id DESC LIMIT 1")
    JourneyTriggerLog queryTodayByMember(
            @Param("triggerId") Long triggerId,
            @Param("memberId") Long memberId);
}
