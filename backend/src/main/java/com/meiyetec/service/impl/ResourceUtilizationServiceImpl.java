package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.service.ResourceUtilizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源利用率统计服务（M11-04 技师利用率 + 房间占用率）
 *
 * <p>业务口径（严格口径）：
 * <ul>
 *   <li>技师利用率 = 实际服务时长 / 班表工作时长 × 100%</li>
 *   <li>实际服务时长：status=4（已完成）的 booking 时长，多技师协作均摊人头</li>
 *   <li>班表工作时长：biz_employee_schedule.startTime ~ endTime，请假/休息不计入</li>
 *   <li>爽约/取消：记录到 noShowCount / cancelCount，不计入服务时长</li>
 *   <li>房间占用率：已完成 booking 时长 / 营业时长</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceUtilizationServiceImpl extends ServiceImpl<ResourceUtilizationMapper, ResourceUtilization>
        implements ResourceUtilizationService {

    private final ResourceUtilizationMapper resourceUtilizationMapper;
    private final RoomMapper roomMapper;
    private final EmployeeMapper employeeMapper;
    private final BookingMapper bookingMapper;
    private final EmployeeScheduleMapper employeeScheduleMapper;

    @Override
    public List<ResourceUtilization> queryList(Long storeId, String resourceType, Long resourceId,
                                                LocalDate startDate, LocalDate endDate) {
        return resourceUtilizationMapper.queryList(storeId, resourceType, resourceId, startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> topUtilization(Long storeId, LocalDate startDate, LocalDate endDate) {
        return resourceUtilizationMapper.topUtilization(storeId, startDate, endDate);
    }

    /**
     * 重算某日某门店下所有技师 + 房间的利用率统计。
     *
     * <p>口径：
     * <ul>
     *   <li>技师：班表工作时长（扣除请假/休息）= 分母；已完成 booking 时长 = 分子（双人协作均摊人头）</li>
     *   <li>房间：总可用时长（默认 8h）= 分母；已完成 booking 时长 = 分子</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculate(Long storeId, LocalDate date) {
        // 1. 先删旧记录
        resourceUtilizationMapper.deleteByStoreAndDate(storeId, date);

        // 2. 重算技师利用率
        recalcTechnicians(storeId, date);

        // 3. 重算房间占用率
        recalcRooms(storeId, date);
    }

    // ─── 技师利用率 ───────────────────────────────────────────────────────────

    private void recalcTechnicians(Long storeId, LocalDate date) {
        // 查询在职技师
        List<Employee> technicians = employeeMapper.selectList(
                new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getDeleted, 0)
                        .eq(Employee::getStatus, 1)
                        .eq(storeId != null, Employee::getStoreId, storeId));

        // 批量查班表（减少 N+1）
        Set<Long> techIds = technicians.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, EmployeeSchedule> scheduleMap = new HashMap<>();
        if (!techIds.isEmpty()) {
            List<EmployeeSchedule> schedules = employeeScheduleMapper.selectList(
                    new LambdaQueryWrapper<EmployeeSchedule>()
                            .eq(EmployeeSchedule::getDeleted, 0)
                            .eq(EmployeeSchedule::getWorkDate, date)
                            .in(EmployeeSchedule::getEmployeeId, techIds));
            for (EmployeeSchedule s : schedules) {
                scheduleMap.put(s.getEmployeeId(), s);
            }
        }

        // 批量查当日 booking 聚合
        List<Map<String, Object>> bookingStats = resourceUtilizationMapper
                .statBookingsByResource(storeId, date, "technician", null);

        // 按技师分组
        Map<Long, List<Map<String, Object>>> byTech = bookingStats.stream()
                .filter(m -> m.get("employeeId") != null)
                .collect(Collectors.groupingBy(m -> ((Number) m.get("employeeId")).longValue()));

        // 批量统计每技师的爽约/取消数（从 bookingStats 再算一次，减少查询）
        Map<Long, Integer> noShowCountMap = computeNoShowCount(byTech);
        Map<Long, Integer> cancelCountMap = computeCancelCount(byTech);

        for (Employee tech : technicians) {
            EmployeeSchedule schedule = scheduleMap.get(tech.getId());

            // 跳过：当天无排班 或 请假/休息
            if (schedule == null || schedule.getStatus() == null
                    || schedule.getStatus() == 2   // 请假
                    || schedule.getStatus() == 3) { // 休息
                // 无排班技师：记录 0 利用率（不丢弃，留历史）
                saveZeroRecord(storeId, "technician", tech.getId(), tech.getEmployeeName(), date);
                continue;
            }

            // 分母：班次工作时长（分钟）
            int totalWorkMinutes = computeWorkMinutes(schedule);

            // 分子：从 booking 聚合（已完成 = status=4）
            List<Map<String, Object>> techBookings = byTech.getOrDefault(tech.getId(), Collections.emptyList());

            int actualServiceMinutes = 0;
            int orderCount = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            Set<Long> countedBookingIds = new HashSet<>();

            for (Map<String, Object> b : techBookings) {
                Integer status = ((Number) b.get("status")).intValue();
                Long bookingId = b.get("orderId") != null ? ((Number) b.get("orderId")).longValue() : null;
                if (bookingId != null && countedBookingIds.contains(bookingId)) {
                    continue; // 同一订单有多个技师只算一次
                }
                int dur = ((Number) b.get("durationMinutes")).intValue();
                BigDecimal amt = b.get("finalAmount") != null
                        ? new BigDecimal(b.get("finalAmount").toString()) : BigDecimal.ZERO;

                if (status == 4) {
                    // 严格口径：仅已完成（status=4）计入服务时长
                    int techDur = dur; // 默认单人

                    // 双人协作：均摊人头
                    int techCount = resourceUtilizationMapper.countTechsForBooking(
                            date,
                            b.get("startTime") != null ? LocalDateTime.parse(b.get("startTime").toString()) : null,
                            b.get("endTime") != null ? LocalDateTime.parse(b.get("endTime").toString()) : null);
                    if (techCount > 1) {
                        techDur = dur / techCount;
                    }

                    actualServiceMinutes += techDur;
                    revenue = revenue.add(amt);
                    countedBookingIds.add(bookingId);
                }

                if (status == 4) {
                    orderCount++;
                }
            }

            // 利用率
            BigDecimal utilRate = totalWorkMinutes > 0
                    ? BigDecimal.valueOf(actualServiceMinutes)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalWorkMinutes), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            saveRecord(storeId, "technician", tech.getId(), tech.getEmployeeName(), date,
                    totalWorkMinutes, 0, actualServiceMinutes,
                    totalWorkMinutes - actualServiceMinutes,
                    orderCount,
                    noShowCountMap.getOrDefault(tech.getId(), 0),
                    cancelCountMap.getOrDefault(tech.getId(), 0),
                    revenue, utilRate);
        }
    }

    // ─── 房间占用率 ───────────────────────────────────────────────────────────

    private void recalcRooms(Long storeId, LocalDate date) {
        List<Room> rooms = roomMapper.selectList(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getDeleted, 0)
                        .eq(storeId != null, Room::getStoreId, storeId));

        // 批量查当日 booking 聚合
        List<Map<String, Object>> bookingStats = resourceUtilizationMapper
                .statBookingsByResource(storeId, date, "room", null);

        Map<Long, List<Map<String, Object>>> byRoom = bookingStats.stream()
                .filter(m -> m.get("roomId") != null)
                .collect(Collectors.groupingBy(m -> ((Number) m.get("roomId")).longValue()));

        Map<Long, Integer> noShowCountMap = computeNoShowCount(byRoom);
        Map<Long, Integer> cancelCountMap = computeCancelCount(byRoom);

        for (Room room : rooms) {
            List<Map<String, Object>> roomBookings = byRoom.getOrDefault(room.getId(), Collections.emptyList());

            int bookedMinutes = 0;    // 含所有状态的预约时长
            int actualMinutes = 0;    // 仅已完成
            int orderCount = 0;
            BigDecimal revenue = BigDecimal.ZERO;

            for (Map<String, Object> b : roomBookings) {
                int dur = ((Number) b.get("durationMinutes")).intValue();
                BigDecimal amt = b.get("finalAmount") != null
                        ? new BigDecimal(b.get("finalAmount").toString()) : BigDecimal.ZERO;
                int status = ((Number) b.get("status")).intValue();

                bookedMinutes += dur;

                if (status == 4) {
                    actualMinutes += dur;
                    revenue = revenue.add(amt);
                    orderCount++;
                }
            }

            // 房间默认营业 8h（480 分钟），实际用已完成时长算利用率
            int totalAvailableMinutes = 8 * 60;
            int idleMinutes = Math.max(0, totalAvailableMinutes - actualMinutes);

            BigDecimal utilRate = totalAvailableMinutes > 0
                    ? BigDecimal.valueOf(actualMinutes)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalAvailableMinutes), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            saveRecord(storeId, "room", room.getId(), room.getRoomName(), date,
                    totalAvailableMinutes, bookedMinutes, actualMinutes, idleMinutes,
                    orderCount,
                    noShowCountMap.getOrDefault(room.getId(), 0),
                    cancelCountMap.getOrDefault(room.getId(), 0),
                    revenue, utilRate);
        }
    }

    // ─── 工具方法 ────────────────────────────────────────────────────────────

    /**
     * 计算班次工作时长（分钟）
     * shiftType: 1=早班 2=中班 3=晚班 4=全天
     * 如 startTime/endTime 均为 null，按 shiftType 估算
     */
    private int computeWorkMinutes(EmployeeSchedule schedule) {
        if (schedule.getStartTime() != null && schedule.getEndTime() != null) {
            return (int) Duration.between(schedule.getStartTime(), schedule.getEndTime()).toMinutes();
        }
        // 无精确时间，按班次类型估算
        switch (schedule.getShiftType() == null ? 4 : schedule.getShiftType()) {
            case 1: return 8 * 60;          // 早班 8h
            case 2: return 8 * 60;          // 中班 8h
            case 3: return 6 * 60;          // 晚班 6h
            default: return 8 * 60;         // 全天 8h
        }
    }

    private Map<Long, Integer> computeNoShowCount(Map<Long, List<Map<String, Object>>> byResource) {
        Map<Long, Integer> result = new HashMap<>();
        byResource.forEach((rid, bookings) -> {
            int noShow = 0;
            int cancel = 0;
            for (Map<String, Object> b : bookings) {
                int s = ((Number) b.get("status")).intValue();
                if (s == 6) noShow++;
                if (s == 5) cancel++;
            }
            result.put(rid, noShow);
        });
        return result;
    }

    private Map<Long, Integer> computeCancelCount(Map<Long, List<Map<String, Object>>> byResource) {
        Map<Long, Integer> result = new HashMap<>();
        byResource.forEach((rid, bookings) -> {
            int cancel = 0;
            for (Map<String, Object> b : bookings) {
                int s = ((Number) b.get("status")).intValue();
                if (s == 5) cancel++;
            }
            result.put(rid, cancel);
        });
        return result;
    }

    private void saveRecord(Long storeId, String resourceType, Long resourceId, String resourceName,
                            LocalDate statDate,
                            int totalAvailable, int booked, int actual,
                            int idle, int orderCount,
                            int noShow, int cancel,
                            BigDecimal revenue, BigDecimal utilRate) {
        ResourceUtilization u = new ResourceUtilization();
        u.setStoreId(storeId);
        u.setResourceType(resourceType);
        u.setResourceId(resourceId);
        u.setResourceName(resourceName);
        u.setStatDate(statDate);
        u.setTotalAvailableMinutes(totalAvailable);
        u.setBookedMinutes(booked);
        u.setActualServiceMinutes(actual);
        u.setIdleMinutes(idle);
        u.setOrderCount(orderCount);
        u.setNoShowCount(noShow);
        u.setCancelCount(cancel);
        u.setRevenue(revenue);
        u.setUtilizationRate(utilRate);
        save(u);
    }

    private void saveZeroRecord(Long storeId, String resourceType, Long resourceId,
                                 String resourceName, LocalDate statDate) {
        saveRecord(storeId, resourceType, resourceId, resourceName, statDate,
                0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

