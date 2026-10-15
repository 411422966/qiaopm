package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.IotDeviceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface IotDeviceDataMapper extends BaseMapper<IotDeviceData> {

    @Select("SELECT * FROM biz_iot_device_data " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{deviceId} IS NULL OR device_id = #{deviceId}) " +
            "AND (#{memberId} IS NULL OR member_id = #{memberId}) " +
            "AND (#{dataType} IS NULL OR data_type = #{dataType}) " +
            "AND (#{startTime} IS NULL OR collect_time >= #{startTime}) " +
            "AND (#{endTime} IS NULL OR collect_time <= #{endTime}) " +
            "ORDER BY collect_time DESC")
    List<IotDeviceData> queryList(@Param("tenantId") Long tenantId,
                                  @Param("deviceId") Long deviceId,
                                  @Param("memberId") Long memberId,
                                  @Param("dataType") String dataType,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM biz_iot_device_data " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} " +
            "AND data_type IN ('detection','treatment') " +
            "ORDER BY collect_time DESC LIMIT 20")
    List<IotDeviceData> recentByMember(@Param("tenantId") Long tenantId,
                                       @Param("memberId") Long memberId);

    @Select("SELECT DATE(collect_time) AS date, " +
            "AVG(primary_metric) AS avg_primary, " +
            "AVG(secondary_metric) AS avg_secondary, " +
            "COUNT(*) AS report_count " +
            "FROM biz_iot_device_data " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} " +
            "AND data_type = #{dataType} " +
            "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(collect_time) " +
            "ORDER BY date ASC")
    List<Map<String, Object>> trendByMember(@Param("tenantId") Long tenantId,
                                           @Param("memberId") Long memberId,
                                           @Param("dataType") String dataType,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
}