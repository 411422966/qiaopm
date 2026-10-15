package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {

    @Select("SELECT * FROM attendance_leave_request "
            + "WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0 ORDER BY create_time DESC")
    List<LeaveRequest> findByEmployeeId(@Param("tenantId") Long tenantId,
                                        @Param("employeeId") Long employeeId);

    @Select("SELECT * FROM attendance_leave_request "
            + "WHERE tenant_id = #{tenantId} AND store_id = #{storeId} AND deleted = 0 ORDER BY create_time DESC")
    List<LeaveRequest> findByStoreId(@Param("tenantId") Long tenantId,
                                     @Param("storeId") Long storeId);

    @Select("SELECT lr.*, pe.employee_name AS employeeName, pe.position, ps.store_name AS storeName, "
            + "pa.employee_name AS approverName "
            + "FROM attendance_leave_request lr "
            + "LEFT JOIN pos_employee pe ON lr.employee_id = pe.id "
            + "LEFT JOIN pos_store ps ON lr.store_id = ps.id "
            + "LEFT JOIN pos_employee pa ON lr.approver_id = pa.id "
            + "WHERE lr.deleted = 0 AND lr.tenant_id = #{tenantId} "
            + "AND (#{storeId} IS NULL OR lr.store_id = #{storeId}) "
            + "AND (#{employeeId} IS NULL OR lr.employee_id = #{employeeId}) "
            + "AND (#{status} IS NULL OR lr.status = #{status}) "
            + "AND (#{startDate} IS NULL OR lr.start_date >= #{startDate}) "
            + "AND (#{endDate} IS NULL OR lr.end_date <= #{endDate}) "
            + "ORDER BY lr.create_time DESC")
    List<Map<String, Object>> queryRequests(@Param("tenantId") Long tenantId,
                                           @Param("storeId") Long storeId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("status") String status,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    @Select("SELECT COUNT(*) FROM attendance_leave_request "
            + "WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND status = 'approved' "
            + "AND ((start_date <= #{endDate} AND end_date >= #{startDate})) "
            + "AND deleted = 0")
    Integer countOverlapLeave(@Param("tenantId") Long tenantId,
                              @Param("employeeId") Long employeeId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);
}