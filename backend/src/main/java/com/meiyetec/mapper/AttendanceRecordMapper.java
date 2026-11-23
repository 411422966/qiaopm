package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /**
     * 按门店集合 + 日期区间查询考勤记录（I186 第 1 批：单值 storeId 改为集合）。
     *
     * <p>SQL 落在 {@code resources/mapper/AttendanceRecordMapper.xml}（落法 B2）。
     * storeIds 为 null 表示「不限制」（ALL 范围）；非空时按 IN 过滤
     * （空范围传 {@code [-9999]} → 恒空集）。</p>
     */
    List<AttendanceRecord> findByStoresAndDateRange(@Param("tenantId") Long tenantId,
                                                     @Param("storeIds") List<Long> storeIds,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    /**
     * 考勤记录列表（I186 第 1 批：门店维度由「单值或不过滤」改为「有效门店集合」）。
     *
     * <p>SQL 落在 {@code resources/mapper/AttendanceRecordMapper.xml}（落法 B2）。</p>
     */
    List<Map<String, Object>> queryRecords(@Param("tenantId") Long tenantId,
                                           @Param("storeIds") List<Long> storeIds,
                                           @Param("employeeId") Long employeeId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 考勤记录分页查询（I194-0）。
     *
     * <p>与 {@link #queryRecords} 的三点差别：</p>
     * <ol>
     *   <li>返回实体 {@link AttendanceRecord} → 前端拿到的是 camelCase 字段
     *       （{@code employeeName} / {@code checkDate}），不再出现 Map 返回的 snake_case 漂移；</li>
     *   <li>SQL 中 LEFT JOIN {@code pos_employee} / {@code pos_store}
     *       → {@code employeeName} / {@code position} / {@code storeName} 有值（原先恒为 null）；</li>
     *   <li>首参为 {@link IPage}，配合 MyBatis-Plus 分页插件做分页与总数统计。</li>
     * </ol>
     *
     * <p>storeIds 语义与 {@code StoreScope.resolveIds()} 一致（三种语义已实测等价）：</p>
     * <ul>
     *   <li>空列表 → 不拼门店条件（数据范围 = ALL）；</li>
     *   <li>{@code [-9999]} → {@code IN (-9999)} 恒空集（可见门店为空的受限范围）；</li>
     *   <li>其余 → {@code IN} 可见门店集合。</li>
     * </ul>
     */
    Page<AttendanceRecord> selectAttendancePage(IPage<AttendanceRecord> page,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("storeIds") List<Long> storeIds,
                                                 @Param("employeeId") Long employeeId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);
}
