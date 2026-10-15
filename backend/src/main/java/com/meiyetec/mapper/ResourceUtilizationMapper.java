package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ResourceUtilization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ResourceUtilizationMapper extends BaseMapper<ResourceUtilization> {

    @Select("SELECT * FROM biz_resource_utilization "
            + "WHERE deleted = 0 "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND (#{resourceType} IS NULL OR resource_type = #{resourceType}) "
            + "AND (#{resourceId} IS NULL OR resource_id = #{resourceId}) "
            + "AND (#{startDate} IS NULL OR stat_date >= #{startDate}) "
            + "AND (#{endDate} IS NULL OR stat_date <= #{endDate}) "
            + "ORDER BY stat_date DESC")
    List<ResourceUtilization> queryList(@Param("storeId") Long storeId,
                                        @Param("resourceType") String resourceType,
                                        @Param("resourceId") Long resourceId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Select("SELECT resource_type AS resourceType, resource_id AS resourceId, resource_name AS resourceName, "
            + "AVG(utilization_rate) AS avg_utilization, "
            + "SUM(order_count) AS total_orders, "
            + "SUM(revenue) AS total_revenue "
            + "FROM biz_resource_utilization "
            + "WHERE deleted = 0 AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND stat_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY resource_type, resource_id, resource_name "
            + "ORDER BY avg_utilization DESC")
    List<Map<String, Object>> topUtilization(@Param("storeId") Long storeId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /**
     * 按技师统计某日已完成（含取消/爽约）的 booking 聚合
     * duration_minutes = TIMESTAMPDIFF(MINUTE, start_time, end_time)
     * 仅返回 status IN (3已签到, 4已完成, 5已取消, 6爽约)
     */
    @Select("<script>"
            + "SELECT b.employee_id AS employeeId, b.employee_name AS employeeName, b.status, "
            + "b.room_id AS roomId, b.room_name AS roomName, "
            + "CASE WHEN b.end_time IS NOT NULL AND b.start_time IS NOT NULL "
            + "     THEN TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time) ELSE 0 END AS durationMinutes, "
            + "o.id AS orderId, o.final_amount AS finalAmount "
            + "FROM biz_booking b "
            + "LEFT JOIN pos_order o ON o.booking_id = b.id AND o.status = 2 AND o.deleted = 0 "
            + "WHERE b.deleted = 0 AND b.booking_date = #{statDate} "
            + "AND b.status IN (3, 4, 5, 6) "
            + "<if test='storeId != null'> AND b.store_id = #{storeId} </if>"
            + "<if test='resourceType == \"technician\" and resourceId != null'> AND b.employee_id = #{resourceId} </if>"
            + "<if test='resourceType == \"room\" and resourceId != null'> AND b.room_id = #{resourceId} </if>"
            + "</script>")
    List<Map<String, Object>> statBookingsByResource(@Param("storeId") Long storeId,
                                                     @Param("statDate") LocalDate statDate,
                                                     @Param("resourceType") String resourceType,
                                                     @Param("resourceId") Long resourceId);

    /**
     * 查询双人技师服务中某订单涉及的技师人数
     * 如果 need_dual_technician=1，则该订单视为双人协作，人数=2
     */
    @Select("SELECT COUNT(DISTINCT b2.employee_id) AS techCount "
            + "FROM biz_booking b2 "
            + "WHERE b2.deleted = 0 "
            + "AND b2.booking_date = #{bookingDate} "
            + "AND b2.start_time = #{startTime} "
            + "AND b2.end_time = #{endTime} "
            + "AND b2.status IN (3, 4, 5, 6)")
    Integer countTechsForBooking(@Param("bookingDate") LocalDate bookingDate,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 技师利用率重算：先删除旧记录，再批量插入（UPSERT 逻辑由外层事务处理）
     */
    @Select("DELETE FROM biz_resource_utilization "
            + "WHERE deleted = 0 AND store_id = #{storeId} AND stat_date = #{statDate}")
    void deleteByStoreAndDate(@Param("storeId") Long storeId, @Param("statDate") LocalDate statDate);
}
