package com.meiyetec.service;

import com.meiyetec.entity.AiLtvModel;
import com.meiyetec.entity.CrmLtvSnapshot;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AiLtvModelMapper;
import com.meiyetec.mapper.CrmLtvSnapshotMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P3 · T-P02：LTV 与复购概率
 *  - LTV 实时计算（基于历史 BG/NBD 简化版）
 *  - 复购概率（30/90/180 天）
 *  - 批量计算 + 模型管理
 */
@RestController
@RequestMapping("/intel/ltv")
@RequiredArgsConstructor
public class LtvService {

    private final CrmLtvSnapshotMapper ltvSnapshotMapper;
    private final AiLtvModelMapper ltvModelMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 单会员 LTV 计算（简化 BG/NBD 模型）
    // ====================================================================

    @GetMapping("/compute/{memberId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeForMember(@PathVariable Long memberId) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();

        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, tenant_id FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // 历史消费
        Map<String, Object> stats = jdbc.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total_orders, " +
            "  COALESCE(SUM(paid_amount), 0) AS historical_ltv, " +
            "  COALESCE(AVG(paid_amount), 0) AS aov, " +
            "  COALESCE(DATEDIFF(NOW(), MAX(pay_time)), 9999) AS last_purchase_days, " +
            "  COALESCE(DATEDIFF(NOW(), MIN(pay_time)), 0) AS tenure_days " +
            "FROM pos_order WHERE member_id = ? AND status = 2 AND deleted = 0", memberId);

        int totalOrders = ((Number) stats.get("total_orders")).intValue();
        BigDecimal historicalLtv = new BigDecimal(stats.get("historical_ltv").toString());
        BigDecimal aov = new BigDecimal(stats.get("aov").toString());
        int lastPurchaseDays = ((Number) stats.get("last_purchase_days")).intValue();
        int tenureDays = ((Number) stats.get("tenure_days")).intValue();

        // 简化 BG/NBD：
        // 月均消费频次 f = totalOrders / (tenureDays/30)
        // 衰减系数：lastPurchaseDays 越大复购概率越低
        double months = Math.max(tenureDays / 30.0, 1.0);
        double monthlyFreq = totalOrders / months;
        // 复购概率 sigmoid-like：f 越大、lastPurchaseDays 越小 → 越高
        double prob30 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays, 30);
        double prob90 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays, 90);
        double prob180 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays, 180);

        // LTV 预测
        BigDecimal ltv30 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                .multiply(BigDecimal.valueOf(prob30))
                .multiply(BigDecimal.valueOf(1))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ltv90 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                .multiply(BigDecimal.valueOf(3))
                .multiply(BigDecimal.valueOf(prob90))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ltv365 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                .multiply(BigDecimal.valueOf(12))
                .multiply(BigDecimal.valueOf(prob180))
                .setScale(2, RoundingMode.HALF_UP);
        // 终身 LTV：12 * ltv365 / 期望活跃月数（按行业 36 月）
        BigDecimal ltvLifetime = ltv365.multiply(BigDecimal.valueOf(3));

        // 取激活的模型
        AiLtvModel model = ltvModelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiLtvModel>()
                .eq(AiLtvModel::getIsActive, 1)
                .eq(AiLtvModel::getEnabled, 1)
                .last("LIMIT 1"));
        String modelVersion = model != null ? model.getModelCode() : "DEFAULT-V1";

        CrmLtvSnapshot snap = new CrmLtvSnapshot();
        snap.setTenantId(tenantId);
        snap.setBrandId(null); // brand_id column not exist in crm_member
        snap.setMemberId(memberId);
        snap.setHistoricalLtv(historicalLtv);
        snap.setPredictedLtv30d(ltv30);
        snap.setPredictedLtv90d(ltv90);
        snap.setPredictedLtv365d(ltv365);
        snap.setPredictedLtvLifetime(ltvLifetime);
        snap.setRepurchaseProb30d(BigDecimal.valueOf(prob30).setScale(4, RoundingMode.HALF_UP));
        snap.setRepurchaseProb90d(BigDecimal.valueOf(prob90).setScale(4, RoundingMode.HALF_UP));
        snap.setRepurchaseProb180d(BigDecimal.valueOf(prob180).setScale(4, RoundingMode.HALF_UP));
        snap.setModelType("BG");
        snap.setModelVersion(modelVersion);
        snap.setAvgOrderValue(aov);
        snap.setPurchaseFrequency(BigDecimal.valueOf(monthlyFreq).setScale(2, RoundingMode.HALF_UP));
        snap.setLastPurchaseDays(lastPurchaseDays);
        snap.setTotalOrders(totalOrders);
        snap.setSnapshotDate(LocalDate.now());
        snap.setCalcDurationMs((int) (System.currentTimeMillis() - t0));
        snap.setOperatorName("online");
        ltvSnapshotMapper.insert(snap);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("historicalLtv", historicalLtv);
        data.put("predictedLtv30d", ltv30);
        data.put("predictedLtv90d", ltv90);
        data.put("predictedLtv365d", ltv365);
        data.put("predictedLtvLifetime", ltvLifetime);
        data.put("repurchaseProb30d", prob30);
        data.put("repurchaseProb90d", prob90);
        data.put("repurchaseProb180d", prob180);
        data.put("modelVersion", modelVersion);
        data.put("avgOrderValue", aov);
        data.put("monthlyFreq", monthlyFreq);
        data.put("durationMs", snap.getCalcDurationMs());
        data.put("lastPurchaseDays", lastPurchaseDays);
        double p365 = ltv365.doubleValue();
        data.put("bucket", p365 >= 10000 ? "HIGH" : p365 >= 3000 ? "MID" : "LOW");
        return Result.success(data);
    }

    /**
     * 复购概率：sigmoid 曲线
     * 距离上次消费天数越远，概率指数衰减；月频次越高，概率提升
     */
    private double computeRepurchaseProb(double monthlyFreq, int lastPurchaseDays, int horizonDays) {
        if (lastPurchaseDays >= 9999) return 0.05;
        // 衰减系数
        double decay = Math.exp(-lastPurchaseDays / 60.0);
        // 月频次影响（log）
        double freqFactor = Math.log1p(monthlyFreq) / Math.log(10);
        // 时间窗口内概率
        double baseProb = Math.min(1.0, 0.1 + 0.3 * freqFactor + 0.5 * decay);
        // horizonDays 范围内累计
        double horizonProb = 1 - Math.pow(1 - baseProb, horizonDays / 30.0);
        return Math.max(0.01, Math.min(0.99, horizonProb));
    }

    // ====================================================================
    // 批量 LTV 计算
    // ====================================================================

    @PostMapping("/batch/compute")
    public Result<Map<String, Object>> batchCompute(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        int limit = body == null || body.get("limit") == null ? 5000
                : ((Number) body.get("limit")).intValue();

        StringBuilder sql = new StringBuilder(
            "SELECT id FROM crm_member WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        sql.append("LIMIT ?");
        args.add(limit);

        List<Long> memberIds = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
        int success = 0, fail = 0;
        for (Long mid : memberIds) {
            try {
                computeForMember(mid);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", memberIds.size());
        data.put("success", success);
        data.put("fail", fail);
        data.put("durationMs", (int) (System.currentTimeMillis() - t0));
        return Result.success(data);
    }

    // ====================================================================
    // 模型管理
    // ====================================================================

    @PostMapping("/model")
    public Result<AiLtvModel> createModel(@RequestBody AiLtvModel m) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (m.getModelCode() == null || m.getModelName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (m.getEnabled() == null) m.setEnabled(1);
        if (m.getStatus() == null) m.setStatus(1);
        m.setTenantId(tenantId);
        ltvModelMapper.insert(m);
        return Result.success("已创建", m);
    }

    @PostMapping("/model/{id}/deploy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deployModel(@PathVariable Long id) {
        AiLtvModel target = ltvModelMapper.selectById(id);
        if (target == null) return Result.error(BizCode.NOT_FOUND, "模型不存在");
        // 取消同租户其他模型的激活
        jdbc.update("UPDATE ai_ltv_model SET is_active = 0 WHERE tenant_id = ? AND deleted = 0", target.getTenantId());
        jdbc.update("UPDATE ai_ltv_model SET is_active = 1, status = 2 WHERE id = ?", id);
        return Result.success("已部署", null);
    }

    @GetMapping("/model/list")
    public Result<List<AiLtvModel>> modelList() {
        return Result.success(ltvModelMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiLtvModel>()
                .eq(AiLtvModel::getTenantId, TenantContextHolder.get().getTenantId())
                .orderByDesc(AiLtvModel::getIsActive)
                .orderByDesc(AiLtvModel::getId)));
    }

    // ====================================================================
    // 汇总统计
    // ====================================================================

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();

        // 总 LTV 池
        BigDecimal totalLtv = jdbc.queryForObject(
            "SELECT COALESCE(SUM(historical_ltv), 0) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?)",
            BigDecimal.class, tenantId, tenantId);
        BigDecimal totalPredicted = jdbc.queryForObject(
            "SELECT COALESCE(SUM(predicted_ltv_365d), 0) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?)",
            BigDecimal.class, tenantId, tenantId);

        Long memberCount = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT member_id) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?)",
            Long.class, tenantId, tenantId);

        Double avgProb30 = jdbc.queryForObject(
            "SELECT COALESCE(AVG(repurchase_prob_30d), 0) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?)",
            Double.class, tenantId, tenantId);

        // 分桶：高/中/低 LTV
        List<Map<String, Object>> dist = jdbc.queryForList(
            "SELECT " +
            "  CASE " +
            "    WHEN predicted_ltv_365d >= 10000 THEN 'HIGH' " +
            "    WHEN predicted_ltv_365d >= 3000  THEN 'MID' " +
            "    ELSE 'LOW' END AS bucket, " +
            "  COUNT(*) AS cnt, " +
            "  ROUND(AVG(predicted_ltv_365d), 2) AS avg_ltv " +
            "FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?) " +
            "GROUP BY bucket", tenantId, tenantId);

        data.put("totalLtv", totalLtv);
        data.put("totalPredicted365d", totalPredicted);
        data.put("memberCount", memberCount);
        data.put("avgRepurchaseProb30d", avgProb30 == null ? 0 : Math.round(avgProb30 * 1000) / 10.0);
        data.put("ltvDistribution", dist);
        return Result.success(data);
    }

    // ====================================================================
    // A-2：月度趋势（最近 12 个月，历史 LTV vs 预测 LTV）
    // ====================================================================

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 每月最新一条 snapshot 求和（用 snapshot_date 所在月）
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT DATE_FORMAT(snapshot_date, '%Y-%m') AS ym, " +
            "  ROUND(SUM(historical_ltv), 2) AS hist, " +
            "  ROUND(SUM(predicted_ltv_365d), 2) AS pred " +
            "FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? " +
            "  AND snapshot_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
            "GROUP BY ym ORDER BY ym", tenantId);

        // 若不足 12 个月，用 0 补齐
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Map<String, Object> r : rows) have.add((String) r.get("ym"));
        List<Map<String, Object>> full = new ArrayList<>();
        java.time.YearMonth now = java.time.YearMonth.now();
        DateTimeFormatter ymf = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 11; i >= 0; i--) {
            String ym = now.minusMonths(i).format(ymf);
            Map<String, Object> m = new HashMap<>();
            m.put("ym", ym);
            m.put("hist", 0);
            m.put("pred", 0);
            for (Map<String, Object> r : rows) {
                if (ym.equals(r.get("ym"))) {
                    m.put("hist", r.get("hist"));
                    m.put("pred", r.get("pred"));
                }
            }
            full.add(m);
        }
        return Result.success(full);
    }

    // ====================================================================
    // A-3：LTV TOP N 会员
    // ====================================================================

    @GetMapping("/top")
    public Result<List<Map<String, Object>>> top(@RequestParam(defaultValue = "10") int limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (limit < 1 || limit > 100) limit = 10;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.member_id, m.name AS member_name, m.phone, " +
            "  s.predicted_ltv_365d AS pred365, " +
            "  s.predicted_ltv_lifetime AS predLife, " +
            "  s.repurchase_prob_30d AS prob30, " +
            "  s.snapshot_date " +
            "FROM crm_ltv_snapshot s " +
            "LEFT JOIN crm_member m ON m.id = s.member_id AND m.deleted = 0 " +
            "WHERE s.tenant_id = ? " +
            "  AND s.snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?) " +
            "ORDER BY s.predicted_ltv_365d DESC LIMIT ?",
            tenantId, tenantId, limit);
        return Result.success(rows);
    }

    // ====================================================================
    // B-2：会员基本信息（姓名/手机/品牌/入会时间）
    // ====================================================================

    @GetMapping("/member/{memberId}")
    public Result<Map<String, Object>> memberInfo(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT m.id, m.name, m.phone, " +
            "  m.gender, m.birthday, m.join_date, m.level_id, " +
            "  (SELECT COUNT(*) FROM pos_order o WHERE o.member_id = m.id AND o.status = 2 AND o.deleted = 0) AS total_orders, " +
            "  (SELECT COALESCE(SUM(paid_amount), 0) FROM pos_order o WHERE o.member_id = m.id AND o.status = 2 AND o.deleted = 0) AS total_amount, " +
            "  (SELECT MAX(pay_time) FROM pos_order o WHERE o.member_id = m.id AND o.status = 2 AND o.deleted = 0) AS last_pay_time, " +
            "  (SELECT MIN(pay_time) FROM pos_order o WHERE o.member_id = m.id AND o.status = 2 AND o.deleted = 0) AS first_pay_time " +
            "FROM crm_member m " +
            "WHERE m.id = ? AND m.deleted = 0 AND m.tenant_id = ?",
            memberId, tenantId);
        if (rows.isEmpty()) return Result.error(BizCode.NOT_FOUND, "会员不存在");
        // 字段重命名：join_date -> join_time（前端约定）
        Map<String, Object> row = rows.get(0);
        row.put("join_time", row.get("join_date"));
        // 查找等级名称
        Object levelId = row.get("level_id");
        if (levelId != null) {
            try {
                List<Map<String, Object>> lvl = jdbc.queryForList(
                    "SELECT name FROM mem_level WHERE id = ? AND deleted = 0 LIMIT 1",
                    ((Number) levelId).longValue());
                if (!lvl.isEmpty()) row.put("level_name", lvl.get(0).get("name"));
            } catch (Exception ignore) {
                // mem_level 表可能不存在，忽略
            }
        }
        return Result.success(row);
    }

    // ====================================================================
    // B-3：会员 LTV 百分位排名（同租户、按 predicted_ltv_365d）
    // ====================================================================

    @GetMapping("/rank/{memberId}")
    public Result<Map<String, Object>> rank(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 当前会员的 365d 预测
        List<Map<String, Object>> mine = jdbc.queryForList(
            "SELECT predicted_ltv_365d FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND member_id = ? " +
            "  AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?) " +
            "LIMIT 1", tenantId, memberId, tenantId);
        if (mine.isEmpty()) return Result.error(BizCode.NOT_FOUND, "该会员尚无快照");
        BigDecimal minePred = new BigDecimal(mine.get(0).get("predicted_ltv_365d").toString());

        Long totalCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?)",
            Long.class, tenantId, tenantId);
        Long belowCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_ltv_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = ?) " +
            "  AND predicted_ltv_365d < ?",
            Long.class, tenantId, tenantId, minePred);

        double percentile = totalCount == null || totalCount == 0 ? 0
                : Math.round((1.0 - belowCount.doubleValue() / totalCount.doubleValue()) * 1000) / 10.0;

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("totalMembers", totalCount);
        data.put("rankNo", belowCount + 1);          // 名次（从 1 起）
        data.put("percentile", percentile);           // 百分位（高于多少% 的会员）
        data.put("predictedLtv365d", minePred);
        return Result.success(data);
    }

    // ====================================================================
    // B-4：会员最近 N 笔订单
    // ====================================================================

    @GetMapping("/recent-orders/{memberId}")
    public Result<List<Map<String, Object>>> recentOrders(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "5") int limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (limit < 1 || limit > 200) limit = 5;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_no, paid_amount AS pay_amount, pay_time, status, store_id " +
            "FROM pos_order " +
            "WHERE tenant_id = ? AND member_id = ? AND deleted = 0 AND status = 2 " +
            "ORDER BY pay_time DESC LIMIT ?",
            tenantId, memberId, limit);
        return Result.success(rows);
    }
}