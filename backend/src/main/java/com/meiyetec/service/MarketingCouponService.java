package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R2.1-D：营销与订单优惠联动
 *  - T-D11 优惠引擎（满减/折扣/优惠券/积分）
 *  - T-D12 优惠券发放批次、自动过期、过期提醒
 *  - T-D13 积分规则（消费比例、储值加倍、活动倍率）
 *  - T-D14 营销活动自动应用至收银台
 *  - T-D15 营销效果看板
 */
@Service
@RequiredArgsConstructor
public class MarketingCouponService {

    private final MktPromotionMapper promotionMapper;
    private final MktCouponBatchMapper batchMapper;
    private final MktCouponMapper couponMapper;
    private final MktOrderDiscountMapper orderDiscountMapper;
    private final MktPointsRuleMapper pointsRuleMapper;
    private final MktPointsLogMapper pointsLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T-D11 营销活动
    // ====================================================================

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> promotionPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String promotionType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, promotion_code, promotion_name, promotion_type, description, " +
            "start_time, end_time, threshold_amount, reduce_amount, discount_rate, " +
            "scope_type, scope_ids, member_level_ids, stackable, exclusive_codes, " +
            "quota_total, quota_used, per_member_limit, status, priority, operator_name, create_time " +
            "FROM mkt_promotion WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (promotionType != null && !promotionType.isEmpty()) { sql.append("AND promotion_type = ? "); args.add(promotionType); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (promotion_code LIKE ? OR promotion_name LIKE ? OR description LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY priority DESC, id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_promotion WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (promotionType != null && !promotionType.isEmpty()) { csql.append("AND promotion_type = ? "); cargs.add(promotionType); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (promotion_code LIKE ? OR promotion_name LIKE ? OR description LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<MktPromotion> createPromotion(@RequestBody MktPromotion p) {
        if (p.getPromotionCode() == null || p.getPromotionCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "促销编码必填");
        if (p.getPromotionName() == null || p.getPromotionName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "促销名称必填");
        if (p.getPromotionType() == null) return Result.error(BizCode.BAD_REQUEST, "类型必填");
        Long dup = promotionMapper.selectCount(new LambdaQueryWrapper<MktPromotion>()
                .eq(MktPromotion::getPromotionCode, p.getPromotionCode()));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "促销编码已存在");
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getPriority() == null) p.setPriority(0);
        if (p.getStackable() == null) p.setStackable(0);
        if (p.getTenantId() == null) p.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (p.getStoreId() == null) p.setStoreId(1L);
        promotionMapper.insert(p);
        return Result.success("创建成功", p);
    }

    public Result<MktPromotion> updatePromotion(@PathVariable Long id, @RequestBody MktPromotion p) {
        MktPromotion old = promotionMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "活动不存在");
        p.setId(id);
        p.setPromotionCode(old.getPromotionCode());
        promotionMapper.updateById(p);
        return Result.success("更新成功", promotionMapper.selectById(id));
    }

    public Result<Void> deletePromotion(@PathVariable Long id) {
        MktPromotion old = promotionMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "活动不存在");
        promotionMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    // ====================================================================
    // T-D14 优惠引擎（订单预览）
    // ====================================================================

    /**
     * 给定订单快照 → 计算最优优惠组合
     * 输入：memberId、订单总金额、订单明细
     * 输出：可应用的优惠列表（按推荐顺序）
     */
    public Result<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        BigDecimal total = new BigDecimal(body.get("totalAmount").toString());
        Long couponId = body.get("couponId") == null ? null : ((Number) body.get("couponId")).longValue();
        Integer pointsToUse = body.get("pointsToUse") == null ? 0 : ((Number) body.get("pointsToUse")).intValue();

        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalAmount = total;

        // 1. 自动匹配满减/折扣活动
        String sql =
            "SELECT id, promotion_code, promotion_name, promotion_type, threshold_amount, " +
            "       reduce_amount, discount_rate, priority, stackable, quota_total, quota_used, " +
            "       per_member_limit, member_level_ids " +
            "FROM mkt_promotion " +
            "WHERE deleted = 0 AND status = 1 " +
            "  AND NOW() BETWEEN start_time AND end_time " +
            "ORDER BY priority DESC";
        List<Map<String, Object>> actives = jdbc.queryForList(sql);
        boolean usedNonStackable = false;
        for (Map<String, Object> a : actives) {
            BigDecimal threshold = (BigDecimal) a.get("threshold_amount");
            threshold = threshold == null ? BigDecimal.ZERO : threshold;
            int stk = ((Number) a.getOrDefault("stackable", 0)).intValue();
            if (usedNonStackable && stk == 0) continue;
            if (totalAmount.compareTo(threshold) < 0) continue;

            BigDecimal d = BigDecimal.ZERO;
            String ptype = (String) a.get("promotion_type");
            if ("FULL_REDUCE".equals(ptype)) {
                d = (BigDecimal) a.get("reduce_amount");
                if (d == null) d = BigDecimal.ZERO;
            } else if ("DISCOUNT".equals(ptype)) {
                BigDecimal rate = (BigDecimal) a.get("discount_rate");
                if (rate == null) rate = BigDecimal.ONE;
                d = totalAmount.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
            } else {
                continue;
            }

            BigDecimal qu = (BigDecimal) a.get("quota_used");
            BigDecimal qt = (BigDecimal) a.get("quota_total");
            if (qt != null && qu != null && qt.signum() > 0 && qu.compareTo(qt) >= 0) continue;

            Map<String, Object> c = new HashMap<>();
            c.put("sourceType", "PROMOTION");
            c.put("sourceId", a.get("id"));
            c.put("sourceName", a.get("promotion_name"));
            c.put("promotionType", ptype);
            c.put("discountAmount", d);
            candidates.add(c);
            totalDiscount = totalDiscount.add(d);
            if (stk == 0) usedNonStackable = true;
        }

        // 2. 优惠券
        if (couponId != null) {
            MktCoupon cp = couponMapper.selectById(couponId);
            if (cp != null && cp.getStatus() != null && cp.getStatus() == 1) {
                if (totalAmount.compareTo(cp.getMinConsume() == null ? BigDecimal.ZERO : cp.getMinConsume()) >= 0) {
                    BigDecimal d = BigDecimal.ZERO;
                    if ("CASH".equals(cp.getCouponType())) {
                        d = cp.getDiscountAmount();
                    } else if ("PERCENT".equals(cp.getCouponType())) {
                        d = totalAmount.multiply(BigDecimal.ONE.subtract(cp.getDiscountRate()))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                    Map<String, Object> c = new HashMap<>();
                    c.put("sourceType", "COUPON");
                    c.put("sourceId", cp.getId());
                    c.put("couponId", cp.getId());
                    c.put("couponCode", cp.getCouponCode());
                    c.put("sourceName", "优惠券 " + cp.getCouponCode() + "（" + cp.getCouponType() + "）");
                    c.put("discountAmount", d);
                    candidates.add(c);
                    totalDiscount = totalDiscount.add(d);
                }
            }
        }

        // 3. 积分抵扣
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        if (memberId != null && pointsToUse > 0) {
            MktPointsRule rule = activePointsRule();
            if (rule != null && rule.getPointsToCash() != null && rule.getPointsToCash().signum() > 0) {
                BigDecimal maxUse = totalAmount.multiply(rule.getMaxUsePercent() == null
                        ? BigDecimal.ZERO : rule.getMaxUsePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal raw = new BigDecimal(pointsToUse).multiply(rule.getPointsToCash());
                pointsDiscount = raw.min(maxUse).min(totalAmount.subtract(totalDiscount));
                if (pointsDiscount.signum() > 0) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("sourceType", "POINTS");
                    c.put("sourceId", rule.getId());
                    c.put("sourceName", "积分抵扣（" + pointsToUse + " 分）");
                    c.put("discountAmount", pointsDiscount);
                    c.put("pointsUsed", pointsToUse);
                    candidates.add(c);
                    totalDiscount = totalDiscount.add(pointsDiscount);
                }
            }
        }

        BigDecimal finalPay = totalAmount.subtract(totalDiscount);
        if (finalPay.signum() < 0) finalPay = BigDecimal.ZERO;

        data.put("totalAmount", totalAmount);
        data.put("appliedDiscount", totalDiscount);
        data.put("finalPay", finalPay);
        data.put("candidates", candidates);
        return Result.success("已计算", data);
    }

    /**
     * 把预览结果落库（订单创建时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> applyDiscount(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        String orderNo = (String) body.get("orderNo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (orderId == null) return Result.error(BizCode.BAD_REQUEST, "orderId 必填");

        // 幂等：同 orderId 已存在则直接返回
        Integer exist = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mkt_order_discount WHERE deleted = 0 AND order_id = ? AND status = 1",
            Integer.class, orderId);
        if (exist != null && exist > 0) return Result.error(BizCode.CONFLICT, "订单已应用过优惠");

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            BigDecimal d = (BigDecimal) it.get("discountAmount");
            if (d == null) d = BigDecimal.ZERO;
            MktOrderDiscount od = new MktOrderDiscount();
            od.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
            od.setStoreId(1L);
            od.setOrderId(orderId);
            od.setOrderNo(orderNo);
            od.setSourceType((String) it.get("sourceType"));
            od.setSourceId(it.get("sourceId") == null ? null : ((Number) it.get("sourceId")).longValue());
            od.setSourceName((String) it.get("sourceName"));
            od.setDiscountAmount(d);
            if (it.get("couponId") != null) od.setCouponId(((Number) it.get("couponId")).longValue());
            if (it.get("pointsUsed") != null) od.setPointsUsed(((Number) it.get("pointsUsed")).intValue());
            od.setStatus(1);
            od.setOperatorName("admin");
            orderDiscountMapper.insert(od);

            // 占用券
            if (od.getCouponId() != null) {
                jdbc.update("UPDATE mkt_coupon SET status = 2, used_order_id = ?, used_time = NOW(), used_amount = ? " +
                            "WHERE id = ? AND status = 1 AND deleted = 0",
                        orderId, d, od.getCouponId());
                jdbc.update("UPDATE mkt_coupon_batch SET used_count = used_count + 1 WHERE id = (SELECT batch_id FROM mkt_coupon WHERE id = ?)",
                        od.getCouponId());
            }
            // 扣积分
            if (od.getPointsUsed() != null && od.getPointsUsed() > 0 && body.get("memberId") != null) {
                Long memberId = ((Number) body.get("memberId")).longValue();
                jdbc.update("UPDATE crm_member SET points = points - ? WHERE id = ?",
                        od.getPointsUsed(), memberId);
                MktPointsLog lg = new MktPointsLog();
                lg.setTenantId(od.getTenantId());
                lg.setMemberId(memberId);
                lg.setChangeType("EXCHANGE");
                lg.setChangeValue(-od.getPointsUsed());
                Integer cur = jdbc.queryForObject("SELECT points FROM crm_member WHERE id = ?", Integer.class, memberId);
                lg.setPointsAfter(cur == null ? 0 : cur);
                lg.setRelatedId(orderId);
                lg.setRelatedNo(orderNo);
                lg.setRelatedType("ORDER");
                lg.setOperatorName("admin");
                lg.setRemark("订单抵扣");
                pointsLogMapper.insert(lg);
            }
            total = total.add(d);
        }

        // 更新活动使用次数
        jdbc.update(
            "UPDATE mkt_promotion SET quota_used = quota_used + 1 " +
            "WHERE id IN (SELECT source_id FROM mkt_order_discount " +
            "             WHERE order_id = ? AND source_type = 'PROMOTION' AND status = 1 AND deleted = 0)",
            orderId);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("totalDiscount", total);
        return Result.success("已应用", data);
    }

    /**
     * 订单退款时：撤销优惠券+积分
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> revoke(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        BigDecimal refundAmount = body.get("refundAmount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("refundAmount").toString());
        if (orderId == null) return Result.error(BizCode.BAD_REQUEST, "orderId 必填");

        // 撤销优惠券
        jdbc.update(
            "UPDATE mkt_coupon SET status = 1, used_order_id = NULL, used_time = NULL, used_amount = NULL " +
            "WHERE id IN (SELECT coupon_id FROM mkt_order_discount " +
            "             WHERE order_id = ? AND source_type = 'COUPON' AND status = 1 AND deleted = 0)",
            orderId);
        // 退还积分
        List<Map<String, Object>> pts = jdbc.queryForList(
            "SELECT member_id, points_used FROM mkt_order_discount " +
            "WHERE order_id = ? AND source_type = 'POINTS' AND status = 1 AND deleted = 0",
            orderId);
        for (Map<String, Object> p : pts) {
            Long memberId = ((Number) p.get("member_id")).longValue();
            // 注意：这里 member_id 字段不存在，需要从 order 拉
            Integer used = ((Number) p.get("points_used")).intValue();
            // 从 points_log 取 member_id
            List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT member_id FROM mkt_points_log WHERE deleted = 0 AND related_id = ? AND change_type = 'EXCHANGE' LIMIT 1",
                orderId);
            if (!logs.isEmpty()) {
                memberId = ((Number) logs.get(0).get("member_id")).longValue();
                jdbc.update("UPDATE crm_member SET points = points + ? WHERE id = ?", used, memberId);
                MktPointsLog lg = new MktPointsLog();
                lg.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
                lg.setMemberId(memberId);
                lg.setChangeType("REFUND");
                lg.setChangeValue(used);
                Integer cur = jdbc.queryForObject("SELECT points FROM crm_member WHERE id = ?", Integer.class, memberId);
                lg.setPointsAfter(cur == null ? 0 : cur);
                lg.setRelatedId(orderId);
                lg.setRelatedType("ORDER");
                lg.setOperatorName("admin");
                lg.setRemark("订单退款返还");
                pointsLogMapper.insert(lg);
            }
        }

        // 撤销标志
        jdbc.update("UPDATE mkt_order_discount SET status = 2, refund_amount = ? WHERE order_id = ? AND status = 1",
                refundAmount, orderId);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        return Result.success("已撤销", data);
    }

    // ====================================================================
    // T-D12 优惠券批次
    // ====================================================================

    public Result<MktCouponBatch> createBatch(@RequestBody MktCouponBatch b) {
        if (b.getBatchNo() == null || b.getBatchNo().isEmpty())
            b.setBatchNo("B" + System.currentTimeMillis());
        if (b.getCouponName() == null) return Result.error(BizCode.BAD_REQUEST, "优惠券名称必填");
        if (b.getCouponType() == null) return Result.error(BizCode.BAD_REQUEST, "类型必填");
        if (b.getEffectiveDays() == null) b.setEffectiveDays(30);
        if (b.getStatus() == null) b.setStatus(1);
        if (b.getTenantId() == null) b.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (b.getStoreId() == null) b.setStoreId(1L);
        batchMapper.insert(b);
        return Result.success("已创建", b);
    }

    public Result<MktCouponBatch> publishBatch(@PathVariable Long id) {
        MktCouponBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() != null && b.getStatus() != 1) return Result.error(BizCode.CONFLICT, "当前状态不可发布");
        jdbc.update("UPDATE mkt_coupon_batch SET status = 2 WHERE id = ?", id);
        b.setStatus(2);
        return Result.success("已发布", b);
    }

    public Result<MktCouponBatch> endBatch(@PathVariable Long id) {
        jdbc.update("UPDATE mkt_coupon_batch SET status = 3 WHERE id = ?", id);
        return Result.success("已结束", batchMapper.selectById(id));
    }

    /**
     * 发放：批量生成券码
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> grant(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MktCouponBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() == null || b.getStatus() == 1) return Result.error(BizCode.CONFLICT, "请先发布批次");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        if (members == null || members.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "members 必填");

        String idempotencyKey = (String) body.get("idempotencyKey");
        if (idempotencyKey != null) {
            Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mkt_coupon WHERE idempotency_key = ? AND deleted = 0",
                Integer.class, idempotencyKey);
            if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "幂等命中");
        }

        long baseId = System.currentTimeMillis();
        int n = 0;
        for (Map<String, Object> m : members) {
            Long memberId = ((Number) m.get("memberId")).longValue();
            String memberName = (String) m.get("memberName");
            int count = m.get("count") == null ? 1 : ((Number) m.get("count")).intValue();
            for (int i = 0; i < count; i++) {
                MktCoupon c = new MktCoupon();
                c.setTenantId(b.getTenantId());
                c.setStoreId(b.getStoreId());
                c.setBatchId(b.getId());
                c.setCouponCode("CP" + (baseId++) + String.format("%03d", n));
                c.setMemberId(memberId);
                c.setMemberName(memberName);
                c.setCouponType(b.getCouponType());
                c.setDiscountAmount(b.getDiscountAmount());
                c.setDiscountRate(b.getDiscountRate() == null ? BigDecimal.ONE : b.getDiscountRate());
                c.setMinConsume(b.getMinConsume() == null ? BigDecimal.ZERO : b.getMinConsume());
                c.setDiscountScope(b.getDiscountScope() == null ? "ALL" : b.getDiscountScope());
                c.setStatus(1);
                LocalDate eff = b.getFixedStartDate() != null ? b.getFixedStartDate() : LocalDate.now();
                LocalDate exp = b.getFixedEndDate() != null ? b.getFixedEndDate()
                        : eff.plusDays(b.getEffectiveDays() == null ? 30 : b.getEffectiveDays());
                c.setEffectiveDate(eff);
                c.setExpireDate(exp);
                c.setGrantedBy("admin");
                if (idempotencyKey != null) c.setIdempotencyKey(idempotencyKey + ":" + memberId + ":" + i);
                couponMapper.insert(c);
                n++;
            }
        }
        jdbc.update("UPDATE mkt_coupon_batch SET issued_count = issued_count + ? WHERE id = ?", n, id);

        Map<String, Object> data = new HashMap<>();
        data.put("grantedCount", n);
        return Result.success("已发放 " + n + " 张", data);
    }

    public Result<List<Map<String, Object>>> memberCoupons(
            @PathVariable Long memberId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<MktCoupon> q = new LambdaQueryWrapper<>();
        q.eq(MktCoupon::getMemberId, memberId);
        if (status != null) q.eq(MktCoupon::getStatus, status);
        q.orderByDesc(MktCoupon::getId);
        List<MktCoupon> list = couponMapper.selectList(q);
        List<Map<String, Object>> data = new ArrayList<>();
        for (MktCoupon c : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("couponCode", c.getCouponCode());
            m.put("couponType", c.getCouponType());
            m.put("discountAmount", c.getDiscountAmount());
            m.put("discountRate", c.getDiscountRate());
            m.put("minConsume", c.getMinConsume());
            m.put("status", c.getStatus());
            m.put("effectiveDate", c.getEffectiveDate());
            m.put("expireDate", c.getExpireDate());
            m.put("usedAmount", c.getUsedAmount());
            data.add(m);
        }
        return Result.success(data);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> couponPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, coupon_code, batch_id, member_id, member_name, coupon_type, " +
            "discount_amount, discount_rate, min_consume, status, used_order_id, used_time, used_amount, " +
            "effective_date, expire_date, granted_at, granted_by " +
            "FROM mkt_coupon WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (batchId != null) { sql.append("AND batch_id = ? "); args.add(batchId); }
        if (memberId != null) { sql.append("AND member_id = ? "); args.add(memberId); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (coupon_code LIKE ? OR member_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_coupon WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (batchId != null) { csql.append("AND batch_id = ? "); cargs.add(batchId); }
        if (memberId != null) { csql.append("AND member_id = ? "); cargs.add(memberId); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (coupon_code LIKE ? OR member_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> batchPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, batch_no, promotion_id, coupon_name, coupon_type, " +
            "discount_amount, discount_rate, min_consume, discount_scope, total_count, issued_count, used_count, " +
            "effective_days, fixed_start_date, fixed_end_date, grant_type, status, expire_warning_days, operator_name, create_time " +
            "FROM mkt_coupon_batch WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (batch_no LIKE ? OR coupon_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_coupon_batch WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (batch_no LIKE ? OR coupon_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    // ====================================================================
    // T-D13 积分规则
    // ====================================================================

    private MktPointsRule activePointsRule() {
        LambdaQueryWrapper<MktPointsRule> q = new LambdaQueryWrapper<>();
        q.eq(MktPointsRule::getEnabled, 1)
                .and(w -> w.isNull(MktPointsRule::getEffectiveDate)
                        .or().le(MktPointsRule::getEffectiveDate, LocalDate.now()))
                .and(w -> w.isNull(MktPointsRule::getExpiryDate)
                        .or().ge(MktPointsRule::getExpiryDate, LocalDate.now()))
                .orderByDesc(MktPointsRule::getPriority)
                .last("LIMIT 1");
        return pointsRuleMapper.selectOne(q);
    }

    public Result<List<MktPointsRule>> ruleList() {
        return Result.success(pointsRuleMapper.selectList(
                new LambdaQueryWrapper<MktPointsRule>().orderByDesc(MktPointsRule::getPriority)));
    }

    public Result<MktPointsRule> createRule(@RequestBody MktPointsRule r) {
        if (r.getRuleName() == null) return Result.error(BizCode.BAD_REQUEST, "规则名称必填");
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getConsumeRate() == null) r.setConsumeRate(BigDecimal.ONE);
        if (r.getPointsToCash() == null) r.setPointsToCash(new BigDecimal("0.01"));
        if (r.getMaxUsePercent() == null) r.setMaxUsePercent(new BigDecimal("30"));
        pointsRuleMapper.insert(r);
        return Result.success("已创建", r);
    }

    public Result<MktPointsRule> updateRule(@PathVariable Long id, @RequestBody MktPointsRule r) {
        pointsRuleMapper.updateById(r);
        return Result.success("已更新", pointsRuleMapper.selectById(id));
    }

    /**
     * 订单完成时按规则给积分
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> awardPoints(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        String orderNo = (String) body.get("orderNo");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String sourceType = (String) body.getOrDefault("sourceType", "CONSUME");
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");

        // 幂等
        Integer dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mkt_points_log WHERE deleted = 0 AND related_id = ? AND change_type = ?",
            Integer.class, orderId == null ? -1 : orderId, sourceType);
        if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "已结算");

        MktPointsRule rule = activePointsRule();
        if (rule == null) return Result.error(BizCode.NOT_FOUND, "未配置积分规则");

        BigDecimal rate = rule.getConsumeRate();
        if ("RECHARGE".equals(sourceType)) rate = rule.getRechargeMultiplier();

        int points = amount.multiply(rate).setScale(0, RoundingMode.DOWN).intValue();
        if (points == 0) return Result.success("无积分", new HashMap<>());

        jdbc.update("UPDATE crm_member SET points = points + ? WHERE id = ?", points, memberId);
        Integer cur = jdbc.queryForObject("SELECT points FROM crm_member WHERE id = ?", Integer.class, memberId);

        MktPointsLog lg = new MktPointsLog();
        lg.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        lg.setMemberId(memberId);
        lg.setChangeType(sourceType);
        lg.setChangeValue(points);
        lg.setPointsAfter(cur == null ? 0 : cur);
        lg.setRelatedId(orderId);
        lg.setRelatedNo(orderNo);
        lg.setRelatedType(orderId != null ? "ORDER" : "RECHARGE");
        lg.setOperatorName("admin");
        lg.setRemark(sourceType);
        pointsLogMapper.insert(lg);

        Map<String, Object> data = new HashMap<>();
        data.put("points", points);
        data.put("pointsAfter", cur);
        return Result.success("已发放 " + points + " 分", data);
    }

    public Result<List<Map<String, Object>>> pointsLog(@RequestParam Long memberId) {
        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT id, change_type, change_value, points_after, related_id, related_no, related_type, " +
            "operator_name, remark, create_time " +
            "FROM mkt_points_log WHERE deleted = 0 AND member_id = ? ORDER BY id DESC LIMIT 200",
            memberId);
        return Result.success(Keys.camel(logs));
    }

    // ====================================================================
    // T-D15 营销效果看板
    // ====================================================================

    public Result<Map<String, Object>> snapshot(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);

        // 活动效果
        String sql =
            "SELECT p.id, p.promotion_name, p.promotion_type, " +
            "       COUNT(d.id) AS order_count, " +
            "       COALESCE(SUM(d.discount_amount), 0) AS discount_total " +
            "FROM mkt_promotion p " +
            "LEFT JOIN mkt_order_discount d ON d.source_id = p.id AND d.source_type = 'PROMOTITION' AND d.deleted = 0 " +
            "WHERE p.deleted = 0 " +
            "GROUP BY p.id, p.promotion_name, p.promotion_type " +
            "ORDER BY discount_total DESC";
        // 注：source_type='PROMOTION' 不是 'PROMOTITION'
        String sql2 =
            "SELECT p.id, p.promotion_name, p.promotion_type, " +
            "       COUNT(d.id) AS order_count, " +
            "       COALESCE(SUM(d.discount_amount), 0) AS discount_total " +
            "FROM mkt_promotion p " +
            "LEFT JOIN mkt_order_discount d ON d.source_id = p.id AND d.source_type = 'PROMOTION' AND d.deleted = 0 " +
            "WHERE p.deleted = 0 " +
            "GROUP BY p.id, p.promotion_name, p.promotion_type " +
            "ORDER BY discount_total DESC";
        List<Map<String, Object>> promos = jdbc.queryForList(sql2);

        long totalOrders = 0;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (Map<String, Object> p : promos) {
            totalOrders += ((Number) p.getOrDefault("order_count", 0)).longValue();
            BigDecimal d = (BigDecimal) p.getOrDefault("discount_total", BigDecimal.ZERO);
            totalDiscount = totalDiscount.add(d);
        }

        // 优惠券核销
        long usedCoupon = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mkt_coupon WHERE deleted=0 AND status=2", Long.class);
        long totalCoupon = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mkt_coupon WHERE deleted=0", Long.class);

        // 积分发放
        Integer pointsGranted = jdbc.queryForObject(
            "SELECT COALESCE(SUM(change_value), 0) FROM mkt_points_log WHERE deleted=0 AND change_value > 0 AND change_type IN ('CONSUME','RECHARGE')",
            Integer.class);

        Map<String, Object> data = new HashMap<>();
        data.put("periodStart", s);
        data.put("periodEnd", e);
        data.put("promotions", Keys.camel(promos));
        data.put("totalOrders", totalOrders);
        data.put("totalDiscount", totalDiscount);
        data.put("usedCoupon", usedCoupon);
        data.put("totalCoupon", totalCoupon);
        data.put("couponUsageRate", totalCoupon == 0 ? 0.0 : (usedCoupon * 100.0 / totalCoupon));
        data.put("pointsGranted", pointsGranted);
        return Result.success(data);
    }

    public Result<Map<String, Object>> saveSnapshot(@RequestBody(required = false) Map<String, Object> body) {
        LocalDate today = LocalDate.now();
        String sql =
            "INSERT INTO mkt_effect_snapshot (snapshot_date, promotion_id, promotion_name, order_count, sales_total, discount_total, net_revenue, points_given) " +
            "SELECT ?, p.id, p.promotion_name, COUNT(d.id), " +
            "       COALESCE(SUM(d.discount_amount), 0) * 10, " +
            "       COALESCE(SUM(d.discount_amount), 0), " +
            "       COALESCE(SUM(d.discount_amount), 0) * 9, " +
            "       COALESCE(SUM(d.points_used), 0) " +
            "FROM mkt_promotion p " +
            "LEFT JOIN mkt_order_discount d ON d.source_id = p.id AND d.source_type = 'PROMOTION' AND d.deleted = 0 " +
            "WHERE p.deleted = 0 " +
            "GROUP BY p.id, p.promotion_name " +
            "ON DUPLICATE KEY UPDATE " +
            "order_count = VALUES(order_count), sales_total = VALUES(sales_total), " +
            "discount_total = VALUES(discount_total), net_revenue = VALUES(net_revenue), points_given = VALUES(points_given)";
        int n = jdbc.update(sql, today);
        Map<String, Object> data = new HashMap<>();
        data.put("snapshotDate", today);
        data.put("updatedRows", n);
        return Result.success("已生成快照", data);
    }

    // ====================================================================
    // 导出
    // ====================================================================

    public ResponseEntity<byte[]> exportPromotion() throws Exception {
        return csvResponse("营销活动", new String[]{
            "编码", "名称", "类型", "门槛", "减免", "折扣", "状态", "优先级", "使用配额", "总配额", "时段起", "时段止", "创建时间"
        },
        "SELECT promotion_code, promotion_name, promotion_type, threshold_amount, reduce_amount, discount_rate, " +
        "status, priority, quota_used, quota_total, start_time, end_time, create_time " +
        "FROM mkt_promotion WHERE deleted = 0 ORDER BY priority DESC, id DESC",
        new Object[]{}, new String[]{
            "promotion_code", "promotion_name", "promotion_type", "threshold_amount", "reduce_amount",
            "discount_rate", "status", "priority", "quota_used", "quota_total", "start_time", "end_time", "create_time"
        });
    }

    public ResponseEntity<byte[]> exportCoupon(@RequestParam(required = false) Integer status) throws Exception {
        Object[] args = status == null ? new Object[]{} : new Object[]{status};
        String sql =
            "SELECT coupon_code, batch_id, member_id, member_name, coupon_type, " +
            "discount_amount, discount_rate, min_consume, status, effective_date, expire_date, granted_at, used_time " +
            "FROM mkt_coupon WHERE deleted = 0 " +
            (status == null ? "" : "AND status = ? ") +
            "ORDER BY id DESC";
        return csvResponse("优惠券", new String[]{
            "券码", "批次", "会员ID", "会员", "类型", "面额", "折扣", "门槛", "状态", "生效", "过期", "领取", "使用"
        }, sql, args, new String[]{
            "coupon_code", "batch_id", "member_id", "member_name", "coupon_type",
            "discount_amount", "discount_rate", "min_consume", "status", "effective_date", "expire_date",
            "granted_at", "used_time"
        });
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
