package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.Booking;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.Store;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.EmployeeSchedule;
import com.meiyetec.entity.Room;
import com.meiyetec.mapper.StoreMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BookingService;
import com.meiyetec.service.EmployeeScheduleService;
import com.meiyetec.service.EmployeeService;
import com.meiyetec.service.RoomService;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M06 预约管理控制器
 * - 包含 T06-09 ~ T06-14：创建/列表/可用时间/确认/签到/完成/取消/状态变更
 */
@RestController
@RequestMapping("/booking")
@RequiredArgsConstructor
@SysOpLog(module = "预约")
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final EmployeeScheduleService scheduleService;
    private final EmployeeService employeeService;
    private final RoomService roomService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoreMapper storeMapper;
    private final com.meiyetec.mkt.event.MarketingEventPublisher marketingEventPublisher;

    private static final String BOOKING_NO_PREFIX = "BK";

    /** T06-09 创建预约（POST /booking） */
    @PostMapping
    @RequirePermission("booking:add")
    public Result<Booking> create(@RequestBody Booking b) {
        // T07-D2：Controller 强制从 TenantContextHolder 取租户并注入，Service 不再兜底
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(com.meiyetec.exception.BizCode.UNAUTHORIZED, "未识别租户");
        }
        b.setTenantId(tenantId);

        // P0-3：storeId 必须属于当前租户（防止跨租户预约数据注入）
        if (b.getStoreId() != null) {
            Store store = storeMapper.selectById(b.getStoreId());
            if (store == null || !tenantId.equals(store.getTenantId())) {
                return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "门店不存在或不属于当前租户");
            }
        }

        // P0-TEST-001：把冲突检查 + 插入下沉到 Service（事务内原子完成）
        try {
            Result<Booking> result = bookingService.createWithConflictCheck(b);
            if (result.getCode() == 200 && result.getData() != null) {
                Booking created = result.getData();
                // ===== M16 营销事件发布：预约创建 =====
                try {
                    marketingEventPublisher.bookingCreated(
                            created.getTenantId(), created.getMemberId(),
                            created.getMemberName(), created.getBookingNo(), created.getStoreId());
                } catch (Exception ex) {
                    log.warn("[M16-Event] bookingCreated 发布失败: bookingNo={} err={}",
                            created.getBookingNo(), ex.getMessage());
                }
            }
            return result;
        } catch (com.meiyetec.exception.BusinessException ex) {
            // 把 BizCode 转 Result，前端可识别 code
            return Result.error(ex.getCode(), ex.getMessage());
        }
    }

    /** T06-10 预约列表（GET /booking/page）支持分页+多条件 */
    @GetMapping("/page")
    @RequirePermission("booking:list")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<Booking> qw = new LambdaQueryWrapper<>();
        if (tenantId != null) qw.eq(Booking::getTenantId, tenantId);
        if (memberId != null) qw.eq(Booking::getMemberId, memberId);
        if (employeeId != null) qw.eq(Booking::getEmployeeId, employeeId);
        if (status != null) qw.eq(Booking::getStatus, status);
        if (date != null && !date.isEmpty()) {
            LocalDate d = LocalDate.parse(date);
            qw.eq(Booking::getBookingDate, d);
        }
        qw.orderByDesc(Booking::getStartTime);

        IPage<Booking> page = bookingService.page(new Page<>(current, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("current", page.getCurrent());
        data.put("size", page.getSize());
        return Result.success(data);
    }

    /** 列表（不分页，全量）T06-10 简化版 */
    @GetMapping("/list")
    @RequirePermission("booking:list")
    public Result<List<Booking>> list(@RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<Booking> qw = new LambdaQueryWrapper<>();
        if (tenantId != null) qw.eq(Booking::getTenantId, tenantId);
        if (status != null) qw.eq(Booking::getStatus, status);
        qw.orderByDesc(Booking::getStartTime);
        return Result.success(bookingService.list(qw));
    }

    /**
     * P2-13 IND-15：可用时间查询（GET /booking/available）
     *
     * <p>返回某技师某天的 30 分钟精度可预约时间槽。
     * <p>返回格式：{@code ["09:00~09:30", "09:30~10:00", ...]}。
     */
    @GetMapping("/available")
    @RequirePermission("booking:available")
    public Result<List<String>> available(
            @RequestParam Long employeeId,
            @RequestParam String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate d = LocalDate.parse(date);

        // P0-3：先拿技师所属门店，验证属于当前租户（防止跨租户查排班）
        Employee emp = employeeService.getById(employeeId);
        if (emp == null) {
            return Result.success(Collections.emptyList());
        }
        Long storeId = emp.getStoreId();
        if (storeId == null) {
            return Result.success(Collections.emptyList());
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || (tenantId != null && !tenantId.equals(store.getTenantId()))) {
            return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "该技师所属门店不属于当前租户");
        }

        // 1) 找技师排班（按租户隔离）
        LambdaQueryWrapper<EmployeeSchedule> schQ = new LambdaQueryWrapper<EmployeeSchedule>()
                .eq(EmployeeSchedule::getEmployeeId, employeeId)
                .eq(EmployeeSchedule::getWorkDate, d);
        if (tenantId != null) schQ.eq(EmployeeSchedule::getTenantId, tenantId);
        EmployeeSchedule sch = scheduleService.getOne(schQ);
        if (sch == null || sch.getStatus() != 1) {
            return Result.success(Collections.emptyList());
        }
        LocalTime start = sch.getStartTime();
        LocalTime end = sch.getEndTime();
        int max = sch.getMaxBookings() == null ? 16 : sch.getMaxBookings() * 2; // 30-min 翻倍

        // 2) 找当天已有预约（占用时段，按租户隔离）
        LambdaQueryWrapper<Booking> busyQ = new LambdaQueryWrapper<Booking>()
                .eq(Booking::getEmployeeId, employeeId)
                .eq(Booking::getBookingDate, d)
                .in(Booking::getStatus, Arrays.asList(1, 2, 3));
        if (tenantId != null) busyQ.eq(Booking::getTenantId, tenantId);
        List<Booking> busy = bookingService.list(busyQ);

        // 3) P2-13：切成 30 分钟时段
        List<String> slots = new ArrayList<>();
        for (LocalTime t = start; !t.isAfter(end.minusMinutes(30)); t = t.plusMinutes(30)) {
            LocalDateTime slotStart = LocalDateTime.of(d, t);
            LocalDateTime slotEnd = slotStart.plusMinutes(30);
            // 已经被占用？已有预约与此时段有重叠
            boolean taken = busy.stream().anyMatch(b ->
                    b.getStartTime().isBefore(slotEnd) && b.getEndTime().isAfter(slotStart));
            if (!taken) {
                slots.add(t.toString() + "~" + t.plusMinutes(30).toString());
            }
            if (slots.size() >= max) break;
        }
        return Result.success(slots);
    }

    /** T06-12 改变预约状态（PUT /booking/{id}/status）—— 确认/取消/爽约 */
    @PutMapping("/{id:\\d+}/status")
    @RequirePermission("booking:edit")
    public Result<Booking> changeStatus(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam(required = false) String reason) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking b = bookingService.getById(id);
        if (b == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(b.getTenantId())) {
            return Result.error("预约不存在");
        }
        // 状态机校验
        Map<Integer, Set<Integer>> allowed = new HashMap<>();
        allowed.put(1, new HashSet<>(Arrays.asList(2, 5, 6))); // 待确认 → 已确认/取消/爽约
        allowed.put(2, new HashSet<>(Arrays.asList(3, 5, 6))); // 已确认 → 签到/取消/爽约
        allowed.put(3, new HashSet<>(Arrays.asList(4, 5)));    // 已签到 → 完成/取消
        if (!allowed.getOrDefault(b.getStatus(), Collections.emptySet()).contains(status)) {
            return Result.error("状态不合法：当前" + b.getStatus() + " 不可变更为" + status);
        }
        b.setStatus(status);
        if (status == 5 || status == 6) {
            b.setCancelTime(LocalDateTime.now());
            b.setCancelReason(reason);
        }
        bookingService.updateById(b);
        return Result.success("状态变更成功", b);
    }

    /** T06-13 签到（POST /booking/{id}/checkin） */
    @PostMapping("/{id:\\d+}/checkin")
    @RequirePermission("booking:checkin")
    public Result<Booking> checkin(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking b = bookingService.getById(id);
        if (b == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(b.getTenantId())) {
            return Result.error("预约不存在");
        }
        if (b.getStatus() != 2) return Result.error("仅已确认的预约可以签到");
        b.setStatus(3);
        b.setCheckinTime(LocalDateTime.now());
        bookingService.updateById(b);
        return Result.success("签到成功", b);
    }

    /** T06-14 完成（POST /booking/{id}/complete） */
    @PostMapping("/{id:\\d+}/complete")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("booking:complete")
    public Result<Booking> complete(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking b = bookingService.getById(id);
        if (b == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(b.getTenantId())) {
            return Result.error("预约不存在");
        }
        if (b.getStatus() != 3) return Result.error("仅已签到的预约可以完成");
        b.setStatus(4);
        b.setCompleteTime(LocalDateTime.now());
        bookingService.updateById(b);
        return Result.success("服务已完成", b);
    }

    /** 详情 */
    @GetMapping("/{id:\\d+}")
    @RequirePermission("booking:detail")
    public Result<Booking> detail(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking b = bookingService.getById(id);
        if (b == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(b.getTenantId())) {
            return Result.error("预约不存在");
        }
        return Result.success(b);
    }

    /** P0-BOOKING-004：编辑预约（PUT /booking/{id}） */
    @PutMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("booking:update")
    public Result<Booking> update(@PathVariable Long id, @RequestBody Booking body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking old = bookingService.getById(id);
        if (old == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(old.getTenantId())) {
            return Result.error("预约不存在");
        }
        // 仅允许修改部分字段
        if (body.getMemberId() != null) old.setMemberId(body.getMemberId());
        if (body.getMemberName() != null) old.setMemberName(body.getMemberName());
        if (body.getMemberPhone() != null) old.setMemberPhone(body.getMemberPhone());
        if (body.getEmployeeId() != null) old.setEmployeeId(body.getEmployeeId());
        if (body.getEmployeeName() != null) old.setEmployeeName(body.getEmployeeName());
        if (body.getServiceItem() != null) old.setServiceItem(body.getServiceItem());
        if (body.getRoomId() != null) old.setRoomId(body.getRoomId());
        if (body.getRoomName() != null) old.setRoomName(body.getRoomName());
        if (body.getBookingDate() != null) old.setBookingDate(body.getBookingDate());
        if (body.getStartTime() != null) {
            int startMin = body.getStartTime().getMinute();
            if (startMin % 30 != 0) {
                return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "开始时间必须为整点或半点（如 09:00、09:30）");
            }
            old.setStartTime(body.getStartTime());
        }
        if (body.getEndTime() != null) {
            int endMin = body.getEndTime().getMinute();
            if (endMin % 30 != 0) {
                return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "结束时间必须为整点或半点（如 10:00、10:30）");
            }
            old.setEndTime(body.getEndTime());
        }
        if (body.getRemark() != null) old.setRemark(body.getRemark());

        // P1-BOOKING-001: 编辑预约时检查时段冲突（同一技师或同一房间）
        if (body.getEmployeeId() != null || body.getBookingDate() != null
                || body.getStartTime() != null || body.getEndTime() != null) {
            Long empId = body.getEmployeeId() != null ? body.getEmployeeId() : old.getEmployeeId();
            Long roomId = body.getRoomId() != null ? body.getRoomId() : old.getRoomId();
            LocalDate date = body.getBookingDate() != null ? body.getBookingDate() : old.getBookingDate();
            LocalDateTime startTime = body.getStartTime() != null ? body.getStartTime() : old.getStartTime();
            LocalDateTime endTime = body.getEndTime() != null ? body.getEndTime() : old.getEndTime();
            if (empId != null && date != null && startTime != null && endTime != null) {
                LambdaQueryWrapper<Booking> conflict = new LambdaQueryWrapper<>();
                conflict.eq(Booking::getEmployeeId, empId)
                        .eq(Booking::getBookingDate, date)
                        .in(Booking::getStatus, Arrays.asList(1, 2, 3))
                        .ne(Booking::getId, id)
                        .and(w -> w.and(w1 -> w1.le(Booking::getStartTime, startTime).ge(Booking::getEndTime, startTime))
                                     .or(w2 -> w2.le(Booking::getStartTime, endTime).gt(Booking::getEndTime, endTime))
                                     .or(w3 -> w3.gt(Booking::getStartTime, startTime).lt(Booking::getEndTime, endTime)));
                if (bookingService.count(conflict) > 0) {
                    return Result.error(com.meiyetec.exception.BizCode.CONFLICT, "该技师在此时段已有其他预约");
                }
            }
            if (roomId != null && date != null && startTime != null && endTime != null) {
                LambdaQueryWrapper<Booking> conflict = new LambdaQueryWrapper<>();
                conflict.eq(Booking::getRoomId, roomId)
                        .eq(Booking::getBookingDate, date)
                        .in(Booking::getStatus, Arrays.asList(1, 2, 3))
                        .ne(Booking::getId, id)
                        .and(w -> w.and(w1 -> w1.le(Booking::getStartTime, startTime).ge(Booking::getEndTime, startTime))
                                     .or(w2 -> w2.le(Booking::getStartTime, endTime).gt(Booking::getEndTime, endTime))
                                     .or(w3 -> w3.gt(Booking::getStartTime, startTime).lt(Booking::getEndTime, endTime)));
                if (bookingService.count(conflict) > 0) {
                    return Result.error(com.meiyetec.exception.BizCode.CONFLICT, "该房间在此时段已被占用");
                }
            }
        }

        bookingService.updateById(old);
        return Result.success("更新成功", old);
    }

    /** 删除（仅待确认/已取消能删） */
    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("booking:delete")
    public Result<Void> delete(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Booking b = bookingService.getById(id);
        if (b == null) return Result.error("预约不存在");
        if (tenantId != null && !tenantId.equals(b.getTenantId())) {
            return Result.error("预约不存在");
        }
        if (b.getStatus() != 1 && b.getStatus() != 5) {
            return Result.error("仅待确认/已取消可以删除");
        }
        bookingService.removeById(id);
        return Result.success("删除成功", null);
    }

    /** 首页统计：今日预约数 + 到店率 */
    @GetMapping("/stats")
    @RequirePermission("booking:stat")
    public Result<Map<String, Object>> stats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();

        String sql =
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN status IN (2,3,4) THEN 1 ELSE 0 END),0) AS confirmed, " +
            "  COALESCE(SUM(CASE WHEN status IN (3,4) THEN 1 ELSE 0 END),0) AS checkedIn, " +
            "  COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END),0) AS noShow, " +
            "  COALESCE(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END),0) AS cancelled " +
            "FROM biz_booking WHERE deleted = 0 AND tenant_id = ? AND booking_date = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, today);
        Map<String, Object> row = rows.isEmpty() ? Map.of(
            "confirmed", 0, "checkedIn", 0, "noShow", 0, "cancelled", 0) : rows.get(0);

        int confirmed = ((Number) row.getOrDefault("confirmed", 0)).intValue();
        int checkedIn = ((Number) row.getOrDefault("checkedIn", 0)).intValue();
        int noShow = ((Number) row.getOrDefault("noShow", 0)).intValue();
        int cancelled = ((Number) row.getOrDefault("cancelled", 0)).intValue();
        int pending = (int) bookingService.count(new LambdaQueryWrapper<Booking>()
            .eq(Booking::getTenantId, tenantId)
            .eq(Booking::getBookingDate, today)
            .eq(Booking::getStatus, 1));

        int total = confirmed + checkedIn + noShow + cancelled + pending;

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("confirmed", confirmed);
        data.put("checkedIn", checkedIn);
        data.put("noShow", noShow);
        data.put("cancelled", cancelled);
        data.put("checkInRate", total == 0 ? 0 :
            new java.math.BigDecimal(checkedIn * 100.0 / total).setScale(1, java.math.RoundingMode.HALF_UP));

        // 待完成 = 已确认但未签到/未完成
        data.put("pending", confirmed - checkedIn);
        return Result.success(data);
    }

    /** 导出 CSV（GET /booking/export） —— 直接流式输出 .csv，UTF-8 BOM 兼容 Excel */
    @GetMapping("/export")
    @RequirePermission("booking:export")
    public void export(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<Booking> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(Booking::getStatus, status);
        if (startDate != null && !startDate.isEmpty()) {
            qw.ge(Booking::getBookingDate, LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            qw.le(Booking::getBookingDate, LocalDate.parse(endDate));
        }
        qw.orderByAsc(Booking::getBookingDate).orderByAsc(Booking::getStartTime);

        List<Booking> list = bookingService.list(qw);

        String fileName = URLEncoder.encode(
                "预约列表_" + LocalDate.now() + ".csv", StandardCharsets.UTF_8);
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            // BOM 头：让 Excel 正确识别 UTF-8
            pw.write('\ufeff');
            pw.println("预约号,会员,会员电话,技师,服务项目,房间,日期,开始时间,结束时间,状态,下单时间,备注");
            for (Booking b : list) {
                pw.println(String.join(",",
                        csv(b.getBookingNo()),
                        csv(b.getMemberName()),
                        csv(b.getMemberPhone()),
                        csv(b.getEmployeeName()),
                        csv(b.getServiceItem()),
                        csv(b.getRoomName()),
                        csv(b.getBookingDate() == null ? "" : b.getBookingDate().toString()),
                        csv(b.getStartTime() == null ? "" : b.getStartTime().toLocalTime().toString()),
                        csv(b.getEndTime() == null ? "" : b.getEndTime().toLocalTime().toString()),
                        csv(statusText(b.getStatus())),
                        csv(b.getCreateTime() == null ? "" : b.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                        csv(b.getRemark())
                ));
            }
            pw.flush();
        }
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = v.toString();
        // 含逗号/引号/换行的字段用双引号包起来
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String statusText(Integer s) {
        if (s == null) return "";
        String[] m = {"", "待确认", "已确认", "已签到", "已完成", "已取消", "爽约"};
        return s >= 0 && s < m.length ? m[s] : "未知";
    }

    /** 日历视图（GET /booking/calendar）：按月返回每天的预约统计 */
    @GetMapping("/calendar")
    @RequirePermission("booking:calendar")
    public Result<Map<String, Object>> calendar(
            @RequestParam(required = false) String yearMonth) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();
        int y = today.getYear();
        int m = today.getMonthValue();
        if (yearMonth != null && yearMonth.matches("\\d{4}-\\d{2}")) {
            y = Integer.parseInt(yearMonth.substring(0, 4));
            m = Integer.parseInt(yearMonth.substring(5, 7));
        }
        LocalDate start = LocalDate.of(y, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        String sql = "SELECT DATE(booking_date) AS day, status, COUNT(*) AS cnt " +
                "FROM biz_booking WHERE deleted=0 AND tenant_id=? AND booking_date BETWEEN ? AND ? " +
                "GROUP BY DATE(booking_date), status ORDER BY day";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, start, end);

        Map<String, Map<String, Integer>> cal = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, Integer> dayStat = new HashMap<>();
            dayStat.put("total", 0);
            for (int s = 1; s <= 6; s++) dayStat.put("status_" + s, 0);
            cal.put(d.toString(), dayStat);
        }
        for (Map<String, Object> r : rows) {
            String day = r.get("day").toString();
            int st = ((Number) r.get("status")).intValue();
            int cnt = ((Number) r.get("cnt")).intValue();
            Map<String, Integer> dayStat = cal.get(day);
            if (dayStat != null) {
                dayStat.put("status_" + st, cnt);
                dayStat.put("total", dayStat.get("total") + cnt);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearMonth", y + "-" + String.format("%02d", m));
        result.put("days", cal);
        return Result.success(result);
    }

    /** 技师排班表（GET /booking/schedule）：返回指定日期范围内所有技师的排班 + 预约占用情况 */
    @GetMapping("/schedule")
    @RequirePermission("booking:schedule:list")
    public Result<Map<String, Object>> schedule(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate start = startDate == null || startDate.isEmpty()
                ? LocalDate.now() : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isEmpty()
                ? start.plusDays(6) : LocalDate.parse(endDate);

        // 1) 技师基础信息（按门店筛选）
        List<Employee> emps = employeeService.list(
                new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getStoreId, TenantContextHolder.get().getStoreId())
                        .eq(Employee::getStatus, 1));

        // 2) 排班
        List<EmployeeSchedule> schedules = scheduleService.list(
                new LambdaQueryWrapper<EmployeeSchedule>()
                        .eq(EmployeeSchedule::getTenantId, tenantId)
                        .ge(EmployeeSchedule::getWorkDate, start)
                        .le(EmployeeSchedule::getWorkDate, end));

        // 3) 预约占用
        List<Booking> bookings = bookingService.list(
                new LambdaQueryWrapper<Booking>()
                        .eq(Booking::getTenantId, tenantId)
                        .ge(Booking::getBookingDate, start)
                        .le(Booking::getBookingDate, end)
                        .in(Booking::getStatus, Arrays.asList(1, 2, 3)));

        Map<Long, List<Map<String, Object>>> empBookings = bookings.stream()
                .collect(Collectors.groupingBy(
                        Booking::getEmployeeId,
                        Collectors.mapping(b -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("bookingId", b.getId());
                            m.put("date", b.getBookingDate());
                            m.put("startTime", b.getStartTime());
                            m.put("endTime", b.getEndTime());
                            m.put("memberName", b.getMemberName());
                            m.put("serviceItem", b.getServiceItem());
                            m.put("status", b.getStatus());
                            return m;
                        }, Collectors.toList())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Employee emp : emps) {
            Map<String, Object> empRow = new LinkedHashMap<>();
            empRow.put("employeeId", emp.getId());
            empRow.put("employeeName", emp.getEmployeeName());
            empRow.put("title", emp.getPosition());
            Map<String, Map<String, Object>> scheduleMap = new LinkedHashMap<>();
            for (EmployeeSchedule sch : schedules) {
                if (!sch.getEmployeeId().equals(emp.getId())) continue;
                Map<String, Object> schRow = new HashMap<>();
                schRow.put("workDate", sch.getWorkDate());
                schRow.put("startTime", sch.getStartTime());
                schRow.put("endTime", sch.getEndTime());
                schRow.put("maxBookings", sch.getMaxBookings());
                schRow.put("status", sch.getStatus());
                scheduleMap.put(sch.getWorkDate().toString(), schRow);
            }
            empRow.put("schedule", scheduleMap);
            empRow.put("bookings", empBookings.getOrDefault(emp.getId(), Collections.emptyList()));
            result.add(empRow);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startDate", start);
        data.put("endDate", end);
        data.put("employees", result);
        return Result.success(data);
    }
}


