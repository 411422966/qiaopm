package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.IotTriggerRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IotTriggerRuleMapper extends BaseMapper<IotTriggerRule> {

    @Select("SELECT * FROM biz_iot_trigger_rule "
            + "WHERE deleted = 0 AND enabled = 1 "
            + "AND (#{deviceType} IS NULL OR device_type = #{deviceType}) "
            + "AND (#{dataType} IS NULL OR data_type = #{dataType}) "
            + "ORDER BY priority DESC")
    List<IotTriggerRule> findActive(@Param("deviceType") String deviceType,
                                    @Param("dataType") String dataType);

    @Update("UPDATE biz_iot_trigger_rule SET trigger_count = trigger_count + 1, "
            + "last_trigger_time = NOW() WHERE id = #{ruleId}")
    int incrementRuleTriggerCount(@Param("ruleId") Long ruleId);
}
