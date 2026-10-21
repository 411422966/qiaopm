package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.dto.MemberBatchTagDTO;
import com.meiyetec.dto.MemberDTO;
import com.meiyetec.dto.PageQueryDTO;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.PosOrderItemMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.mapper.PosPaymentMapper;
import com.meiyetec.service.*;
import com.meiyetec.util.IdGenerator;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
@SysOpLog(module = "会员")
@Slf4j
public class MemberController {

    private final MemberService memberService;
    private final MemberTagService tagService;
    private final MemberLevelService levelService;
    private final StoreService storeService;
    private final MemberProfileService profileService;
    private final JdbcTemplate jdbc;
    private final com.meiyetec.service.WalletTransactionService txnService;
    private final IdGenerator idGenerator;
    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper itemMapper;
    private final PosPaymentMapper paymentMapper;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final com.meiyetec.mkt.event.MarketingEventPublisher marketingEventPublisher;

    /**
     * 补全订单的 items / payments / cashierName（与 PosController.enrichOrders 行为一致）
     */
    private void enrichOrders(List<PosOrder> orders) {
        List<Long> orderIds = orders.stream().map(PosOrder::getId).collect(Collectors.toList());
        Map<Long, List<PosOrderItem>> itemMap = itemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>().in(PosOrderItem::getOrderId, orderIds)
        ).stream().collect(Collectors.groupingBy(PosOrderItem::getOrderId));
        Map<Long, List<PosPayment>> paymentMap = paymentMapper.selectList(
                new LambdaQueryWrapper<PosPayment>().in(PosPayment::getOrderId, orderIds)
        ).stream().collect(Collectors.groupingBy(PosPayment::getOrderId));
        Set<Long> cashierIds = orders.stream()
                .map(PosOrder::getCashierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> cashierNameMap = new HashMap<>();
        if (!cashierIds.isEmpty()) {
            try {
                List<Map<String, Object>> rows = namedJdbc.queryForList(
                        "SELECT id, real_name, username FROM sys_user WHERE id IN (:ids)",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("ids", cashierIds));
                for (Map<String, Object> r : rows) {
                    Object idObj = r.get("id");
                    if (idObj == null) continue;
                    long uid = ((Number) idObj).longValue();
                    String name = (String) r.get("real_name");
                    if (name == null || name.isEmpty()) name = (String) r.get("username");
                    if (name != null) cashierNameMap.put(uid, name);
                }
            } catch (Exception ignored) {
                // 静默兜底：sys_user 表缺失不影响订单展示
            }
        }
        orders.forEach(o -> {
            o.setItems(itemMap.getOrDefault(o.getId(), new ArrayList<>()));
            o.setPayments(paymentMap.getOrDefault(o.getId(), new ArrayList<>()));
            if (o.getCashierId() != null) {
                o.setCashierName(cashierNameMap.getOrDefault(o.getCashierId(), o.getCashierName()));
            }
        });
    }

    private Long tenantId() {
        Long id = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        return id; // null 表示平台级（极少数场景）
    }

    private Member requireMember(Long id) {
        Member m = memberService.getById(id);
        if (m == null) return null;
        Long tid = tenantId();
        if (tid != null && !tid.equals(m.getTenantId())) return null;
        return m;
    }

    private com.meiyetec.security.TenantContext ctx() {
        return com.meiyetec.security.TenantContextHolder.get();
    }

    @PostMapping("/page")
    @RequirePermission("member:list")
    public Result<PageResult<Member>> page(@RequestBody PageQueryDTO query) {
        Page<Member> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like(Member::getName, kw)
                    .or().like(Member::getPhone, kw)
                    .or().like(Member::getMemberNo, kw));
        }
        if (query.getStatus() != null) wrapper.eq(Member::getStatus, query.getStatus());
        Long tid = tenantId();
        if (tid != null) wrapper.eq(Member::getTenantId, tid);
        if (query.getStoreId() != null) {
            wrapper.eq(Member::getStoreId, query.getStoreId());
        } else {
            var c = ctx();
            if (!"ALL".equals(c.getDataScope()) && c.getStoreIds() != null
                    && !c.getStoreIds().isEmpty() && !c.getStoreIds().contains(-1L)) {
                wrapper.in(Member::getStoreId, c.getStoreIds());
            }
        }
        if (query.getLevelId() != null) wrapper.eq(Member::getLevelId, query.getLevelId());
        if (query.getTagId() != null) {
            wrapper.inSql(Member::getId,
                    "SELECT member_id FROM crm_member_tag_rel WHERE tag_id = " + query.getTagId());
        }
        if (query.getTagIds() != null && !query.getTagIds().isEmpty()) {
            for (Long tagId : query.getTagIds()) {
                wrapper.inSql(Member::getId,
                        "SELECT member_id FROM crm_member_tag_rel WHERE tag_id = " + tagId);
            }
        }
        applySort(wrapper, query.getSortField(), query.getSortOrder());
        Page<Member> result = memberService.page(page, wrapper);
        if (result.getTotal() == 0L && result.getRecords() != null && !result.getRecords().isEmpty()) {
            long realTotal = memberService.count(wrapper);
            result.setTotal(realTotal);
            result.setPages((realTotal + query.getPageSize() - 1) / query.getPageSize());
        }
        fillExtras(result.getRecords());
        return Result.success(PageResult.of(result));
    }

    private void applySort(LambdaQueryWrapper<Member> wrapper, String sortField, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if (sortField == null || sortField.isEmpty()) {
            wrapper.orderByDesc(Member::getId);
            return;
        }
        switch (sortField) {
            case "totalConsume" -> wrapper.orderBy(true, asc, Member::getTotalConsume);
            case "points"        -> wrapper.orderBy(true, asc, Member::getPoints);
            case "balance"       -> wrapper.orderBy(true, asc, Member::getBalance);
            case "lastVisit"     -> wrapper.orderBy(true, asc, Member::getLastVisit);
            case "joinDate"      -> wrapper.orderBy(true, asc, Member::getJoinDate);
            default              -> wrapper.orderByDesc(Member::getId);
        }
    }

    @GetMapping("/list")
    @RequirePermission("member:list")
    public Result<List<Member>> list() {
        LambdaQueryWrapper<Member> w = new LambdaQueryWrapper<Member>()
                .eq(Member::getStatus, 1);
        Long tid = tenantId();
        if (tid != null) w.eq(Member::getTenantId, tid);
        List<Member> list = memberService.list(w.orderByDesc(Member::getId));
        fillExtras(list);
        return Result.success(list);
    }

    @GetMapping("/stats")
    @RequirePermission("member:stat")
    public Result<Map<String, Object>> stats() {
        Long tid = tenantId();
        LambdaQueryWrapper<Member> w = new LambdaQueryWrapper<Member>();
        if (tid != null) w.eq(Member::getTenantId, tid);
        List<Member> all = memberService.list(w);
        long total = all.size();
        long active = all.stream().filter(m -> m.getStatus() != null && m.getStatus() == 1).count();
        long frozen = all.stream().filter(m -> m.getStatus() != null && m.getStatus() == 2).count();
        BigDecimal totalBalance = all.stream()
                .map(m -> m.getBalance() == null ? BigDecimal.ZERO : m.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalConsume = all.stream()
                .map(m -> m.getTotalConsume() == null ? BigDecimal.ZERO : m.getTotalConsume())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalPoints = all.stream().mapToInt(m -> m.getPoints() == null ? 0 : m.getPoints()).sum();

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate prevStart = monthStart.minusMonths(1);
        LocalDate prevEnd = monthStart.minusDays(1);
        long monthNew = all.stream()
                .filter(m -> m.getJoinDate() != null && !m.getJoinDate().isBefore(monthStart)).count();
        long prevMonthNew = all.stream()
                .filter(m -> m.getJoinDate() != null
                        && !m.getJoinDate().isBefore(prevStart)
                        && !m.getJoinDate().isAfter(prevEnd)).count();

        BigDecimal monthBalance = all.stream()
                .filter(m -> m.getJoinDate() != null && !m.getJoinDate().isBefore(monthStart))
                .map(m -> m.getBalance() == null ? BigDecimal.ZERO : m.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int monthPoints = all.stream()
                .filter(m -> m.getJoinDate() != null && !m.getJoinDate().isBefore(monthStart))
                .mapToInt(m -> m.getPoints() == null ? 0 : m.getPoints()).sum();
        int prevPoints = all.stream()
                .filter(m -> m.getJoinDate() != null
                        && !m.getJoinDate().isBefore(prevStart)
                        && !m.getJoinDate().isAfter(prevEnd))
                .mapToInt(m -> m.getPoints() == null ? 0 : m.getPoints()).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("frozen", frozen);
        stats.put("totalBalance", totalBalance);
        stats.put("totalConsume", totalConsume);
        stats.put("totalPoints", totalPoints);
        stats.put("monthNew", monthNew);
        stats.put("totalTrend", calcTrend(monthNew, prevMonthNew));
        stats.put("activeRate", total == 0 ? 0 :
                new BigDecimal(active * 100.0 / total).setScale(1, RoundingMode.HALF_UP));
        stats.put("balanceTrend", calcTrendBig(monthBalance, BigDecimal.ZERO));
        stats.put("pointsTrend", calcTrend(monthPoints, prevPoints));

        // 今日营收
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        QueryWrapper<WalletTransaction> todayQw = new QueryWrapper<>();
        todayQw.ge("created_at", todayStart).lt("created_at", tomorrowStart);
        if (tid != null) todayQw.eq("tenant_id", tid);
        List<WalletTransaction> todayTxns = txnService.list(todayQw);
        BigDecimal todayRecharge = BigDecimal.ZERO;
        BigDecimal todayConsume = BigDecimal.ZERO;
        long todayRechargeCount = 0;
        long todayConsumeCount = 0;
        for (WalletTransaction t : todayTxns) {
            if (t.getType() == null) continue;
            if (t.getType() == 1) {
                todayRecharge = todayRecharge.add(t.getAmount() == null ? BigDecimal.ZERO : t.getAmount());
                todayRechargeCount++;
            } else if (t.getType() == 2) {
                todayConsume = todayConsume.add(t.getAmount() == null ? BigDecimal.ZERO : t.getAmount());
                todayConsumeCount++;
            }
        }
        stats.put("todayRevenue", todayConsume);
        stats.put("todayOrderCount", todayConsumeCount);
        stats.put("todayRecharge", todayRecharge);
        stats.put("todayRechargeCount", todayRechargeCount);

        // 最近 5 条流水
        QueryWrapper<WalletTransaction> recentQw = new QueryWrapper<>();
        recentQw.orderByDesc("created_at").last("LIMIT 5");
        if (tid != null) recentQw.eq("tenant_id", tid);
        List<WalletTransaction> recent = txnService.list(recentQw);
        List<Map<String, Object>> recentList = new ArrayList<>();
        for (WalletTransaction t : recent) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("type", t.getType());
            m.put("typeLabel", switch (t.getType() == null ? 0 : t.getType()) {
                case 1 -> "充值"; case 2 -> "消费"; case 3 -> "退款";
                case 4 -> "赠送"; case 5 -> "冻结"; case 6 -> "解冻";
                default -> "其它";
            });
            m.put("amount", t.getAmount());
            m.put("giftAmount", t.getGiftAmount());
            m.put("memberId", t.getMemberId());
            Member mem = memberService.getById(t.getMemberId());
            m.put("memberName", mem == null ? "?" : mem.getName());
            m.put("createdAt", t.getCreatedAt());
            recentList.add(m);
        }
        stats.put("recentTxns", recentList);
        return Result.success(stats);
    }

    private static BigDecimal calcTrend(long cur, long prev) {
        if (prev == 0) return cur == 0 ? BigDecimal.ZERO : new BigDecimal(100);
        return new BigDecimal((cur - prev) * 100.0 / prev).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal calcTrendBig(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return cur == null || cur.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : new BigDecimal(100);
        }
        return cur.subtract(prev).multiply(new BigDecimal(100))
                .divide(prev, 1, RoundingMode.HALF_UP);
    }

    /**
     * M03 历史消费追溯：会员的所有 POS 订单（含已支付/已退款/已作废）
     * @param id 会员 ID
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 10，最大 100）
     */
    @GetMapping("/{id:\\d+}/orders")
    @RequirePermission("member:order:list")
    public Result<PageResult<PosOrder>> memberOrders(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Member member = requireMember(id);
        if (member == null) return Result.error("会员不存在或无权访问");
        // 防止一次拉太多
        int ps = Math.min(Math.max(pageSize, 1), 100);
        Page<PosOrder> page = new Page<>(pageNum, ps);
        LambdaQueryWrapper<PosOrder> wrapper = new LambdaQueryWrapper<>();
        if (tenantId() != null) wrapper.eq(PosOrder::getTenantId, tenantId());
        wrapper.eq(PosOrder::getMemberId, id);
        wrapper.orderByDesc(PosOrder::getId);
        Page<PosOrder> result = orderMapper.selectPage(page, wrapper);
        // 补全 items + payments + cashierName（与 /pos/orders/page 行为一致）
        List<PosOrder> orders = result.getRecords();
        if (orders != null && !orders.isEmpty()) {
            enrichOrders(orders);
        }
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("member:list")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        Member member = requireMember(id);
        if (member == null) return Result.error("会员不存在或无权访问");
        fillExtras(Collections.singletonList(member));
        MemberProfile profile = profileService.getOne(
            new LambdaQueryWrapper<MemberProfile>().eq(MemberProfile::getMemberId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("member", member);
        result.put("profile", profile);
        return Result.success(result);
    }

    @PostMapping
    @RequirePermission("member:add")
    public Result<Void> create(@Valid @RequestBody MemberDTO dto) {
        Long tid = tenantId();
        Member existing = memberService.getOne(
            new LambdaQueryWrapper<Member>().eq(Member::getPhone, dto.getPhone()));
        if (existing != null) {
            return Result.error("该手机号已存在");
        }
        Member m = new Member();
        BeanUtils.copyProperties(dto, m);
        if (m.getMemberNo() == null || m.getMemberNo().isEmpty()) {
            m.setMemberNo("M" + idGenerator.nextIdStr());
        }
        if (m.getJoinDate() == null) m.setJoinDate(LocalDate.now());
        if (m.getBalance() == null) m.setBalance(BigDecimal.ZERO);
        if (m.getPoints() == null) m.setPoints(0);
        if (m.getTotalConsume() == null) m.setTotalConsume(BigDecimal.ZERO);
        if (m.getStatus() == null) m.setStatus(1);
        m.setTenantId(tid);
        memberService.save(m);
        saveMemberTags(m.getId(), dto.getTagIds());

        // ===== M16 营销事件发布：会员注册 =====
        try {
            marketingEventPublisher.memberRegistered(m.getTenantId(), m.getId(), m.getName());
        } catch (Exception ex) {
            log.warn("[M16-Event] memberRegistered 发布失败: memberId={} err={}", m.getId(), ex.getMessage());
        }

        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("member:edit")
    public Result<Void> update(@Valid @RequestBody MemberDTO dto) {
        if (dto.getId() == null) return Result.error("ID不能为空");
        Member m = requireMember(dto.getId());
        if (m == null) return Result.error("会员不存在或无权访问");
        Member samePhone = memberService.getOne(
            new LambdaQueryWrapper<Member>().eq(Member::getPhone, dto.getPhone())
                .ne(Member::getId, dto.getId()));
        if (samePhone != null) return Result.error("该手机号已被其他会员使用");
        BeanUtils.copyProperties(dto, m);
        memberService.updateById(m);
        saveMemberTags(m.getId(), dto.getTagIds());
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("member:delete")
    public Result<Void> delete(@PathVariable Long id) {
        Member m = requireMember(id);
        if (m == null) return Result.error("会员不存在或无权访问");
        memberService.removeById(id);
        jdbc.update("DELETE FROM crm_member_tag_rel WHERE member_id = ?", id);
        jdbc.update("DELETE FROM crm_member_profile WHERE member_id = ?", id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id:\\d+}/freeze")
    @RequirePermission("member:freeze")
    public Result<Void> freeze(@PathVariable Long id) {
        Member m = requireMember(id);
        if (m == null) return Result.error("会员不存在或无权访问");
        m.setStatus(2);
        memberService.updateById(m);
        return Result.success("已冻结", null);
    }

    @PostMapping("/{id:\\d+}/unfreeze")
    @RequirePermission("member:freeze")
    public Result<Void> unfreeze(@PathVariable Long id) {
        Member m = requireMember(id);
        if (m == null) return Result.error("会员不存在或无权访问");
        m.setStatus(1);
        memberService.updateById(m);
        return Result.success("已解冻", null);
    }

    @PostMapping("/{id:\\d+}/profile")
    @RequirePermission("member:profile")
    public Result<Void> saveProfile(@PathVariable Long id, @RequestBody MemberProfile profile) {
        Member m = requireMember(id);
        if (m == null) return Result.error("会员不存在或无权访问");
        Long tid = tenantId();
        profile.setMemberId(id);
        profile.setTenantId(tid);
        MemberProfile exist = profileService.getOne(
            new LambdaQueryWrapper<MemberProfile>()
                .eq(MemberProfile::getMemberId, id)
                .eq(tid != null ? MemberProfile::getTenantId : null, tid));
        if (exist == null) {
            profileService.save(profile);
        } else {
            profile.setId(exist.getId());
            profileService.updateById(profile);
        }
        return Result.success("档案已保存", null);
    }

    @PostMapping("/batch/tag")
    @RequirePermission("member:tag")
    public Result<Map<String, Object>> batchTag(@RequestBody MemberBatchTagDTO dto) {
        if (dto.getMemberIds() == null || dto.getMemberIds().isEmpty()) {
            return Result.error("请选择会员");
        }
        if (dto.getTagIds() == null || dto.getTagIds().isEmpty()) {
            return Result.error("请选择标签");
        }
        Long tid = tenantId();
        // 过滤到仅当前租户的会员
        LambdaQueryWrapper<Member> w = new LambdaQueryWrapper<Member>().in(Member::getId, dto.getMemberIds());
        if (tid != null) w.eq(Member::getTenantId, tid);
        Set<Long> validIds = memberService.list(w).stream()
                .map(Member::getId).collect(Collectors.toSet());

        int affected = 0;
        for (Long memberId : validIds) {
            if (dto.getOp() == null || dto.getOp() == 1) {
                jdbc.update("DELETE FROM crm_member_tag_rel WHERE member_id = ?", memberId);
                for (Long tagId : dto.getTagIds()) {
                    jdbc.update("INSERT IGNORE INTO crm_member_tag_rel(member_id, tag_id) VALUES(?, ?)",
                            memberId, tagId);
                }
            } else if (dto.getOp() == 2) {
                for (Long tagId : dto.getTagIds()) {
                    jdbc.update("INSERT IGNORE INTO crm_member_tag_rel(member_id, tag_id) VALUES(?, ?)",
                            memberId, tagId);
                }
            } else if (dto.getOp() == 3) {
                for (Long tagId : dto.getTagIds()) {
                    jdbc.update("DELETE FROM crm_member_tag_rel WHERE member_id = ? AND tag_id = ?",
                            memberId, tagId);
                }
            }
            affected++;
        }
        Map<String, Object> r = new HashMap<>();
        r.put("affected", affected);
        return Result.success("批量操作已完成，影响 " + affected + " 位会员", r);
    }

    @PostMapping("/batch/operation")
    @RequirePermission("member:batch")
    public Result<Map<String, Object>> batchOperation(@RequestBody Map<String, Object> body) {
        Long tid = tenantId();
        List<Long> ids = ((List<?>) body.get("memberIds")).stream()
                .map(o -> ((Number) o).longValue()).collect(Collectors.toList());
        LambdaQueryWrapper<Member> w = new LambdaQueryWrapper<Member>().in(Member::getId, ids);
        if (tid != null) w.eq(Member::getTenantId, tid);
        Set<Long> validIds = memberService.list(w).stream()
                .map(Member::getId).collect(Collectors.toSet());

        String op = (String) body.get("op");
        int affected = 0;
        for (Long id : validIds) {
            Member m = memberService.getById(id);
            if (m == null) continue;
            switch (op) {
                case "freeze" -> m.setStatus(2);
                case "unfreeze" -> m.setStatus(1);
                case "delete" -> {
                    memberService.removeById(id);
                    jdbc.update("DELETE FROM crm_member_tag_rel WHERE member_id = ?", id);
                    jdbc.update("DELETE FROM crm_member_profile WHERE member_id = ?", id);
                    affected++;
                    continue;
                }
                default -> { return Result.error("未知操作"); }
            }
            memberService.updateById(m);
            affected++;
        }
        Map<String, Object> r = new HashMap<>();
        r.put("affected", affected);
        return Result.success("批量操作成功", r);
    }

    @GetMapping("/export")
    @RequirePermission("member:export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) Long levelId,
                       @RequestParam(required = false) Long tagId,
                       HttpServletResponse response) throws IOException {
        Long tid = tenantId();
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Member::getName, keyword)
                    .or().like(Member::getPhone, keyword)
                    .or().like(Member::getMemberNo, keyword));
        }
        if (status != null) wrapper.eq(Member::getStatus, status);
        if (tid != null) wrapper.eq(Member::getTenantId, tid);
        if (storeId != null) wrapper.eq(Member::getStoreId, storeId);
        if (levelId != null) wrapper.eq(Member::getLevelId, levelId);
        if (tagId != null) {
            wrapper.inSql(Member::getId,
                    "SELECT member_id FROM crm_member_tag_rel WHERE tag_id = " + tagId);
        }
        wrapper.orderByDesc(Member::getId);
        List<Member> list = memberService.list(wrapper);
        fillExtras(list);

        String filename = "members_" + LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.setCharacterEncoding("UTF-8");

        try (var w = response.getWriter()) {
            w.write('\ufeff');
            w.println("会员号,姓名,手机号,性别,生日,等级,门店,余额,积分,累计消费,状态,加入日期,标签");
            for (Member m : list) {
                String tags = m.getTags() == null ? "" :
                        m.getTags().stream().map(MemberTag::getTagName).collect(Collectors.joining("|"));
                String gender = m.getGender() == null ? "" : (m.getGender() == 1 ? "男" : m.getGender() == 2 ? "女" : "未知");
                String statusStr = m.getStatus() == null ? "" : (m.getStatus() == 1 ? "正常" : "冻结");
                w.println(csv(m.getMemberNo()) + "," + csv(m.getName()) + "," + csv(m.getPhone()) + ","
                        + gender + "," + (m.getBirthday() == null ? "" : m.getBirthday()) + ","
                        + csv(m.getLevelName()) + "," + csv(m.getStoreName()) + ","
                        + (m.getBalance() == null ? "0" : m.getBalance()) + ","
                        + (m.getPoints() == null ? "0" : m.getPoints()) + ","
                        + (m.getTotalConsume() == null ? "0" : m.getTotalConsume()) + ","
                        + statusStr + "," + (m.getJoinDate() == null ? "" : m.getJoinDate()) + "," + csv(tags));
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @PostMapping("/import")
    @RequirePermission("member:import")
    public Result<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        Long tid = tenantId();
        Map<String, Object> result = new HashMap<>();
        int total = 0, success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.trim().isEmpty()) continue;
                total++;
                try {
                    String[] cols = line.split(",", -1);
                    if (cols.length < 2) { failed++; errors.add("第" + (total + 1) + "行字段不足"); continue; }
                    String name = cols[0].replace("\"", "").trim();
                    String phone = cols[1].replace("\"", "").trim();
                    if (!phone.matches("^1[3-9]\\d{9}$")) {
                        failed++; errors.add("第" + (total + 1) + "行手机号无效: " + phone); continue;
                    }
                    // 租户内手机号唯一性校验
                    LambdaQueryWrapper<Member> w = new LambdaQueryWrapper<Member>().eq(Member::getPhone, phone);
                    if (tid != null) w.eq(Member::getTenantId, tid);
                    Member dup = memberService.getOne(w);
                    if (dup != null) { failed++; errors.add("第" + (total + 1) + "行手机号已存在: " + phone); continue; }
                    Member m = new Member();
                    m.setName(name);
                    m.setPhone(phone);
                    m.setMemberNo("M" + idGenerator.nextIdStr());
                    m.setJoinDate(LocalDate.now());
                    m.setBalance(BigDecimal.ZERO);
                    m.setPoints(0);
                    m.setTotalConsume(BigDecimal.ZERO);
                    m.setStatus(1);
                    m.setTenantId(tid);
                    if (cols.length > 2 && !cols[2].isEmpty()) {
                        m.setGender("男".equals(cols[2].trim()) ? 1 : "女".equals(cols[2].trim()) ? 2 : 0);
                    }
                    if (cols.length > 3 && !cols[3].isEmpty()) {
                        try { m.setBirthday(LocalDate.parse(cols[3].trim())); } catch (Exception ignored) {}
                    }
                    if (cols.length > 4) m.setRemark(cols[4].trim());
                    memberService.save(m);
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("第" + (total + 1) + "行解析失败: " + e.getMessage());
                }
            }
        }
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors.size() > 10 ? errors.subList(0, 10) : errors);
        return Result.success("导入完成", result);
    }

    @GetMapping("/{id:\\d+}/tabs")
    @RequirePermission("member:list")
    public Result<Map<String, Object>> tabs(@PathVariable Long id) {
        Member m = requireMember(id);
        if (m == null) return Result.error("会员不存在或无权访问");
        Map<String, Object> data = new HashMap<>();
        data.put("consumeRecords", Collections.emptyList());
        data.put("bookings", Collections.emptyList());
        data.put("packages", Collections.emptyList());
        data.put("walletTransactions", Collections.emptyList());
        data.put("available", false);
        return Result.success(data);
    }

    private void fillExtras(List<Member> list) {
        if (list.isEmpty()) return;
        Set<Long> levelIds = list.stream().map(Member::getLevelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> storeIds = list.stream().map(Member::getStoreId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> memberIds = list.stream().map(Member::getId).collect(Collectors.toSet());

        Map<Long, String> levelMap = levelIds.isEmpty() ? Collections.emptyMap() :
            levelService.listByIds(levelIds).stream()
                    .collect(Collectors.toMap(MemberLevel::getId, MemberLevel::getLevelName));
        Map<Long, String> storeMap = storeIds.isEmpty() ? Collections.emptyMap() :
            storeService.listByIds(storeIds).stream()
                    .collect(Collectors.toMap(Store::getId, Store::getStoreName));

        List<Map<String, Object>> tagRels = memberIds.isEmpty() ? Collections.emptyList() :
            jdbc.queryForList("SELECT member_id, tag_id FROM crm_member_tag_rel WHERE member_id IN (" +
                    memberIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        Map<Long, List<Long>> memberTagMap = new HashMap<>();
        for (Map<String, Object> r : tagRels) {
            memberTagMap.computeIfAbsent(((Number) r.get("member_id")).longValue(), k -> new ArrayList<>())
                    .add(((Number) r.get("tag_id")).longValue());
        }
        List<MemberTag> allTags = tagService.list(
            new LambdaQueryWrapper<MemberTag>().eq(MemberTag::getStatus, 1));
        Map<Long, MemberTag> tagMap = allTags.stream().collect(Collectors.toMap(MemberTag::getId, t -> t));

        for (Member m : list) {
            if (m.getLevelId() != null) m.setLevelName(levelMap.get(m.getLevelId()));
            if (m.getStoreId() != null) m.setStoreName(storeMap.get(m.getStoreId()));
            List<Long> tids = memberTagMap.getOrDefault(m.getId(), Collections.emptyList());
            List<MemberTag> tags = tids.stream().map(tagMap::get).filter(Objects::nonNull).collect(Collectors.toList());
            m.setTags(tags == null ? new ArrayList<>() : tags);
        }
    }

    private void saveMemberTags(Long memberId, List<Long> tagIds) {
        jdbc.update("DELETE FROM crm_member_tag_rel WHERE member_id = ?", memberId);
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                jdbc.update("INSERT INTO crm_member_tag_rel(member_id, tag_id) VALUES(?, ?)", memberId, tagId);
            }
        }
    }
}