package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {

    @Select("SELECT * FROM attendance_record WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND check_date = #{checkDate} AND deleted = 0")
    AttendanceRecord findByEmployeeAndDate(@Param("tenantId") Long tenantId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("checkDate") LocalDate checkDate);

    @Select("SELECT * FROM attendance_record WHERE tenant_id = #{tenantId} AND store_id = #{storeId} AND check_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0 ORDER BY check_date, employee_id")
    List<AttendanceRecord> findByStoreAndDateRange(@Param("tenantId") Long tenantId,
                                                  @Param("storeId") Long storeId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    @Select("SELECT ar.*, pe.employee_name, pe.position, ps.store_name " +
            "FROM attendance_record ar " +
            "LEFT JOIN pos_employee pe ON ar.employee_id = pe.id " +
            "LEFT JOIN pos_store ps ON ar.store_id = ps.id " +
            "WHERE ar.deleted = 0 AND ar.tenant_id = #{tenantId} " +
            "AND (#{storeId} IS NULL OR ar.store_id = #{storeId}) " +
            "AND (#{employeeId} IS NULL OR ar.employee_id = #{employeeId}) " +
            "AND (#{startDate} IS NULL OR ar.check_date >= #{startDate}) " +
            "AND (#{endDate} IS NULL OR ar.check_date <= #{endDate}) " +
            "ORDER BY ar.check_date DESC, ar.employee_id")
    List<Map<String, Object>> queryRecords(@Param("tenantId") Long tenantId,
                                           @Param("storeId") Long storeId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
}