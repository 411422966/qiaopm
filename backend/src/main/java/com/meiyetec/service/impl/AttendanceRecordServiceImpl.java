package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;import com.meiyetec.entity.AttendanceConfig;
import com.meiyetec.entity.AttendanceRecord;
import com.meiyetec.entity.Employee;
import com.meiyetec.mapper.AttendanceConfigMapper;
import com.meiyetec.mapper.AttendanceRecordMapper;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.service.AttendanceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl extends ServiceImpl<AttendanceRecordMapper, AttendanceRecord>
        implements AttendanceRecordService {

    private final AttendanceConfigMapper attendanceConfigMapper;
    private final EmployeeMapper employeeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRecord checkIn(Long tenantId, Long employeeId, String method, String location, Long storeId) {
        AttendanceConfig config = getConfig(tenantId, storeId);
        LocalDate checkDate = LocalDate.now();
        AttendanceRecord existing = baseMapper.findByEmployeeAndDate(tenantId, employeeId, checkDate);
        if (existing != null) {
            return existing;
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setTenantId(tenantId);
        record.setEmployeeId(employeeId);
        record.setStoreId(storeId);
        record.setCheckDate(checkDate);
        record.setCheckInTime(LocalDateTime.now());
        record.setCheckInMethod(method);
        record.setCheckInLocation(location);
        record.setCheckInStatus(determineCheckInStatus(config));
        record.setIsLate("late".equals(record.getCheckInStatus()) ? 1 : 0);
        baseMapper.insert(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRecord checkOut(Long tenantId, Long employeeId, String method, String location) {
        LocalDate checkDate = LocalDate.now();
        AttendanceRecord record = baseMapper.findByEmployeeAndDate(tenantId, employeeId, checkDate);
        if (record == null) {
            return null;
        }
        AttendanceConfig config = getConfig(tenantId, record.getStoreId());
        record.setCheckOutTime(LocalDateTime.now());
        record.setCheckOutMethod(method);
        record.setCheckOutLocation(location);
        record.setCheckOutStatus(determineCheckOutStatus(config));
        record.setIsEarlyLeave("early".equals(record.getCheckOutStatus()) ? 1 : 0);
        baseMapper.updateById(record);
        return record;
    }

    @Override
    public AttendanceRecord getByEmployeeAndDate(Long tenantId, Long employeeId, LocalDate date) {
        return baseMapper.findByEmployeeAndDate(tenantId, employeeId, date);
    }

    @Override
    public List<AttendanceRecord> getRecords(Long tenantId, Long employeeId, Long storeId, LocalDate startDate, LocalDate endDate) {
        return baseMapper.findByStoreAndDateRange(tenantId, storeId, startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> queryRecords(Long tenantId, Long employeeId, Long storeId, LocalDate startDate, LocalDate endDate) {
        return baseMapper.queryRecords(tenantId, storeId, employeeId, startDate, endDate);
    }

    @Override
    public Map<String, Object> getStatistics(Long tenantId, Long storeId, LocalDate startDate, LocalDate endDate) {
        List<AttendanceRecord> records = baseMapper.findByStoreAndDateRange(tenantId, storeId, startDate, endDate);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", records.size());
        stats.put("normalCount", records.stream().filter(r -> "normal".equals(r.getCheckInStatus()) && "normal".equals(r.getCheckOutStatus())).count());
        stats.put("lateCount", records.stream().filter(r -> r.getIsLate() != null && r.getIsLate() == 1).count());
        stats.put("earlyCount", records.stream().filter(r -> r.getIsEarlyLeave() != null && r.getIsEarlyLeave() == 1).count());
        stats.put("absenceCount", records.stream().filter(r -> r.getIsAbsence() != null && r.getIsAbsence() == 1).count());
        double avgWorkMinutes = records.stream()
                .filter(r -> r.getWorkMinutes() != null)
                .mapToLong(AttendanceRecord::getWorkMinutes)
                .average()
                .orElse(0.0);
        stats.put("avgWorkMinutes", avgWorkMinutes);
        return stats;
    }

    @Override
    public Map<String, Object> getMonthlyReport(Long tenantId, Long employeeId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        LambdaQueryWrapper<AttendanceRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(AttendanceRecord::getTenantId, tenantId);
        qw.eq(AttendanceRecord::getEmployeeId, employeeId);
        qw.between(AttendanceRecord::getCheckDate, start, end);
        List<AttendanceRecord> records = baseMapper.selectList(qw);
        Map<String, Object> report = new HashMap<>();
        report.put("year", year);
        report.put("month", month);
        report.put("total", records.size());
        report.put("lateCount", records.stream().filter(r -> r.getIsLate() != null && r.getIsLate() == 1).count());
        report.put("earlyCount", records.stream().filter(r -> r.getIsEarlyLeave() != null && r.getIsEarlyLeave() == 1).count());
        long totalMin = records.stream().filter(r -> r.getWorkMinutes() != null).mapToLong(AttendanceRecord::getWorkMinutes).sum();
        report.put("totalWorkMinutes", totalMin);
        return report;
    }

    private AttendanceConfig getConfig(Long tenantId, Long storeId) {
        if (storeId == null || tenantId == null) return null;
        LambdaQueryWrapper<AttendanceConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(AttendanceConfig::getTenantId, tenantId);
        qw.eq(AttendanceConfig::getStoreId, storeId);
        return attendanceConfigMapper.selectOne(qw);
    }

    private String determineCheckInStatus(AttendanceConfig config) {
        java.time.LocalTime standard = java.time.LocalTime.of(9, 0);
        if (config != null && config.getWorkStartTime() != null) {
            standard = config.getWorkStartTime();
        }
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.isAfter(standard.plusMinutes(5)) ? "late" : "normal";
    }

    private String determineCheckOutStatus(AttendanceConfig config) {
        java.time.LocalTime standard = java.time.LocalTime.of(18, 0);
        if (config != null && config.getWorkEndTime() != null) {
            standard = config.getWorkEndTime();
        }
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.isBefore(standard.minusMinutes(5)) ? "early" : "normal";
    }
}