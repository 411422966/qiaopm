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

    /**
     * 请假单列表（I186 第 1 批：门店维度由「单值或不过滤」改为「有效门店集合」）。
     *
     * <p>SQL 落在 {@code resources/mapper/LeaveRequestMapper.xml}（落法 B2）。
     * storeIds 为 null 表示「不限制」（ALL 范围）；非空时按 IN 过滤
     * （空范围传 {@code [-9999]} → 恒空集）。</p>
     */
    List<Map<String, Object>> queryRequests(@Param("tenantId") Long tenantId,
                                           @Param("storeIds") List<Long> storeIds,
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
