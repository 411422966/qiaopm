package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ServiceMaterialDeductLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ServiceMaterialDeductLogMapper extends BaseMapper<ServiceMaterialDeductLog> {

    @Select("SELECT * FROM biz_service_material_deduct_log "
            + "WHERE deleted = 0 "
            + "AND (#{orderId} IS NULL OR order_id = #{orderId}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "ORDER BY deduct_time DESC")
    List<ServiceMaterialDeductLog> queryList(@Param("orderId") Long orderId,
                                             @Param("status") String status,
                                             @Param("storeId") Long storeId);

    @Select("SELECT "
            + "COUNT(*) AS total_count, "
            + "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_count, "
            + "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_count, "
            + "SUM(CASE WHEN status = 'partial' THEN 1 ELSE 0 END) AS partial_count, "
            + "SUM(CASE WHEN is_substitute = 1 THEN 1 ELSE 0 END) AS substitute_count, "
            + "SUM(actual_quantity) AS total_quantity, "
            + "SUM(total_cost) AS total_cost, "
            + "SUM(retry_count) AS total_retry "
            + "FROM biz_service_material_deduct_log "
            + "WHERE deleted = 0 "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND (#{startDate} IS NULL OR DATE(deduct_time) >= #{startDate}) "
            + "AND (#{endDate} IS NULL OR DATE(deduct_time) <= #{endDate})")
    Map<String, Object> statistics(@Param("storeId") Long storeId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    @Select("SELECT "
            + "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS success_rate "
            + "FROM biz_service_material_deduct_log "
            + "WHERE deleted = 0 "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND DATE(deduct_time) = #{date}")
    Double successRate(@Param("storeId") Long storeId, @Param("date") LocalDate date);
}
