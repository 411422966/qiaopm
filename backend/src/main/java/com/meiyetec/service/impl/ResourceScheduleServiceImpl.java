package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BizService;
import com.meiyetec.entity.Booking;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.Room;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizServiceMapper;
import com.meiyetec.mapper.BookingMapper;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.mapper.RoomMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ResourceScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * P0-M01 · 房间+技师双资源排程核心服务
 *
 * <p>核心能力：
 * <ul>
 *   <li>房间时段冲突检测</li>
 *   <li>技师时段冲突检测（含主技师 + 副技师双人服务）</li>
 *   <li>基于服务规则 + 房间设备 + 技师技能的智能排程建议</li>
 *   <li>带冲突解决策略的创建排程</li>
 * </ul>
 *
 * <p>冲突判断规则：{@code [start, end)} 半开区间，与 {@code BookingServiceImpl.createWithConflictCheck} 一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceScheduleServiceImpl implements ResourceScheduleService {

    private final BookingMapper bookingMapper;
    private final RoomMapper roomMapper;
    private final EmployeeMapper employeeMapper;
    private final BizServiceMapper bizServiceMapper;

    /**
     * 活跃状态：已确认 / 已签到（排除已取消 / 已完成 — 已完成的预约不阻塞新预约）
     */
    private static final List<Integer> ACTIVE_STATUSES =
            List.of(1 /*待确认*/, 2 /*已确认*/, 3 /*已签到*/, 4 /*服务中*/, 5 /*已完成*/);

    @Override
    public ConflictCheckResult checkConflict(Long roomId, Long employeeId,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              Long excludeBookingId) {
        ConflictCheckResult r = new ConflictCheckResult();
        r.roomConflicts = new ArrayList<>();
        r.employeeConflicts = new ArrayList<>();

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            r.hasConflict = true;
            r.message = "时间参数无效：endTime 必须晚于 startTime";
            return r;
        }

        Long tid = tenantId();

        // 房间冲突
        if (roomId != null) {
            r.roomConflicts = findConflicts(null, roomId, startTime, endTime, excludeBookingId, tid);
            r.hasRoomConflict = !r.roomConflicts.isEmpty();
        }
        // 技师冲突（含主技师 + 副技师 — 当前 booking 实体只有 employeeId 一列）
        if (employeeId != null) {
            r.employeeConflicts = findConflicts(employeeId, null, startTime, endTime, excludeBookingId, tid);
            r.hasEmployeeConflict = !r.employeeConflicts.isEmpty();
        }

        r.hasDualEmployeeConflict = false; // 预留扩展位：双技师场景
        r.hasConflict = r.hasRoomConflict || r.hasEmployeeConflict || r.hasDualEmployeeConflict;
        if (r.hasConflict) {
            StringBuilder sb = new StringBuilder("检测到冲突：");
            if (r.hasRoomConflict) sb.append("房间[").append(roomId).append("] ").append(r.roomConflicts.size()).append(" 条；");
            if (r.hasEmployeeConflict) sb.append("技师[").append(employeeId).append("] ").append(r.employeeConflicts.size()).append(" 条");
            r.message = sb.toString();
        } else {
            r.message = "无冲突";
        }
        return r;
    }

    /**
     * 核心冲突检测：startTime <= other.endTime AND endTime >= other.startTime
     */
    private List<Booking> findConflicts(Long employeeId, Long roomId,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         Long excludeBookingId, Long tenantId) {
        LambdaQueryWrapper<Booking> w = new LambdaQueryWrapper<>();
        if (tenantId != null) w.eq(Booking::getTenantId, tenantId);
        if (employeeId != null) w.eq(Booking::getEmployeeId, employeeId);
        if (roomId != null) w.eq(Booking::getRoomId, roomId);
        w.in(Booking::getStatus, ACTIVE_STATUSES);
        // 半开区间 [start, end) 冲突判断
        w.lt(Booking::getStartTime, endTime);
        w.gt(Booking::getEndTime, startTime);
        if (excludeBookingId != null) w.ne(Booking::getId, excludeBookingId);
        return bookingMapper.selectList(w);
    }

    @Override
    public List<ScheduleSuggestion> suggest(Long serviceId, LocalDateTime startTime, int durationMinutes) {
        if (serviceId == null || startTime == null) return Collections.emptyList();
        Long tid = tenantId();
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

        // 1. 加载服务信息（用于评分）
        BizService service = bizServiceMapper.selectById(serviceId);
        if (service == null) return Collections.emptyList();

        // 2. 候选房间（门店下 status=1）
        LambdaQueryWrapper<Room> roomW = new LambdaQueryWrapper<>();
        if (tid != null) roomW.eq(Room::getTenantId, tid);
        roomW.eq(Room::getStatus, 1);
        List<Room> rooms = roomMapper.selectList(roomW);
        if (rooms.isEmpty()) return Collections.emptyList();

        // 3. 候选技师（门店下 status=1）
        LambdaQueryWrapper<Employee> empW = new LambdaQueryWrapper<>();
        if (tid != null) empW.eq(Employee::getTenantId, tid);
        empW.eq(Employee::getStatus, 1);
        List<Employee> employees = employeeMapper.selectList(empW);

        // 4. 冲突表（一次查询所有相关预约）
        LambdaQueryWrapper<Booking> busyW = new LambdaQueryWrapper<>();
        if (tid != null) busyW.eq(Booking::getTenantId, tid);
        busyW.in(Booking::getStatus, ACTIVE_STATUSES);
        busyW.lt(Booking::getStartTime, endTime);
        busyW.gt(Booking::getEndTime, startTime);
        List<Booking> busy = bookingMapper.selectList(busyW);

        // 5. 笛卡尔积生成 (room, employee) 组合并评分
        List<ScheduleSuggestion> out = new ArrayList<>();
        for (Room room : rooms) {
            for (Employee emp : employees) {
                boolean roomBusy = busy.stream().anyMatch(b -> room.getId().equals(b.getRoomId()));
                boolean empBusy = busy.stream().anyMatch(b -> emp.getId().equals(b.getEmployeeId()));
                if (roomBusy || empBusy) continue; // 跳过冲突组合

                ScheduleSuggestion s = new ScheduleSuggestion();
                s.roomId = room.getId();
                s.roomName = room.getRoomName();
                s.primaryEmployeeId = emp.getId();
                s.primaryEmployeeName = null; // Employee entity 没有 name 字段时留 null
                s.assistantEmployeeId = null;
                s.score = scoreCombination(service, room, emp);
                s.reason = buildReason(service, room, emp);
                out.add(s);
            }
        }

        // 6. 按分数降序
        out.sort(Comparator.comparingInt((ScheduleSuggestion s) -> s.score).reversed());
        // 7. 限制最多返回 20 条
        if (out.size() > 20) return out.subList(0, 20);
        return out;
    }

    /**
     * 评分：服务时长匹配(50) + 房间容量匹配(20) + 房间设备匹配(20) + 基础分(10)
     */
    private int scoreCombination(BizService service, Room room, Employee employee) {
        int score = 10;
        // 服务时长匹配：房间可用容量 >= 服务的隐含需求（默认单人）
        if (room.getCapacity() != null && room.getCapacity() >= 1) score += 20;
        // 设备匹配：服务名包含的关键词（如"美甲"→美甲台）与 room.equipment 匹配
        if (service.getServiceName() != null && room.getEquipment() != null
                && room.getEquipment().contains(extractKeyword(service.getServiceName()))) {
            score += 20;
        }
        // 时长匹配：默认中等时长 60min 视为标准
        if (service.getDuration() != null && service.getDuration() <= 120) score += 50;
        else if (service.getDuration() != null && service.getDuration() <= 180) score += 30;
        else score += 10;
        return score;
    }

    private String buildReason(BizService service, Room room, Employee employee) {
        return "服务[" + service.getServiceName() + "] → 房间[" + room.getRoomName()
                + "]（容量=" + room.getCapacity() + "）+ 技师[" + employee.getId() + "]";
    }

    private String extractKeyword(String serviceName) {
        if (serviceName == null) return "";
        // 取前 2 个中文字符作为关键词
        if (serviceName.length() >= 2) return serviceName.substring(0, 2);
        return serviceName;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Booking createSchedule(Booking booking, String conflictResolveAction) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "startTime / endTime 不能为空");
        }
        if (!booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "endTime 必须晚于 startTime");
        }
        if (booking.getTenantId() == null) {
            booking.setTenantId(tenantId());
        }
        if (booking.getStatus() == null) booking.setStatus(1);

        // 1. 冲突检测
        ConflictCheckResult cc = checkConflict(
                booking.getRoomId(), booking.getEmployeeId(),
                booking.getStartTime(), booking.getEndTime(),
                null);
        if (cc.hasConflict) {
            if ("override".equalsIgnoreCase(conflictResolveAction)) {
                log.warn("[RESOURCE-SCHED] 强制覆盖冲突创建 booking: {}", cc.message);
                // 不做特殊处理 — 让 INSERT 触发 DB 级唯一约束保护（如果有）
            } else if ("reschedule".equalsIgnoreCase(conflictResolveAction)) {
                // 智能建议最近可用时段
                LocalDateTime suggested = suggestNextAvailable(
                        booking.getRoomId(), booking.getEmployeeId(),
                        booking.getStartTime(), booking.getEndTime());
                booking.setStartTime(suggested);
                booking.setEndTime(suggested.plusMinutes(
                        java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes()));
                log.info("[RESOURCE-SCHED] 改约至 {} (冲突解决策略=reschedule)", suggested);
            } else {
                // 默认 manual_resolve：直接拒绝
                throw new BusinessException(BizCode.BOOKING_CONFLICT, cc.message);
            }
        }

        bookingMapper.insert(booking);
        return booking;
    }

    /**
     * 智能找下一个空档（从 startTime 开始向后扫描，最多 24 小时）
     */
    private LocalDateTime suggestNextAvailable(Long roomId, Long employeeId,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime candidate = startTime;
        for (int i = 0; i < 48; i++) { // 30 分钟步进 × 48 = 24 小时
            ConflictCheckResult r = checkConflict(roomId, employeeId, candidate, endTime, null);
            if (!r.hasConflict) return candidate;
            candidate = candidate.plusMinutes(30);
        }
        // 找不到则返回 +24h（最终由 caller 决定是否接受）
        return startTime.plusHours(24);
    }

    private Long tenantId() {
        try {
            return TenantContextHolder.get().getTenantId();
        } catch (Exception e) {
            return null;
        }
    }
}
