package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ExpiryAlertLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ExpiryAlertLogMapper extends BaseMapper<ExpiryAlertLog> {

    @Select("SELECT * FROM biz_expiry_alert_log WHERE deleted = 0 "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{alertLevel} IS NULL OR alert_level = #{alertLevel}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "ORDER BY FIELD(alert_level,'critical','warning','normal'), days_to_expiry ASC")
    List<ExpiryAlertLog> queryList(@Param("status") String status,
                                   @Param("alertLevel") String alertLevel,
                                   @Param("storeId") Long storeId);

    @Update("UPDATE biz_expiry_alert_log SET status = #{status}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT "
            + "SUM(CASE WHEN days_to_expiry < 0 THEN 1 ELSE 0 END) AS expired_count, "
            + "SUM(CASE WHEN days_to_expiry >= 0 AND days_to_expiry <= 7 THEN 1 ELSE 0 END) AS critical_count, "
            + "SUM(CASE WHEN days_to_expiry > 7 AND days_to_expiry <= 30 THEN 1 ELSE 0 END) AS warning_count, "
            + "SUM(CASE WHEN days_to_expiry > 30 THEN 1 ELSE 0 END) AS normal_count, "
            + "SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending_count "
            + "FROM biz_expiry_alert_log "
            + "WHERE deleted = 0 AND store_id = #{storeId}")
    Map<String, Object> dashboardStatistics(@Param("storeId") Long storeId);
}
