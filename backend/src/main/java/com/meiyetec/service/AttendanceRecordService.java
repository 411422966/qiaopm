package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface AttendanceRecordService extends IService<AttendanceRecord> {
    /**
     * 上班打卡
     */
    AttendanceRecord checkIn(Long tenantId, Long employeeId, String method, String location, Long storeId);
    /**
     * 下班打卡
     */
    AttendanceRecord checkOut(Long tenantId, Long employeeId, String method, String location);
    /**
     * 获取员工某日打卡记录
     */
    AttendanceRecord getByEmployeeAndDate(Long tenantId, Long employeeId, LocalDate date);
    /**
     * 获取员工打卡记录列表
     */
    List<AttendanceRecord> getRecords(Long tenantId, Long employeeId, Long storeId, LocalDate startDate, LocalDate endDate);
    /**
     * 获取带员工信息的打卡记录
     */
    List<Map<String, Object>> queryRecords(Long tenantId, Long employeeId, Long storeId, LocalDate startDate, LocalDate endDate);
    /**
     * 获取考勤统计
     */
    Map<String, Object> getStatistics(Long tenantId, Long storeId, LocalDate startDate, LocalDate endDate);
    /**
     * 分页查询打卡记录（I186 第 1 批：门店范围注入下沉到 Service 层）。
     *
     * <p>storeId 缺省时按当前会话的有效门店集合过滤；显式传入且越权 → 403（Service 层纵深防御）。</p>
     */
    Page<AttendanceRecord> pageRecords(Long tenantId, Long employeeId, Long storeId,
                                       LocalDate startDate, LocalDate endDate,
                                       int pageNum, int pageSize);
    /**
     * 获取员工考勤月报
     */
    Map<String, Object> getMonthlyReport(Long tenantId, Long employeeId, int year, int month);
}