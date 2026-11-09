package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktCoupon;
import com.meiyetec.entity.MktCouponBatch;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MktCouponBatchMapper;
import com.meiyetec.mapper.MktCouponMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.meiyetec.util.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 优惠券批次 + 实例 controller（前端 /mkt/coupon/* 实际对应的端点）
 *
 * 与 CouponController（/market/coupon/*）的关系：
 *  - CouponController 管理「券模板」（market_coupon 表）
 *  - MktCouponController 管理「券批次 + 券实例」（mkt_coupon_batch + mkt_coupon 表）
 *  - 前端 views/mkt/coupon.vue 实际用 /mkt/coupon/* 这一组
 */
@RestController
@RequestMapping("/mkt/coupon")
@RequiredArgsConstructor
@Slf4j
@SysOpLog(module = "卡券批次")
public class MktCouponController {

    /** 单个会员单次最多发放张数 */
    private static final int MAX_COUNT_PER_MEMBER = 100;
    /** 单次请求最多发放总张数 */
    private static final int MAX_GRANT_PER_REQUEST = 2000;
    /** 券码自增序列（保证同批发放券码唯一，避免撞唯一键 uk_tenant_code） */
    private static final java.util.concurrent.atomic.AtomicLong COUPON_CODE_SEQ = new java.util.concurrent.atomic.AtomicLong();

    private final MktCouponBatchMapper batchMapper;
    private final MktCouponMapper couponMapper;
    private final JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    // ==================== 批次 ====================

    /**
     * 批次分页（POST 兼容入口）。
     * GET 别名见 {@link #batchPageGet}——前端 views/mkt/coupon.vue 用的是 GET，原先只有 POST 会返回 405。
     *
     * <p>权限：只读端点允许 {@code market:coupon:list} 或 {@code mkt:coupon} 任一命中（ANY）。
     * 原因：/market/coupon 路由（router/index.ts，权限码 market:coupon:list）渲染的就是本页面，
     * 而写端点（创建/发布/发放）仍严格要求 mkt:coupon，避免批量发放权外溢。</p>
     */
    @PostMapping("/batch/page")
    @RequirePermission(value = {"mkt:coupon", "market:coupon:list"}, mode = RequirePermission.Mode.ANY)
    public Result<PageResult<MktCouponBatch>> batchPage(@RequestBody Map<String, Object> params) {
        Map<String, Object> p = params == null ? new HashMap<>() : params;
        return Result.success(queryBatchPage(
                intOf(p.get("pageNum"), 1),
                intOf(p.get("pageSize"), 20),
                p.get("keyword") == null ? null : p.get("keyword").toString(),
                p.get("status") == null ? null : intOf(p.get("status"), null)));
    }

    /**
     * 批次分页（GET 别名，与 /page 端点风格一致：@RequestParam）。
     */
    @GetMapping("/batch/page")
    @RequirePermission(value = {"mkt:coupon", "market:coupon:list"}, mode = RequirePermission.Mode.ANY)
    public Result<PageResult<MktCouponBatch>> batchPageGet(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(queryBatchPage(pageNum, pageSize, keyword, status));
    }

    private PageResult<MktCouponBatch> queryBatchPage(int pageNum, int pageSize, String keyword, Integer status) {
        Long t = tid();
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;

        LambdaQueryWrapper<MktCouponBatch> w = new LambdaQueryWrapper<>();
        w.eq(MktCouponBatch::getTenantId, t);
        if (status != null) w.eq(MktCouponBatch::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(MktCouponBatch::getBatchNo, keyword)
                    .or().like(MktCouponBatch::getCouponName, keyword));
        }
        w.orderByDesc(MktCouponBatch::getId);
        Page<MktCouponBatch> page = batchMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(page);
    }

    @PostMapping("/batch")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:coupon")
    public Result<MktCouponBatch> createBatch(@RequestBody MktCouponBatch batch) {
        Long t = tid();
        if (batch.getCouponName() == null || batch.getCouponName().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "couponName 必填");
        }
        if (batch.getCouponType() == null) {
            return Result.error(BizCode.BAD_REQUEST, "couponType 必填");
        }
        batch.setId(null);
        batch.setTenantId(t);
        if (batch.getStoreId() == null) batch.setStoreId(1L);
        if (batch.getBatchNo() == null || batch.getBatchNo().isEmpty()) {
            batch.setBatchNo("B" + System.currentTimeMillis());
        }
        if (batch.getTotalCount() == null) batch.setTotalCount(0);
        if (batch.getIssuedCount() == null) batch.setIssuedCount(0);
        if (batch.getUsedCount() == null) batch.setUsedCount(0);
        if (batch.getEffectiveDays() == null) batch.setEffectiveDays(30);
        if (batch.getExpireWarningDays() == null) batch.setExpireWarningDays(3);
        if (batch.getDiscountScope() == null) batch.setDiscountScope("ALL");
        if (batch.getGrantType() == null) batch.setGrantType("GENERAL");
        if (batch.getStatus() == null) batch.setStatus(1); // 草稿
        batchMapper.insert(batch);
        return Result.success("创建成功", batch);
    }

    @PostMapping("/batch/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:coupon")
    public Result<MktCouponBatch> publish(@PathVariable Long id) {
        Long t = tid();
        MktCouponBatch old = batchMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "批次不存在");
        }
        if (old.getStatus() != null && old.getStatus() != 1) {
            return Result.error(BizCode.CONFLICT, "只有草稿状态才能发布");
        }
        // 校验日期
        if (old.getFixedStartDate() != null && old.getFixedStartDate().isAfter(LocalDate.now())) {
            return Result.error(BizCode.BAD_REQUEST, "起始日期还没到，不能发布");
        }
        old.setStatus(2); // 发放中
        batchMapper.updateById(old);
        return Result.success("已发布", old);
    }

    @PostMapping("/batch/{id}/end")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:coupon")
    public Result<MktCouponBatch> endBatch(@PathVariable Long id) {
        Long t = tid();
        MktCouponBatch old = batchMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "批次不存在");
        }
        if (old.getStatus() != null && old.getStatus() == 3) {
            return Result.error(BizCode.CONFLICT, "已结束");
        }
        old.setStatus(3);
        batchMapper.updateById(old);
        return Result.success("已结束", old);
    }

    /**
     * 批次发放。
     *
     * P0 护栏（P0-MKT-COUPON-001）：原实现只读 body.memberIds，
     * 而前端 views/mkt/coupon.vue 提交的是 members[{memberId, memberName, count}]，
     * memberIds 缺失时 SQL 退化成「发给租户内全部 active 会员」——一次误点即批量误发。
     * 现在：
     *   1) 必须显式指定发放对象（members 或 memberIds），二者皆空 → 400；
     *   2) 全量发放必须显式 allMembers=true 且 confirm=true，否则 400；
     *   3) 发放对象按 tenant_id + deleted=0 + status=1 校验，非法 id → 400（拒绝跨租户/非正常会员）；
     *   4) 总量配额按批次剩余量截断，并在响应中回报截断情况；
     *   5) issued_count 用条件 UPDATE 累加，避免并发超发。
     */
    @PostMapping("/batch/{id}/grant")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:coupon")
    public Result<Map<String, Object>> grantBatch(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long t = tid();
        MktCouponBatch batch = batchMapper.selectById(id);
        if (batch == null || !t.equals(batch.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "批次不存在");
        }
        if (batch.getStatus() == null || batch.getStatus() != 2) {
            return Result.error(BizCode.CONFLICT, "批次未发布，无法发放");
        }

        Map<String, Object> req = body == null ? new HashMap<>() : body;
        boolean allMembers = boolOf(req.get("allMembers"));
        boolean confirm = boolOf(req.get("confirm"));

        List<Map<String, Object>> rawMembers = listOfMaps(req.get("members"));
        List<Number> rawMemberIds = listOfNumbers(req.get("memberIds"));
        int countPerMember = intOf(req.get("countPerMember"), 1);
        if (countPerMember < 1) countPerMember = 1;
        if (countPerMember > MAX_COUNT_PER_MEMBER) {
            return Result.error(BizCode.BAD_REQUEST, "每人张数不能超过 " + MAX_COUNT_PER_MEMBER);
        }

        // 护栏 1：不接受「缺省即全量」
        if (rawMembers.isEmpty() && rawMemberIds.isEmpty() && !allMembers) {
            return Result.error(BizCode.BAD_REQUEST,
                    "请指定发放会员：members（会员+张数）或 memberIds（会员ID列表）；"
                            + "若确实要发给全部会员，请显式传 allMembers=true 且 confirm=true");
        }
        // 护栏 2：全量发放需要二次确认
        if (allMembers && !confirm) {
            return Result.error(BizCode.BAD_REQUEST, "全量发放需要二次确认：请传 confirm=true");
        }
        if (allMembers && (!rawMembers.isEmpty() || !rawMemberIds.isEmpty())) {
            return Result.error(BizCode.BAD_REQUEST, "allMembers 与 members/memberIds 不能同时指定");
        }

        // 计算剩余可发
        int total = batch.getTotalCount() == null ? 0 : batch.getTotalCount();
        if (total <= 0) {
            return Result.error(BizCode.CONFLICT, "批次未设置发放数量（totalCount=0），无法发放");
        }
        int issued = batch.getIssuedCount() == null ? 0 : batch.getIssuedCount();
        int remaining = total - issued;
        if (remaining <= 0) {
            return Result.error(BizCode.CONFLICT, "批次已发完");
        }

        // 解析发放对象 → memberId -> 张数
        LinkedHashMap<Long, Integer> targets = new LinkedHashMap<>();
        if (!rawMembers.isEmpty()) {
            for (Map<String, Object> m : rawMembers) {
                Long memberId = longOf(m.get("memberId"));
                if (memberId == null) {
                    return Result.error(BizCode.BAD_REQUEST, "members[].memberId 必填");
                }
                int cnt = intOf(m.get("count"), countPerMember);
                if (cnt < 1) cnt = 1;
                if (cnt > MAX_COUNT_PER_MEMBER) {
                    return Result.error(BizCode.BAD_REQUEST, "每人张数不能超过 " + MAX_COUNT_PER_MEMBER);
                }
                targets.merge(memberId, cnt, Integer::sum);
            }
        } else if (!rawMemberIds.isEmpty()) {
            for (Number n : rawMemberIds) {
                if (n == null) continue;
                targets.merge(n.longValue(), countPerMember, Integer::sum);
            }
        }

        // 全量发放：取租户内全部 active 会员
        List<Map<String, Object>> memberRows;
        if (targets.isEmpty()) {
            memberRows = jdbc.queryForList(
                    "SELECT id, name FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND status = 1 ORDER BY id",
                    t);
            for (Map<String, Object> m : memberRows) {
                targets.put(((Number) m.get("id")).longValue(), countPerMember);
            }
        } else {
            // 护栏 3：校验发放对象属于当前租户且状态正常
            memberRows = queryMembers(t, new ArrayList<>(targets.keySet()));
            if (memberRows.size() != targets.size()) {
                List<Long> found = new ArrayList<>();
                for (Map<String, Object> m : memberRows) found.add(((Number) m.get("id")).longValue());
                List<String> bad = new ArrayList<>();
                for (Long mid : targets.keySet()) if (!found.contains(mid)) bad.add(String.valueOf(mid));
                return Result.error(BizCode.BAD_REQUEST,
                        "发放对象不存在、不属于当前租户或状态非正常：" + String.join(",", bad));
            }
        }

        int requested = 0;
        for (int c : targets.values()) requested += c;
        if (requested > MAX_GRANT_PER_REQUEST) {
            return Result.error(BizCode.BAD_REQUEST,
                    "单次发放总量不能超过 " + MAX_GRANT_PER_REQUEST + " 张（本次请求 " + requested + " 张）");
        }

        int granted = 0;
        int truncated = 0;
        for (Map<String, Object> m : memberRows) {
            Long memberId = ((Number) m.get("id")).longValue();
            String memberName = m.get("name") == null ? null : m.get("name").toString();
            int want = targets.getOrDefault(memberId, 0);
            for (int i = 0; i < want; i++) {
                if (granted >= remaining) {
                    truncated++;
                    continue;
                }
                MktCoupon c = new MktCoupon();
                c.setTenantId(t);
                c.setStoreId(batch.getStoreId());
                c.setBatchId(batch.getId());
                c.setCouponCode(generateCouponCode(t, batch.getId()));
                c.setMemberId(memberId);
                c.setMemberName(memberName);
                c.setCouponType(batch.getCouponType());
                c.setDiscountAmount(batch.getDiscountAmount());
                c.setDiscountRate(batch.getDiscountRate());
                c.setMinConsume(batch.getMinConsume());
                c.setDiscountScope(batch.getDiscountScope());
                c.setStatus(1);
                // 有效期
                if (batch.getFixedStartDate() != null && batch.getFixedEndDate() != null) {
                    c.setEffectiveDate(batch.getFixedStartDate());
                    c.setExpireDate(batch.getFixedEndDate());
                } else {
                    c.setEffectiveDate(LocalDate.now());
                    c.setExpireDate(LocalDate.now().plusDays(
                            batch.getEffectiveDays() == null ? 30 : batch.getEffectiveDays()));
                }
                c.setGrantedAt(LocalDateTime.now());
                c.setGrantedBy("batch:" + batch.getId());
                couponMapper.insert(c);
                granted++;
            }
        }

        if (granted > 0) {
            // 条件 UPDATE：并发下若剩余配额已被抢占，影响行数为 0 → 抛错回滚
            // COALESCE 兜底：历史行 issued_count / total_count 可能为 NULL，NULL 比较恒为 false 会误报并发冲突
            int rows = jdbc.update(
                    "UPDATE mkt_coupon_batch SET issued_count = COALESCE(issued_count, 0) + ?, update_time = NOW() "
                            + "WHERE id = ? AND tenant_id = ? AND deleted = 0 "
                            + "AND COALESCE(issued_count, 0) + ? <= COALESCE(total_count, 0)",
                    granted, batch.getId(), t, granted);
            if (rows == 0) {
                throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "批次剩余额度被并发抢占，请刷新后重试");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("issuedCount", granted);
        result.put("grantedCount", granted);   // 前端 coupon.vue 读 grantedCount
        result.put("grantedMembers", targets.size());
        result.put("requestedCount", requested);
        result.put("truncatedCount", truncated);
        result.put("remainingCount", remaining - granted);
        return Result.success("已发放 " + granted + " 张", result);
    }

    private List<Map<String, Object>> queryMembers(Long tenantId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return new ArrayList<>();
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < memberIds.size(); i++) {
            if (i > 0) inClause.append(",");
            inClause.append("?");
        }
        inClause.append(")");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(memberIds);
        return jdbc.queryForList(
                "SELECT id, name FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND status = 1 AND id IN " + inClause,
                args.toArray());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object v) {
        if (!(v instanceof List)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<Object>) v) {
            if (o instanceof Map) out.add((Map<String, Object>) o);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Number> listOfNumbers(Object v) {
        if (!(v instanceof List)) return new ArrayList<>();
        List<Number> out = new ArrayList<>();
        for (Object o : (List<Object>) v) {
            if (o instanceof Number) out.add((Number) o);
        }
        return out;
    }

    private boolean boolOf(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return v != null && "true".equalsIgnoreCase(v.toString().trim());
    }

    private Integer intOf(Object v, Integer def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v == null) return def;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Long longOf(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v == null) return null;
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateCouponCode(Long tenantId, Long batchId) {
        // 唯一键 uk_tenant_code(tenant_id, coupon_code)：
        // 仅「时间戳后 8 位 + 随机 4 位」在批量发放时有碰撞风险（32^4 ≈ 1e6，
        // 单批 2000 张的生日碰撞概率不可忽略 → 唯一键冲突导致整批回滚）。
        // 叠加进程内自增序列，保证同一批次内券码严格唯一。
        long seq = COUPON_CODE_SEQ.incrementAndGet() % 100000L;
        return String.format("%08d%05d%s",
                System.currentTimeMillis() % 100000000L,
                seq,
                Keys.randomCode(4));
    }

    // ==================== 过期扫描 ====================

    @PostMapping("/expire-sweep")
    @RequirePermission("mkt:coupon")
    public Result<Map<String, Object>> expireSweep() {
        Long t = tid();
        LocalDateTime sweepStart = LocalDateTime.now();
        int expired = 0;
        int batchesClosed = 0;
        try {
            // 把过期但仍为 1（未使用）的 mkt_coupon 改为 3
            expired = jdbc.update(
                    "UPDATE mkt_coupon SET status = 3, update_time = NOW() " +
                            "WHERE tenant_id = ? AND status = 1 AND expire_date < CURDATE()",
                    t);
            // 把结束日期到了的批次自动收尾
            batchesClosed = jdbc.update(
                    "UPDATE mkt_coupon_batch SET status = 3, update_time = NOW() " +
                            "WHERE tenant_id = ? AND status = 2 AND fixed_end_date < CURDATE()",
                    t);
        } catch (DataAccessException e) {
            // 修复 I060：DB 异常不再 500，记录 audit log 后返回错误
            log.error("[AUDIT] expireSweep failed tenantId={} durationMs={} err={}",
                    t, java.time.Duration.between(sweepStart, LocalDateTime.now()).toMillis(), e.getMessage());
            return Result.error(BizCode.SYSTEM_ERROR, "扫描失败：" + e.getMessage());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("expiredCount", expired);
        result.put("nearExpireCount", 0);
        result.put("batchesClosed", batchesClosed);
        // 修复 I060：加 audit log（slf4j — 不需新建表，最小可行）
        log.info("[AUDIT] expireSweep tenantId={} operator=auto expiredCount={} batchesClosed={} durationMs={}",
                t, expired, batchesClosed,
                java.time.Duration.between(sweepStart, LocalDateTime.now()).toMillis());
        return Result.success("扫描完成", result);
    }

    // ==================== 模板列表（与 /market/coupon/page 同语义，但走 /mkt 命名）====================

    @GetMapping("/page")
    @RequirePermission(value = {"mkt:coupon", "market:coupon:list"}, mode = RequirePermission.Mode.ANY)
    public Result<PageResult<MktCoupon>> couponPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        // 这里走 mkt_coupon（实例）而非 market_coupon（模板）
        LambdaQueryWrapper<MktCoupon> w = new LambdaQueryWrapper<>();
        w.eq(MktCoupon::getTenantId, t);
        if (status != null) w.eq(MktCoupon::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(MktCoupon::getCouponCode, keyword)
                    .or().like(MktCoupon::getCouponName, keyword)
                    .or().eq(MktCoupon::getMemberId, isNumeric(keyword) ? Long.valueOf(keyword) : -1));
        }
        w.orderByDesc(MktCoupon::getId);
        Page<MktCoupon> page = couponMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    private boolean isNumeric(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return !s.isEmpty();
    }

    // ==================== 导出 ====================

    /**
     * 导出券实例 CSV。
     *
     * 修复：原实现返回 Result&lt;List&lt;Map&gt;&gt;（JSON 信封），而前端 views/mkt/coupon.vue
     * 用 responseType:'blob' 下载 → 落到用户手里的是一段 JSON 文本，不是 CSV。
     * 现在直接产出带 UTF-8 BOM 的 CSV 字节流，Excel 打开中文不乱码。
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    @RequirePermission(value = {"mkt:coupon", "market:coupon:list"}, mode = RequirePermission.Mode.ANY)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MktCoupon> w = new LambdaQueryWrapper<>();
        w.eq(MktCoupon::getTenantId, t);
        if (status != null) w.eq(MktCoupon::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(MktCoupon::getCouponCode, keyword)
                    .or().like(MktCoupon::getCouponName, keyword)
                    .or().eq(MktCoupon::getMemberId, isNumeric(keyword) ? Long.valueOf(keyword) : -1));
        }
        w.orderByDesc(MktCoupon::getId);
        List<MktCoupon> rows = couponMapper.selectList(w);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            // UTF-8 BOM：让 Excel 正确识别编码，否则中文列会乱码
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            writer.write("券码,会员ID,会员,券名称,类型,面值,折扣率,最低消费,状态,生效日期,过期日期,发放时间\n");
            for (MktCoupon c : rows) {
                writer.write(csv(c.getCouponCode()) + "," + csv(c.getMemberId()) + "," + csv(c.getMemberName())
                        + "," + csv(c.getCouponName()) + "," + csv(c.getCouponType())
                        + "," + csv(c.getDiscountAmount()) + "," + csv(c.getDiscountRate())
                        + "," + csv(c.getMinConsume()) + "," + csv(c.getStatus())
                        + "," + csv(c.getEffectiveDate()) + "," + csv(c.getExpireDate())
                        + "," + csv(c.getGrantedAt()) + "\n");
            }
            writer.flush();
            HttpHeaders headers = new HttpHeaders();
            String fileName = "coupons-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
            return new ResponseEntity<>(baos.toByteArray(), headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("[AUDIT] coupon export failed tenantId={} err={}", t, e.getMessage());
            throw new BusinessException(BizCode.SYSTEM_ERROR, "导出失败：" + e.getMessage());
        }
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义内部引号 */
    private String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}