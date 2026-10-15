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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final MktCouponBatchMapper batchMapper;
    private final MktCouponMapper couponMapper;
    private final JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    // ==================== 批次 ====================

    @PostMapping("/batch/page")
    public Result<PageResult<MktCouponBatch>> batchPage(@RequestBody Map<String, Object> params) {
        Long t = tid();
        int pageNum = (int) params.getOrDefault("pageNum", 1);
        int pageSize = (int) params.getOrDefault("pageSize", 20);
        String keyword = params.get("keyword") == null ? null : params.get("keyword").toString();
        Integer status = params.get("status") == null ? null : ((Number) params.get("status")).intValue();

        LambdaQueryWrapper<MktCouponBatch> w = new LambdaQueryWrapper<>();
        w.eq(MktCouponBatch::getTenantId, t);
        if (status != null) w.eq(MktCouponBatch::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(MktCouponBatch::getBatchNo, keyword)
                    .or().like(MktCouponBatch::getCouponName, keyword));
        }
        w.orderByDesc(MktCouponBatch::getId);
        Page<MktCouponBatch> page = batchMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/batch")
    @Transactional(rollbackFor = Exception.class)
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

    @PostMapping("/batch/{id}/grant")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> grantBatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long t = tid();
        MktCouponBatch batch = batchMapper.selectById(id);
        if (batch == null || !t.equals(batch.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "批次不存在");
        }
        if (batch.getStatus() == null || batch.getStatus() != 2) {
            return Result.error(BizCode.CONFLICT, "批次未发布，无法发放");
        }
        // memberIds: list；缺则发放给全部 active 会员
        @SuppressWarnings("unchecked")
        List<Number> rawMemberIds = body == null ? null : (List<Number>) body.get("memberIds");
        Integer countPerMember = body == null || body.get("countPerMember") == null ? 1
                : ((Number) body.get("countPerMember")).intValue();
        if (countPerMember <= 0) countPerMember = 1;

        // 计算剩余可发
        int remaining = (batch.getTotalCount() == null ? 0 : batch.getTotalCount())
                - (batch.getIssuedCount() == null ? 0 : batch.getIssuedCount());
        if (remaining <= 0) {
            return Result.error(BizCode.CONFLICT, "批次已发完");
        }

        StringBuilder memberSql = new StringBuilder("SELECT id, name FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND status = 1 ");
        List<Object> memberArgs = new ArrayList<>();
        memberArgs.add(t);
        if (rawMemberIds != null && !rawMemberIds.isEmpty()) {
            List<Long> midList = new ArrayList<>();
            for (Number n : rawMemberIds) midList.add(n.longValue());
            StringBuilder inClause = new StringBuilder("(");
            for (int i = 0; i < midList.size(); i++) {
                if (i > 0) inClause.append(",");
                inClause.append("?");
            }
            inClause.append(")");
            memberSql.append("AND id IN ").append(inClause);
            memberArgs.addAll(midList);
        }
        List<Map<String, Object>> members = jdbc.queryForList(memberSql.toString(), memberArgs.toArray());

        int issued = 0;
        for (Map<String, Object> m : members) {
            if (issued >= remaining) break;
            Long memberId = ((Number) m.get("id")).longValue();
            String memberName = m.get("name") == null ? null : m.get("name").toString();
            for (int i = 0; i < countPerMember && issued < remaining; i++) {
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
                issued++;
            }
        }

        // 更新批次 issued_count
        batch.setIssuedCount((batch.getIssuedCount() == null ? 0 : batch.getIssuedCount()) + issued);
        batchMapper.updateById(batch);

        Map<String, Object> result = new HashMap<>();
        result.put("issuedCount", issued);
        result.put("grantedMembers", members.size());
        return Result.success("已发放 " + issued + " 张", result);
    }

    private String generateCouponCode(Long tenantId, Long batchId) {
        // 12 位：时间戳后 8 + 随机 4
        return System.currentTimeMillis() % 100000000L + Keys.randomCode(4);
    }

    // ==================== 过期扫描 ====================

    @PostMapping("/expire-sweep")
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

    @GetMapping("/export")
    public Result<List<Map<String, Object>>> export(
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MktCoupon> w = new LambdaQueryWrapper<>();
        w.eq(MktCoupon::getTenantId, t);
        if (status != null) w.eq(MktCoupon::getStatus, status);
        w.orderByDesc(MktCoupon::getId);
        List<MktCoupon> rows = couponMapper.selectList(w);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (MktCoupon c : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("couponCode", c.getCouponCode());
            m.put("memberId", c.getMemberId());
            m.put("memberName", c.getMemberName());
            m.put("couponType", c.getCouponType());
            m.put("discountAmount", c.getDiscountAmount());
            m.put("discountRate", c.getDiscountRate());
            m.put("status", c.getStatus());
            m.put("effectiveDate", c.getEffectiveDate());
            m.put("expireDate", c.getExpireDate());
            m.put("grantedAt", c.getGrantedAt());
            out.add(m);
        }
        return Result.success(out);
    }
}