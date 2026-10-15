package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.IotDeviceAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IotDeviceAlertMapper extends BaseMapper<IotDeviceAlert> {

    @Select("SELECT * FROM biz_iot_device_alert "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{deviceId} IS NULL OR device_id = #{deviceId}) "
            + "AND (#{alertLevel} IS NULL OR alert_level = #{alertLevel}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "ORDER BY alert_time DESC")
    List<IotDeviceAlert> queryList(@Param("tenantId") Long tenantId,
                                   @Param("deviceId") Long deviceId,
                                   @Param("alertLevel") String alertLevel,
                                   @Param("status") String status,
                                   @Param("storeId") Long storeId);
}