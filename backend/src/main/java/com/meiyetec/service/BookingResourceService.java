package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * R2.1-B
 *  - T-B12 资源 CRUD + 资源与项目绑定
 *  - T-B13 预约校验升级：多资源冲突、跨店权限、改期策略
 *  - T-B14 候补队列：取消/爽约后自动补位
 *  - T-B15 爽约规则：扣费、信用分、锁定 N 天
 */
@RestController
@RequestMapping("/biz/booking")
@RequiredArgsConstructor
public class BookingResourceService {

    private final BookingResourceMapper resourceMapper;
    private final BookingResourceLinkMapper linkMapper;
    private final BookingBookingResourceMapper bookingResourceMapper;
    private final BookingWaitlistMapper waitlistMapper;
    private final BookingNoShowRuleMapper ruleMapper;
    private final MemberCreditMapper memberCreditMapper;
    private final MemberCreditLogMapper creditLogMapper;
    private final BookingWaitlistFillService waitlistFillService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext applicationContext;

    private static final String[][] TYPE_OPTIONS = {
        {"ROOM", "房间"},
        {"BED", "床位"},
        {"DEVICE", "设备"}
    };

    public record ResourceTypeOption(String code, String name) {}

    @GetMapping("/resource/types")
    public Result<List<ResourceTypeOption>> resourceTypes() {
        List<ResourceTypeOption> list = new ArrayList<>();
        for (String[] t : TYPE_OPTIONS) list.add(new ResourceTypeOption(t[0], t[1]));
        return Result.success(list);
    }

    // ====================================================================
    // 资源 CRUD
    // ====================================================================

    @GetMapping("/resource/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> resourcePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, resource_code, resource_name, resource_type, capacity, location, " +
            "equipment, default_duration, available_from, available_to, status, sort_order, " +
            "create_time, update_time " +
            "FROM booking_resource WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (resourceType != null && !resourceType.isEmpty()) { sql.append("AND resource_type = ? "); args.add(resourceType); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (resource_code LIKE ? OR resource_name LIKE ? OR location LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY sort_order, id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM booking_resource WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (resourceType != null && !resourceType.isEmpty()) { csql.append("AND resource_type = ? "); cargs.add(resourceType); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (resource_code LIKE ? OR resource_name LIKE ? OR location LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/resource/list")
    public Result<List<BookingResource>> resourceList(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<BookingResource> q = new LambdaQueryWrapper<>();
        if (storeId != null) q.eq(BookingResource::getStoreId, storeId);
        if (resourceType != null && !resourceType.isEmpty()) q.eq(BookingResource::getResourceType, resourceType);
        if (status != null) q.eq(BookingResource::getStatus, status);
        q.orderByAsc(BookingResource::getSortOrder).orderByDesc(BookingResource::getId);
        return Result.success(resourceMapper.selectList(q));
    }

    @GetMapping("/resource/{id}")
    public Result<Map<String, Object>> resourceDetail(@PathVariable Long id) {
        BookingResource r = resourceMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "资源不存在");
        List<Map<String, Object>> services = jdbc.queryForList(
            "SELECT l.id, l.service_item_id, l.is_required, l.priority, " +
            "i.service_code, i.service_name, i.category_id, i.unit_price, i.duration " +
            "FROM booking_resource_link l " +
            "LEFT JOIN biz_service_item i ON l.service_item_id = i.id " +
            "WHERE l.deleted = 0 AND l.resource_id = ? ORDER BY l.priority DESC",
            id);
        Map<String, Object> data = new HashMap<>();
        data.put("resource", r);
        data.put("services", Keys.camel(services));
        return Result.success(data);
    }

    @PostMapping("/resource")
    public Result<BookingResource> createResource(@RequestBody BookingResource r) {
        if (r.getResourceCode() == null || r.getResourceCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "资源编码必填");
        if (r.getResourceName() == null || r.getResourceName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "资源名称必填");
        if (r.getResourceType() == null || r.getResourceType().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "资源类型必填");
        Long exist = resourceMapper.selectCount(new LambdaQueryWrapper<BookingResource>()
                .eq(BookingResource::getResourceCode, r.getResourceCode()));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "资源编码已存在");
        if (r.getCapacity() == null) r.setCapacity(1);
        if (r.getStatus() == null) r.setStatus(1);
        if (r.getDefaultDuration() == null) r.setDefaultDuration(60);
        if (r.getAvailableFrom() == null) r.setAvailableFrom(LocalTime.of(9, 0));
        if (r.getAvailableTo() == null) r.setAvailableTo(LocalTime.of(21, 0));
        if (r.getSortOrder() == null) r.setSortOrder(0);
        if (r.getTenantId() == null) r.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (r.getStoreId() == null) r.setStoreId(1L);
        resourceMapper.insert(r);
        return Result.success("创建成功", r);
    }

    @PutMapping("/resource/{id}")
    public Result<BookingResource> updateResource(@PathVariable Long id, @RequestBody BookingResource r) {
        BookingResource old = resourceMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "资源不存在");
        r.setId(id);
        r.setResourceCode(old.getResourceCode()); // 编码不可改
        resourceMapper.updateById(r);
        return Result.success("更新成功", resourceMapper.selectById(id));
    }

    @DeleteMapping("/resource/{id}")
    public Result<Void> deleteResource(@PathVariable Long id) {
        BookingResource old = resourceMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "资源不存在");
        Long inUse = bookingResourceMapper.selectCount(new LambdaQueryWrapper<BookingBookingResource>()
                .eq(BookingBookingResource::getResourceId, id));
        if (inUse > 0) return Result.error(BizCode.CONFLICT, "该资源被预约占用，无法删除");
        resourceMapper.deleteById(id);
        linkMapper.delete(new LambdaQueryWrapper<BookingResourceLink>()
                .eq(BookingResourceLink::getResourceId, id));
        return Result.success("删除成功", null);
    }

    // ====================================================================
    // 资源 - 项目绑定
    // ====================================================================

    @PostMapping("/resource/{id}/link")
    public Result<Map<String, Object>> linkService(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long serviceItemId = body.get("serviceItemId") == null ? null
                : ((Number) body.get("serviceItemId")).longValue();
        if (serviceItemId == null) return Result.error(BizCode.BAD_REQUEST, "serviceItemId 必填");
        Integer isRequired = body.get("isRequired") == null ? 1 : ((Number) body.get("isRequired")).intValue();
        Integer priority = body.get("priority") == null ? 0 : ((Number) body.get("priority")).intValue();

        BookingResource r = resourceMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "资源不存在");

        Long exist = linkMapper.selectCount(new LambdaQueryWrapper<BookingResourceLink>()
                .eq(BookingResourceLink::getResourceId, id)
                .eq(BookingResourceLink::getServiceItemId, serviceItemId));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "该资源已绑定此项目");

        BookingResourceLink link = new BookingResourceLink();
        link.setTenantId(r.getTenantId());
        link.setResourceId(id);
        link.setServiceItemId(serviceItemId);
        link.setIsRequired(isRequired);
        link.setPriority(priority);
        linkMapper.insert(link);

        Map<String, Object> data = new HashMap<>();
        data.put("resourceId", id);
        data.put("serviceItemId", serviceItemId);
        return Result.success("绑定成功", data);
    }

    @DeleteMapping("/resource/{id}/link/{serviceItemId}")
    public Result<Void> unlinkService(@PathVariable Long id, @PathVariable Long serviceItemId) {
        linkMapper.delete(new LambdaQueryWrapper<BookingResourceLink>()
                .eq(BookingResourceLink::getResourceId, id)
                .eq(BookingResourceLink::getServiceItemId, serviceItemId));
        return Result.success("已解绑", null);
    }

    /**
     * 服务的可选资源列表
     */
    @GetMapping("/resource/available")
    public Result<List<Map<String, Object>>> availableResources(
            @RequestParam Long serviceItemId,
            @RequestParam(required = false) Long storeId) {
        String sql =
            "SELECT r.id, r.resource_code, r.resource_name, r.resource_type, r.capacity, " +
            "       r.location, r.default_duration, r.available_from, r.available_to, r.status, " +
            "       l.is_required, l.priority " +
            "FROM booking_resource r " +
            "JOIN booking_resource_link l ON r.id = l.resource_id AND l.deleted = 0 " +
            "WHERE r.deleted = 0 AND r.status = 1 AND l.service_item_id = ? ";
        List<Object> args = new ArrayList<>();
        args.add(serviceItemId);
        if (storeId != null) { sql += "AND r.store_id = ? "; args.add(storeId); }
        sql += "ORDER BY l.priority DESC, r.sort_order";
        return Result.success(Keys.camel(jdbc.queryForList(sql, args.toArray())));
    }

    // ====================================================================
    // T-B13 预约校验（多资源冲突）
    // ====================================================================

    /**
     * 校验给定资源在 [start, end) 时段是否冲突。
     * 冲突定义：biz_booking 状态 in (1=待确认, 2=已确认, 3=已签到) 且该资源 booking_booking_resource 行存在。
     */
    @GetMapping("/resource/check-conflict")
    public Result<Map<String, Object>> checkConflict(
            @RequestParam Long resourceId,
            @RequestParam String bookingDate,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) Long excludeBookingId) {
        String sql =
            "SELECT b.id, b.booking_no, b.member_name, b.start_time, b.end_time, b.status " +
            "FROM biz_booking b " +
            "JOIN booking_booking_resource br ON br.booking_id = b.id AND br.deleted = 0 " +
            "WHERE br.resource_id = ? AND b.deleted = 0 " +
            "  AND b.booking_date = ? " +
            "  AND b.status IN (1,2,3) " +
            "  AND TIME(b.start_time) < ? AND TIME(b.end_time) > ? ";
        List<Object> args = new ArrayList<>();
        args.add(resourceId);
        args.add(bookingDate);
        args.add(endTime);
        args.add(startTime);
        if (excludeBookingId != null) {
            sql += "AND b.id <> ? ";
            args.add(excludeBookingId);
        }
        List<Map<String, Object>> conflicts = jdbc.queryForList(sql, args.toArray());
        Map<String, Object> data = new HashMap<>();
        data.put("conflict", !conflicts.isEmpty());
        data.put("conflicts", Keys.camel(conflicts));
        return Result.success(data);
    }

    /**
     * 校验可用时段（资源自身的 available_from / available_to）
     */
    @GetMapping("/resource/available-slots")
    public Result<Map<String, Object>> availableSlots(
            @RequestParam Long resourceId,
            @RequestParam String bookingDate,
            @RequestParam(defaultValue = "60") int durationMinutes) {
        BookingResource r = resourceMapper.selectById(resourceId);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "资源不存在");

        // 拉取当天已占用时段
        List<Map<String, Object>> occupied = jdbc.queryForList(
            "SELECT TIME(b.start_time) AS start_time, TIME(b.end_time) AS end_time FROM biz_booking b " +
            "JOIN booking_booking_resource br ON br.booking_id = b.id AND br.deleted = 0 " +
            "WHERE br.resource_id = ? AND b.deleted = 0 " +
            "  AND b.booking_date = ? AND b.status IN (1,2,3) " +
            "ORDER BY b.start_time",
            resourceId, bookingDate);

        // 计算可选时段（30 分钟步长）
        List<String> slots = new ArrayList<>();
        LocalTime from = r.getAvailableFrom() == null ? LocalTime.of(9, 0) : r.getAvailableFrom();
        LocalTime to = r.getAvailableTo() == null ? LocalTime.of(21, 0) : r.getAvailableTo();
        for (LocalTime t = from; t.plusMinutes(durationMinutes).compareTo(to) <= 0; t = t.plusMinutes(30)) {
            LocalTime end = t.plusMinutes(durationMinutes);
            boolean overlap = false;
            for (Map<String, Object> o : occupied) {
                String sStr = String.valueOf(o.get("start_time"));
                String eStr = String.valueOf(o.get("end_time"));
                LocalTime s = LocalTime.parse(sStr.length() > 8 ? sStr.substring(0, 8) : sStr);
                LocalTime e = LocalTime.parse(eStr.length() > 8 ? eStr.substring(0, 8) : eStr);
                if (s.isBefore(end) && e.isAfter(t)) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) slots.add(t.toString().substring(0, 5));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("availableFrom", from.toString());
        data.put("availableTo", to.toString());
        data.put("slots", slots);
        return Result.success(data);
    }

    // ====================================================================
    // T-B14 候补队列
    // ====================================================================

    @PostMapping("/waitlist")
    public Result<BookingWaitlist> createWaitlist(@RequestBody Map<String, Object> body) {
        BookingWaitlist w = new BookingWaitlist();
        w.setWaitlistNo("WL" + System.currentTimeMillis());
        if (body.get("memberId") != null) w.setMemberId(((Number) body.get("memberId")).longValue());
        w.setMemberName((String) body.get("memberName"));
        w.setMemberPhone((String) body.get("memberPhone"));
        if (body.get("serviceItemId") != null) w.setServiceItemId(((Number) body.get("serviceItemId")).longValue());
        w.setServiceItemName((String) body.get("serviceItemName"));
        if (body.get("employeeId") != null) w.setEmployeeId(((Number) body.get("employeeId")).longValue());
        w.setEmployeeName((String) body.get("employeeName"));
        if (body.get("preferredDate") != null) w.setPreferredDate(LocalDate.parse(body.get("preferredDate").toString()));
        if (body.get("preferredTimeFrom") != null) w.setPreferredTimeFrom(LocalTime.parse(body.get("preferredTimeFrom").toString()));
        if (body.get("preferredTimeTo") != null) w.setPreferredTimeTo(LocalTime.parse(body.get("preferredTimeTo").toString()));
        w.setPriority(body.get("priority") == null ? 0 : ((Number) body.get("priority")).intValue());
        w.setStatus(1);
        w.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        w.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (body.get("storeId") != null) w.setStoreId(((Number) body.get("storeId")).longValue()); else w.setStoreId(1L);
        w.setRemark((String) body.get("remark"));
        waitlistMapper.insert(w);
        return Result.success("候补已添加", w);
    }

    @GetMapping("/waitlist/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> waitlistPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, waitlist_no, member_id, member_name, member_phone, " +
            "service_item_id, service_item_name, employee_id, employee_name, " +
            "preferred_date, preferred_time_from, preferred_time_to, priority, " +
            "status, notify_at, notify_count, expired_at, operator_name, remark, create_time " +
            "FROM booking_waitlist WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (waitlist_no LIKE ? OR member_name LIKE ? OR member_phone LIKE ? OR service_item_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY priority DESC, create_time ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM booking_waitlist WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (waitlist_no LIKE ? OR member_name LIKE ? OR member_phone LIKE ? OR service_item_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/waitlist/{id}/cancel")
    public Result<Void> cancelWaitlist(@PathVariable Long id) {
        BookingWaitlist w = waitlistMapper.selectById(id);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "候补记录不存在");
        jdbc.update("UPDATE booking_waitlist SET status = 4 WHERE id = ?", id);
        return Result.success("已取消", null);
    }

    /**
     * M13 智能补位评分 — 预览接口（不通知、不写日志、不改状态）
     * POST /biz/booking/waitlist/score-preview
     *
     * <p>返回评分排序后的候补列表，供前台 UI 决策通知谁。</p>
     */
    @PostMapping("/waitlist/score-preview")
    public Result<Map<String, Object>> scorePreview(@RequestBody Map<String, Object> body) {
        return waitlistFillService.preview(body);
    }

    /**
     * M13 智能补位评分 — 执行智能补位（评分 + 写通知 + 改状态 + 写日志）
     * POST /biz/booking/waitlist/smart-fill
     *
     * <p>与旧 {@code /auto-fill} 共存：
     *  - 旧端点保留 FIFO + priority 排序（向后兼容）；
     *  - 新端点走 5 维度 AI 评分排序。
     * </p>
     */
    @PostMapping("/waitlist/smart-fill")
    public Result<Map<String, Object>> smartFill(@RequestBody Map<String, Object> body) {
        return waitlistFillService.smartFill(body);
    }

    /**
     * M13 智能补位评分 — 读取评分配置（按租户 + 门店）
     * GET /biz/booking/waitlist/score-config
     */
    @GetMapping("/waitlist/score-config")
    public Result<Map<String, Object>> getScoreConfig() {
        Long tenantId = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        Long storeId = com.meiyetec.security.TenantContextHolder.get().getStoreId();
        BookingWaitlistScoreService svc = applicationContext.getBean(BookingWaitlistScoreService.class);
        BookingWaitlistScoreService.ScoreConfig real = svc.loadConfig(tenantId, storeId);
        Map<String, Object> data = new HashMap<>();
        data.put("tenantId", tenantId);
        data.put("storeId", storeId);
        data.put("wPreference", real.getWPreference());
        data.put("wValue", real.getWValue());
        data.put("wNoShow", real.getWNoShow());
        data.put("wWaitTime", real.getWWaitTime());
        data.put("wResponse", real.getWResponse());
        data.put("minScoreToNotify", real.getMinScoreToNotify());
        data.put("maxNotifyPerRound", real.getMaxNotifyPerRound());
        data.put("cooldownMinutes", real.getCooldownMinutes());
        data.put("waitTimeCapHours", real.getWaitTimeCapHours());
        data.put("lockedMemberAction", real.getLockedMemberAction());
        data.put("expiredAction", real.getExpiredAction());
        data.put("enabled", real.isEnabled());
        return Result.success(data);
    }

    /**
     * M13 智能补位评分 — 更新评分配置
     * PUT /biz/booking/waitlist/score-config
     */
    @PutMapping("/waitlist/score-config")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> updateScoreConfig(@RequestBody Map<String, Object> body) {
        Long tenantId = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        Long storeId = com.meiyetec.security.TenantContextHolder.get().getStoreId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        // 校验：5 个权重之和必须接近 1.0（允许 ±0.01）
        double wP = numD(body.get("wPreference"), 0.30);
        double wV = numD(body.get("wValue"), 0.25);
        double wN = numD(body.get("wNoShow"), 0.20);
        double wW = numD(body.get("wWaitTime"), 0.15);
        double wR = numD(body.get("wResponse"), 0.10);
        double sum = wP + wV + wN + wW + wR;
        if (Math.abs(sum - 1.0) > 0.01) {
            return Result.error(BizCode.BAD_REQUEST, "5 个权重之和必须为 1.0（当前=" + sum + "）");
        }

        Long storeIdArg = storeId == null ? 0L : storeId;
        // upsert
        Long exist = jdbc.queryForObject(
            "SELECT id FROM booking_waitlist_score_config " +
            "WHERE deleted = 0 AND tenant_id = ? AND store_id = ? LIMIT 1",
            Long.class, tenantId, storeIdArg);
        if (exist == null) {
            jdbc.update(
                "INSERT INTO booking_waitlist_score_config " +
                "(tenant_id, store_id, weight_preference, weight_value, weight_no_show, " +
                " weight_wait_time, weight_response, min_score_to_notify, max_notify_per_round, " +
                " cooldown_minutes, wait_time_cap_hours, locked_member_action, expired_action, enabled, operator_name) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId, storeIdArg, wP, wV, wN, wW, wR,
                numD(body.get("minScoreToNotify"), 30.0),
                numI(body.get("maxNotifyPerRound"), 5),
                numI(body.get("cooldownMinutes"), 60),
                numI(body.get("waitTimeCapHours"), 8),
                body.getOrDefault("lockedMemberAction", "SKIP"),
                body.getOrDefault("expiredAction", "SKIP"),
                body.getOrDefault("enabled", 1),
                body.getOrDefault("operatorName", "admin"));
        } else {
            StringBuilder sql = new StringBuilder(
                "UPDATE booking_waitlist_score_config SET weight_preference=?, weight_value=?, " +
                "weight_no_show=?, weight_wait_time=?, weight_response=?, update_time=NOW()");
            List<Object> args = new ArrayList<>();
            args.add(wP); args.add(wV); args.add(wN); args.add(wW); args.add(wR);
            if (body.containsKey("minScoreToNotify")) { sql.append(", min_score_to_notify=?"); args.add(numD(body.get("minScoreToNotify"), 30)); }
            if (body.containsKey("maxNotifyPerRound")) { sql.append(", max_notify_per_round=?"); args.add(numI(body.get("maxNotifyPerRound"), 5)); }
            if (body.containsKey("cooldownMinutes")) { sql.append(", cooldown_minutes=?"); args.add(numI(body.get("cooldownMinutes"), 60)); }
            if (body.containsKey("waitTimeCapHours")) { sql.append(", wait_time_cap_hours=?"); args.add(numI(body.get("waitTimeCapHours"), 8)); }
            if (body.containsKey("lockedMemberAction")) { sql.append(", locked_member_action=?"); args.add(body.get("lockedMemberAction")); }
            if (body.containsKey("expiredAction")) { sql.append(", expired_action=?"); args.add(body.get("expiredAction")); }
            if (body.containsKey("enabled")) { sql.append(", enabled=?"); args.add(numI(body.get("enabled"), 1)); }
            sql.append(" WHERE id = ?");
            args.add(exist);
            jdbc.update(sql.toString(), args.toArray());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "评分配置已更新");
        return Result.success(data);
    }

    /**
     * M13 智能补位评分 — 查询最近 N 条评分日志（运维审计）
     * GET /biz/booking/waitlist/score-log?waitlistId=&limit=
     */
    @GetMapping("/waitlist/score-log")
    public Result<List<Map<String, Object>>> scoreLog(
            @RequestParam(required = false) Long waitlistId,
            @RequestParam(defaultValue = "20") int limit) {
        Long tenantId = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, waitlist_id, member_id, preferred_date, total_score, breakdown_json, " +
            "ranking, trigger_source, decision, reason, created_at " +
            "FROM booking_waitlist_score_log WHERE tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (waitlistId != null) { sql.append("AND waitlist_id = ? "); args.add(waitlistId); }
        sql.append("ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 200));
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        // breakdown_json 是 String，把它转成 Map
        for (Map<String, Object> r : rows) {
            Object bj = r.get("breakdown_json");
            if (bj != null) r.put("breakdown_json", bj.toString());
        }
        return Result.success(Keys.camel(rows));
    }

    /**
     * 取消/爽约后自动补位：选最优先 + 期望时段匹配 的候补并通知。
     * 返回通知的候补 id 列表。
     */
    @PostMapping("/waitlist/auto-fill")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> autoFill(@RequestBody Map<String, Object> body) {
        Long serviceItemId = body.get("serviceItemId") == null ? null
                : ((Number) body.get("serviceItemId")).longValue();
        Long employeeId = body.get("employeeId") == null ? null
                : ((Number) body.get("employeeId")).longValue();
        LocalDate preferredDate = body.get("preferredDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("preferredDate").toString());
        String startTime = (String) body.getOrDefault("startTime", "00:00");
        String endTime = (String) body.getOrDefault("endTime", "23:59");
        if (serviceItemId == null) return Result.error(BizCode.BAD_REQUEST, "serviceItemId 必填");

        String sql =
            "SELECT id, waitlist_no, member_id, member_name, member_phone, priority " +
            "FROM booking_waitlist " +
            "WHERE deleted = 0 AND status = 1 AND service_item_id = ? " +
            "  AND preferred_date = ? " +
            "  AND (preferred_time_from IS NULL OR preferred_time_from < ?) " +
            "  AND (preferred_time_to IS NULL OR preferred_time_to > ?) " +
            "  AND (employee_id IS NULL OR employee_id = ?) " +
            "ORDER BY priority DESC, create_time ASC LIMIT 5";
        List<Map<String, Object>> candidates = jdbc.queryForList(sql,
                serviceItemId, preferredDate, endTime + ":00", startTime + ":00",
                employeeId == null ? -1 : employeeId);

        List<Long> notified = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Long wid = ((Number) c.get("id")).longValue();
            // 通知（演示阶段仅写日志）
            jdbc.update(
                "INSERT INTO booking_waitlist_notify (waitlist_id, notify_type, target, content, succeeded) " +
                "VALUES (?, 'SMS', ?, ?, 1)",
                wid, c.get("member_phone"),
                "您好，您候补的「" + c.get("member_name") + "」项目有空位可预约，请尽快回复确认。");
            jdbc.update("UPDATE booking_waitlist SET status = 2, notify_at = NOW(), notify_count = notify_count + 1 WHERE id = ?", wid);
            notified.add(wid);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("notifiedCount", notified.size());
        data.put("notifiedIds", notified);
        return Result.success("已通知候补成员", data);
    }

    // ====================================================================
    // T-B15 爽约规则 + 信用分
    // ====================================================================

    @GetMapping("/no-show-rule/list")
    public Result<List<BookingNoShowRule>> ruleList(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<BookingNoShowRule> q = new LambdaQueryWrapper<>();
        if (storeId != null) q.eq(BookingNoShowRule::getStoreId, storeId);
        q.orderByAsc(BookingNoShowRule::getSortOrder).orderByDesc(BookingNoShowRule::getId);
        return Result.success(ruleMapper.selectList(q));
    }

    @PostMapping("/no-show-rule")
    public Result<BookingNoShowRule> createRule(@RequestBody BookingNoShowRule r) {
        if (r.getName() == null || r.getName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "规则名称必填");
        if (r.getNoShowCountThreshold() == null) r.setNoShowCountThreshold(3);
        if (r.getLockDays() == null) r.setLockDays(30);
        if (r.getCreditPenalty() == null) r.setCreditPenalty(10);
        if (r.getFeePenalty() == null) r.setFeePenalty(BigDecimal.ZERO);
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getTenantId() == null) r.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (r.getStoreId() == null) r.setStoreId(1L);
        ruleMapper.insert(r);
        return Result.success("创建成功", r);
    }

    @PutMapping("/no-show-rule/{id}")
    public Result<BookingNoShowRule> updateRule(@PathVariable Long id, @RequestBody BookingNoShowRule r) {
        BookingNoShowRule old = ruleMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        r.setId(id);
        ruleMapper.updateById(r);
        return Result.success("更新成功", ruleMapper.selectById(id));
    }

    @DeleteMapping("/no-show-rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        BookingNoShowRule old = ruleMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        ruleMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 触发爽约：扣分 + 扣费 + 锁定 + 累计次数
     * 通常由预约状态机从"已签到/已确认"切换到"已爽约"时调用
     */
    @PostMapping("/no-show-rule/trigger")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> triggerNoShow(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        Long bookingId = body.get("bookingId") == null ? null
                : ((Number) body.get("bookingId")).longValue();
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");

        // 拿第一条启用规则
        BookingNoShowRule rule = jdbc.queryForObject(
            "SELECT * FROM booking_no_show_rule WHERE deleted = 0 AND enabled = 1 " +
            "ORDER BY sort_order ASC, id ASC LIMIT 1",
            (rs, rn) -> {
                BookingNoShowRule r = new BookingNoShowRule();
                r.setId(rs.getLong("id"));
                r.setNoShowCountThreshold(rs.getInt("no_show_count_threshold"));
                r.setLockDays(rs.getInt("lock_days"));
                r.setCreditPenalty(rs.getInt("credit_penalty"));
                r.setFeePenalty(rs.getBigDecimal("fee_penalty"));
                return r;
            });
        if (rule == null) return Result.error(BizCode.NOT_FOUND, "未配置爽约规则");

        // 原子更新 member_credit：累计爽约 +1、信用 -N、locked_until
        Long newCount = jdbc.queryForObject(
            "SELECT no_show_count FROM member_credit WHERE member_id = ?",
            Long.class, memberId);
        long count = newCount == null ? 0 : newCount;
        long newCountAfter = count + 1;

        // 查询当前 credit 用于 audit
        Integer curCredit = jdbc.queryForObject(
            "SELECT credit FROM member_credit WHERE member_id = ?",
            Integer.class, memberId);
        int newCredit = (curCredit == null ? 100 : curCredit) - (rule.getCreditPenalty() == null ? 0 : rule.getCreditPenalty());

        // upsert
        jdbc.update(
            "INSERT INTO member_credit (member_id, tenant_id, credit, no_show_count, total_bookings, locked_until, last_no_show_at, update_time) " +
            "VALUES (?, 1, ?, ?, 0, IF(? >= ?, NOW() + INTERVAL ? DAY, NULL), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE credit = VALUES(credit), no_show_count = VALUES(no_show_count), " +
            "locked_until = IF(VALUES(locked_until) IS NOT NULL, VALUES(locked_until), locked_until), " +
            "last_no_show_at = NOW(), update_time = NOW()",
            memberId, newCredit, newCountAfter,
            newCountAfter, rule.getNoShowCountThreshold() == null ? 99 : rule.getNoShowCountThreshold(),
            rule.getNoShowCountThreshold() == null ? 99 : rule.getNoShowCountThreshold(),
            rule.getLockDays() == null ? 30 : rule.getLockDays());
        // 注：IF 简化；触发锁定的条件：累加后 >= 阈值 才锁定

        // 写信用日志
        jdbc.update(
            "INSERT INTO member_credit_log (tenant_id, member_id, change_type, change_value, credit_after, " +
            "booking_id, fee_penalty, locked_until, operator_name, remark) " +
            "VALUES (1, ?, 'NO_SHOW', ?, ?, ?, ?, ?, ?, ?)",
            memberId, -Math.abs(rule.getCreditPenalty() == null ? 0 : rule.getCreditPenalty()),
            newCredit, bookingId,
            rule.getFeePenalty() == null ? BigDecimal.ZERO : rule.getFeePenalty(),
            newCountAfter >= (rule.getNoShowCountThreshold() == null ? 99 : rule.getNoShowCountThreshold())
                ? LocalDateTime.now().plusDays(rule.getLockDays() == null ? 30 : rule.getLockDays()) : null,
            "system",
            newCountAfter >= (rule.getNoShowCountThreshold() == null ? 99 : rule.getNoShowCountThreshold())
                ? "累计爽约 " + newCountAfter + " 次，锁定 " + rule.getLockDays() + " 天" : "爽约 1 次");

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("newCount", newCountAfter);
        data.put("newCredit", newCredit);
        data.put("locked",
                newCountAfter >= (rule.getNoShowCountThreshold() == null ? 99 : rule.getNoShowCountThreshold()));
        data.put("feePenalty", rule.getFeePenalty());
        return Result.success("爽约已处理", data);
    }

    @GetMapping("/member-credit")
    public Result<Map<String, Object>> getMemberCredit(@RequestParam Long memberId) {
        Map<String, Object> data = new HashMap<>();
        try {
            List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM member_credit WHERE member_id = ?", memberId);
            data.put("credit", r.isEmpty() ? null : r.get(0));
        } catch (Exception e) { data.put("credit", null); }
        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT id, change_type, change_value, credit_after, booking_id, fee_penalty, " +
            "locked_until, operator_name, remark, create_time " +
            "FROM member_credit_log WHERE deleted = 0 AND member_id = ? ORDER BY id DESC LIMIT 50",
            memberId);
        data.put("logs", Keys.camel(logs));
        return Result.success(data);
    }

    // ====================================================================
    // 导出
    // ====================================================================

    @GetMapping("/resource/export")
    public ResponseEntity<byte[]> exportResource(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Integer status) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT resource_code, resource_name, resource_type, capacity, location, equipment, " +
            "default_duration, available_from, available_to, status, create_time " +
            "FROM booking_resource WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (resourceType != null && !resourceType.isEmpty()) { sql.append("AND resource_type = ? "); args.add(resourceType); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        sql.append("ORDER BY sort_order, id");
        return csvResponse("预约资源", new String[]{
            "资源编码", "资源名称", "类型", "容量", "位置", "配套", "默认时长", "可用起", "可用止", "状态", "创建时间"
        }, sql.toString(), args.toArray(), new String[]{
            "resource_code", "resource_name", "resource_type", "capacity", "location", "equipment",
            "default_duration", "available_from", "available_to", "status", "create_time"
        });
    }

    @GetMapping("/waitlist/export")
    public ResponseEntity<byte[]> exportWaitlist(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT waitlist_no, member_name, member_phone, service_item_name, employee_name, " +
            "preferred_date, preferred_time_from, preferred_time_to, priority, status, notify_count, create_time " +
            "FROM booking_waitlist WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        sql.append("ORDER BY priority DESC, create_time");
        return csvResponse("候补队列", new String[]{
            "候补号", "会员", "电话", "项目", "技师", "期望日期", "起", "止", "优先级", "状态", "通知次数", "创建时间"
        }, sql.toString(), args.toArray(), new String[]{
            "waitlist_no", "member_name", "member_phone", "service_item_name", "employee_name",
            "preferred_date", "preferred_time_from", "preferred_time_to", "priority", "status", "notify_count", "create_time"
        });
    }

    /**
     * 爽约统计
     */
    @GetMapping("/no-show/stats")
    public Result<Map<String, Object>> noShowStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalNoShow", safeLong("SELECT COUNT(*) FROM member_credit_log WHERE deleted=0 AND change_type='NO_SHOW'"));
        data.put("totalLocked", safeLong("SELECT COUNT(*) FROM member_credit WHERE locked_until IS NOT NULL AND locked_until > NOW()"));
        data.put("lowCreditMembers", safeLong("SELECT COUNT(*) FROM member_credit WHERE credit < 60"));
        data.put("totalCreditChanges", safeLong("SELECT COUNT(*) FROM member_credit_log WHERE deleted=0"));
        data.put("recent7Days", safeLong(
            "SELECT COUNT(*) FROM member_credit_log WHERE deleted=0 AND change_type='NO_SHOW' " +
            "AND create_time > DATE_SUB(NOW(), INTERVAL 7 DAY)"));
        return Result.success(data);
    }

    private long safeLong(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (Exception e) { return 0; }
    }

    // 评分配置写入时用的工具
    private double numD(Object o, double dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return dflt; }
    }
    private int numI(Object o, int dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return dflt; }
    }

    private ResponseEntity<byte[]> csvResponse(
            String title, String[] headers, String sql, Object[] args, String[] cols) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        String tenantName = resolveTenantName();
        w.write("# " + title + " · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用\n");
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) head.append(',');
            head.append(headers[i]);
        }
        head.append('\n');
        w.write(head.toString());
        for (Map<String, Object> r : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) line.append(',');
                line.append(csv(r.get(cols[i])));
            }
            line.append('\n');
            w.write(line.toString());
        }
        w.flush();

        String filename = title + "_" + LocalDate.now() + ".csv";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        httpHeaders.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        httpHeaders.add("X-Tenant-Name", new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    private String resolveTenantName() {
        try {
            Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) return String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}
        return "默认租户";
    }

    private static String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
