package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MaterialConsumptionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialConsumptionLogMapper extends BaseMapper<MaterialConsumptionLog> {

    @Select("SELECT * FROM biz_material_consumption_log " +
            "WHERE order_id = #{orderId} ORDER BY create_time DESC")
    List<MaterialConsumptionLog> findByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT product_id, product_name, " +
            "SUM(quantity) AS total_quantity, " +
            "SUM(total_cost) AS total_cost, " +
            "COUNT(*) AS consume_count " +
            "FROM biz_material_consumption_log " +
            "WHERE deleted = 0 " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{startDate} IS NULL OR consumption_date >= #{startDate}) " +
            "AND (#{endDate} IS NULL OR consumption_date <= #{endDate}) " +
            "GROUP BY product_id, product_name " +
            "ORDER BY total_quantity DESC")
    List<Map<String, Object>> statisticsByProduct(@Param("storeId") Long storeId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    @Select("SELECT service_id, service_name, " +
            "SUM(total_cost) AS total_cost, " +
            "COUNT(*) AS order_count " +
            "FROM biz_material_consumption_log " +
            "WHERE deleted = 0 " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{startDate} IS NULL OR consumption_date >= #{startDate}) " +
            "AND (#{endDate} IS NULL OR consumption_date <= #{endDate}) " +
            "GROUP BY service_id, service_name " +
            "ORDER BY total_cost DESC")
    List<Map<String, Object>> statisticsByService(@Param("storeId") Long storeId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);
}
