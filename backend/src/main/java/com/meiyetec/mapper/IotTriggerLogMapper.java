package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.IotTriggerLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IotTriggerLogMapper extends BaseMapper<IotTriggerLog> {

    @Select("SELECT * FROM biz_iot_trigger_log "
            + "WHERE deleted = 0 "
            + "AND (#{ruleId} IS NULL OR rule_id = #{ruleId}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "ORDER BY trigger_time DESC LIMIT 200")
    List<IotTriggerLog> queryList(@Param("ruleId") Long ruleId,
                                  @Param("status") String status);

    @Update("UPDATE biz_iot_trigger_rule SET trigger_count = trigger_count + 1, last_trigger_time = NOW() WHERE id = #{id} AND deleted = 0")
    void incrementRuleTriggerCount(@Param("id") Long id);
}
