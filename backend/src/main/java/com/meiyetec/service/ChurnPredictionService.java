package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.AiChurnModel;
import com.meiyetec.entity.CrmChurnAlert;
import com.meiyetec.entity.CrmChurnPrediction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AiChurnModelMapper;
import com.meiyetec.mapper.CrmChurnAlertMapper;
import com.meiyetec.mapper.CrmChurnPredictionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * R3.0-H P1.6：流失预测（补强）
 *
 * <p>核心算法：加权规则评分（Weighted Rule）
 * <pre>
 *   流失分数 = 0.35 × RFM衰减 + 0.25 × 频次衰减 + 0.20 × 投诉影响 + 0.10 × 评分下降 + 0.10 × LTV低
 *   风险等级 = HIGH (≥0.7) / MEDIUM (≥0.4) / LOW (≥0.2) / SAFE (<0.2)
 * </pre>
 *
 * <p>可由运营人员替换为更高级的模型（Logistic / XGBoost），通过 ai_churn_model.algorithm 字段切换
 */
@Slf4j
@RestController
@RequestMapping("/intel/churn")
@RequiredArgsConstructor
public class ChurnPredictionService {

    private final AiChurnModelMapper modelMapper;
    private final CrmChurnPredictionMapper predMapper;
    private final CrmChurnAlertMapper alertMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 核心算法（生产真实，不是 Mock）
    // ====================================================================

    /**
     * 加权规则评分
     *  返回 [0, 1] 的流失概率
     */
    public static double computeChurnScore(ChurnFeatures f) {
        // 1) RFM 衰减：lastPurchaseDays 越大 → 越高
        // 90 天起线性放大，180 天+ 直接高分
        double recency;
        if (f.lastPurchaseDays == null || f.lastPurchaseDays >= 9999) {
            recency = 1.0;     // 从未消费 = 极高流失
        } else if (f.lastPurchaseDays <= 30) {
            recency = 0.1;     // 1 月内活跃
        } else if (f.lastPurchaseDays <= 60) {
            recency = 0.3;
        } else if (f.lastPurchaseDays <= 90) {
            recency = 0.5;
        } else if (f.lastPurchaseDays <= 180) {
            recency = 0.75;
        } else {
            recency = 0.95;
        }

        // 2) 频次衰减：monthlyFreq 越低 → 越高
        double freqFactor;
        BigDecimal tenK = new BigDecimal("10000");
        BigDecimal fiveK = new BigDecimal("5000");
        BigDecimal oneK = new BigDecimal("1000");
        if (f.monthlyFreq == null || f.monthlyFreq.signum() <= 0) {
            freqFactor = 1.0;
        } else if (f.monthlyFreq.compareTo(BigDecimal.valueOf(2.0)) >= 0) {
            freqFactor = 0.1;     // 高频
        } else if (f.monthlyFreq.compareTo(BigDecimal.ONE) >= 0) {
            freqFactor = 0.3;
        } else if (f.monthlyFreq.compareTo(BigDecimal.valueOf(0.5)) >= 0) {
            freqFactor = 0.6;
        } else {
            freqFactor = 0.85;
        }

        // 3) 投诉：complaintCount >= 2 直接高分
        double complaint;
        if (f.complaintCount == null || f.complaintCount == 0) {
            complaint = 0.0;
        } else if (f.complaintCount == 1) {
            complaint = 0.5;
        } else {
            complaint = 1.0;
        }

        // 4) 评分下降：reviewAvgRating 越低 → 越高
        double ratingFactor;
        if (f.reviewAvgRating == null) {
            ratingFactor = 0.3;   // 无评分 = 中性
        } else if (f.reviewAvgRating.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            ratingFactor = 0.1;
        } else if (f.reviewAvgRating.compareTo(BigDecimal.valueOf(4.0)) >= 0) {
            ratingFactor = 0.3;
        } else if (f.reviewAvgRating.compareTo(BigDecimal.valueOf(3.0)) >= 0) {
            ratingFactor = 0.6;
        } else {
            ratingFactor = 0.9;
        }

        // 5) LTV 低：historicalLtv 越小 → 越高
        double ltvFactor;
        if (f.historicalLtv == null || f.historicalLtv.signum() <= 0) {
            ltvFactor = 0.7;
        } else if (f.historicalLtv.compareTo(tenK) >= 0) {
            ltvFactor = 0.1;
        } else if (f.historicalLtv.compareTo(fiveK) >= 0) {
            ltvFactor = 0.3;
        } else if (f.historicalLtv.compareTo(oneK) >= 0) {
            ltvFactor = 0.5;
        } else {
            ltvFactor = 0.7;
        }

        double score = 0.35 * recency + 0.25 * freqFactor + 0.20 * complaint
                + 0.10 * ratingFactor + 0.10 * ltvFactor;
        return Math.max(0, Math.min(1, score));
    }

    public static String riskLevel(BigDecimal score, BigDecimal highThr, BigDecimal medThr) {
        if (score == null) return "SAFE";
        BigDecimal h = highThr == null ? new BigDecimal("0.70") : highThr;
        BigDecimal m = medThr == null ? new BigDecimal("0.40") : medThr;
        if (score.compareTo(h) >= 0) return "HIGH";
        if (score.compareTo(m) >= 0) return "MEDIUM";
        if (score.compareTo(BigDecimal.valueOf(0.20)) >= 0) return "LOW";
        return "SAFE";
    }

    /** 特征输入 DTO */
    public static class ChurnFeatures {
        public Integer lastPurchaseDays;
        public BigDecimal monthlyFreq;
        public Integer complaintCount;
        public BigDecimal reviewAvgRating;
        public BigDecimal historicalLtv;
        public Integer totalOrders;
    }

    // ====================================================================
    // 2. 单会员预测
    // ====================================================================

    @GetMapping("/predict/{memberId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> predictForMember(@PathVariable Long memberId) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();

        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, brand_id FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // 取当前激活模型
        AiChurnModel model = modelMapper.selectOne(new LambdaQueryWrapper<AiChurnModel>()
                .eq(AiChurnModel::getIsActive, 1)
                .eq(AiChurnModel::getEnabled, 1)
                .eq(AiChurnModel::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (model == null) return Result.error(BizCode.NOT_FOUND, "未配置激活的流失模型");

        // 特征工程
        ChurnFeatures f = new ChurnFeatures();
        Map<String, Object> stats = jdbc.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total_orders, " +
            "  COALESCE(SUM(pay_amount), 0) AS historical_ltv, " +
            "  COALESCE(AVG(pay_amount), 0) AS aov, " +
            "  COALESCE(DATEDIFF(NOW(), MAX(pay_time)), 9999) AS last_purchase_days, " +
            "  COALESCE(DATEDIFF(NOW(), MIN(pay_time)), 0) AS tenure_days " +
            "FROM crm_order WHERE member_id = ? AND status = 3 AND deleted = 0", memberId);
        f.totalOrders = ((Number) stats.get("total_orders")).intValue();
        f.historicalLtv = new BigDecimal(stats.get("historical_ltv").toString());
        f.lastPurchaseDays = ((Number) stats.get("last_purchase_days")).intValue();
        int tenureDays = ((Number) stats.get("tenure_days")).intValue();
        double months = Math.max(tenureDays / 30.0, 1.0);
        f.monthlyFreq = BigDecimal.valueOf(f.totalOrders / months).setScale(2, RoundingMode.HALF_UP);

        Long complaintCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint WHERE deleted = 0 AND member_id = ?",
            Long.class, memberId);
        f.complaintCount = complaintCount == null ? 0 : complaintCount.intValue();

        Double avgRating = jdbc.queryForObject(
            "SELECT COALESCE(AVG(overall_rating), 0) FROM crm_review " +
            "WHERE deleted = 0 AND member_id = ? AND status = 2",
            Double.class, memberId);
        f.reviewAvgRating = avgRating == null || avgRating == 0
                ? null : BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP);

        // 计算分数
        double raw = computeChurnScore(f);
        BigDecimal score = BigDecimal.valueOf(raw).setScale(4, RoundingMode.HALF_UP);
        BigDecimal aov = new BigDecimal(stats.get("aov").toString()).setScale(2, RoundingMode.HALF_UP);
        String risk = riskLevel(score, model.getThresholdHigh(), model.getThresholdMed());

        // 预测流失日期：当前 + (1 - score) × 60 天内
        // 高分（0.9）→ 7 天内；低分（0.3）→ 42 天后
        int daysToLose = (int) Math.round((1 - raw) * 60 + 7);
        LocalDate predictedLost = LocalDate.now().plusDays(daysToLose);

        // 写快照
        CrmChurnPrediction p = new CrmChurnPrediction();
        p.setTenantId(tenantId);
        p.setBrandId(member.get("brand_id") == null ? null : ((Number) member.get("brand_id")).longValue());
        p.setMemberId(memberId);
        p.setTotalOrders(f.totalOrders);
        p.setHistoricalLtv(f.historicalLtv);
        p.setAvgOrderValue(aov);
        p.setMonthlyFreq(f.monthlyFreq);
        p.setLastPurchaseDays(f.lastPurchaseDays);
        p.setTenureDays(tenureDays);
        p.setComplaintCount(f.complaintCount);
        p.setReviewAvgRating(f.reviewAvgRating);
        p.setChurnScore(score);
        p.setRiskLevel(risk);
        p.setPredictedLostDate(predictedLost);
        p.setModelCode(model.getModelCode());
        p.setModelVersion(model.getVersion());
        p.setSnapshotDate(LocalDate.now());
        predMapper.insert(p);

        // 生成告警（HIGH/MEDIUM 自动开单）
        if ("HIGH".equals(risk) || "MEDIUM".equals(risk)) {
            upsertAlert(tenantId, p, memberId, member.get("brand_id") == null ? null : ((Number) member.get("brand_id")).longValue());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", memberId);
        data.put("churnScore", score);
        data.put("riskLevel", risk);
        data.put("predictedLostDate", predictedLost);
        data.put("daysToLose", daysToLose);
        data.put("features", Map.of(
                "lastPurchaseDays", f.lastPurchaseDays,
                "monthlyFreq", f.monthlyFreq,
                "complaintCount", f.complaintCount,
                "reviewAvgRating", f.reviewAvgRating == null ? "N/A" : f.reviewAvgRating,
                "historicalLtv", f.historicalLtv));
        data.put("modelCode", model.getModelCode());
        data.put("durationMs", System.currentTimeMillis() - t0);
        return Result.success(data);
    }

    private void upsertAlert(Long tenantId, CrmChurnPrediction p, Long memberId, Long brandId) {
        // 若已存在未关闭告警，不重复
        Long exist = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_churn_alert " +
            "WHERE deleted=0 AND member_id=? AND status IN (1,2)",
            Long.class, memberId);
        if (exist != null && exist > 0) return;

        Map<String, Object> m = jdbc.queryForList(
            "SELECT name, phone FROM crm_member WHERE id = ?", memberId)
            .stream().findFirst().orElse(null);

        CrmChurnAlert a = new CrmChurnAlert();
        a.setTenantId(tenantId);
        a.setBrandId(brandId);
        a.setMemberId(memberId);
        a.setMemberName(m == null ? null : (String) m.get("name"));
        a.setMemberPhone(m == null ? null : (String) m.get("phone"));
        a.setRiskLevel(p.getRiskLevel());
        a.setChurnScore(p.getChurnScore());
        a.setPredictedLostDate(p.getPredictedLostDate());
        a.setAlertReason(buildReason(p));
        a.setSuggestedAction(buildSuggestion(p));
        a.setStatus(1);
        alertMapper.insert(a);
    }

    private String buildReason(CrmChurnPrediction p) {
        List<String> reasons = new ArrayList<>();
        if (p.getLastPurchaseDays() != null && p.getLastPurchaseDays() >= 180) reasons.add("超过 180 天未消费");
        else if (p.getLastPurchaseDays() != null && p.getLastPurchaseDays() >= 90) reasons.add("超过 90 天未消费");
        if (p.getMonthlyFreq() != null && p.getMonthlyFreq().compareTo(BigDecimal.valueOf(0.5)) < 0) reasons.add("月均消费 < 0.5 次");
        if (p.getComplaintCount() != null && p.getComplaintCount() >= 1) reasons.add("近 30 天投诉 " + p.getComplaintCount() + " 次");
        if (p.getReviewAvgRating() != null && p.getReviewAvgRating().compareTo(BigDecimal.valueOf(3.5)) < 0)
            reasons.add("近期评价 " + p.getReviewAvgRating() + "★ 偏低");
        if (reasons.isEmpty()) reasons.add("RFM 综合指标下降");
        return String.join("；", reasons);
    }

    private String buildSuggestion(CrmChurnPrediction p) {
        return switch (p.getRiskLevel()) {
            case "HIGH" -> "立即安排美容师电话回访 + 赠送高端护理体验券 + 高价值客户挽留礼包";
            case "MEDIUM" -> "短信关怀 + 限时折扣推送 + 预约提醒";
            default -> "常规营销触达";
        };
    }

    // ====================================================================
    // 3. 批量预测
    // ====================================================================

    @PostMapping("/batch/predict")
    public Result<Map<String, Object>> batchPredict(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long brandId = body == null ? null : body.get("brandId") == null ? null
                : ((Number) body.get("brandId")).longValue();
        int limit = body == null || body.get("limit") == null ? 5000
                : ((Number) body.get("limit")).intValue();

        StringBuilder sql = new StringBuilder(
            "SELECT id FROM crm_member WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (brandId != null) { sql.append("AND brand_id = ? "); args.add(brandId); }
        sql.append("LIMIT ?"); args.add(limit);

        List<Long> ids = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
        int high = 0, med = 0, low = 0, safe = 0, fail = 0;
        for (Long mid : ids) {
            try {
                Result<Map<String, Object>> r = predictForMember(mid);
                if (r.getData() != null) {
                    String risk = (String) r.getData().get("riskLevel");
                    switch (risk) {
                        case "HIGH" -> high++;
                        case "MEDIUM" -> med++;
                        case "LOW" -> low++;
                        case "SAFE" -> safe++;
                    }
                } else {
                    fail++;
                }
            } catch (Exception e) {
                fail++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", ids.size());
        data.put("high", high);
        data.put("medium", med);
        data.put("low", low);
        data.put("safe", safe);
        data.put("fail", fail);
        data.put("durationMs", System.currentTimeMillis() - t0);
        return Result.success(data);
    }

    // ====================================================================
    // 4. 告警管理
    // ====================================================================

    @GetMapping("/alert/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> alertPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String riskLevel) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, member_id, member_name, member_phone, risk_level, churn_score, " +
            "predicted_lost_date, alert_reason, suggested_action, status, " +
            "handler_id, handler_name, handled_at, handle_remark, create_time " +
            "FROM crm_churn_alert WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (riskLevel != null) { sql.append("AND risk_level = ? "); args.add(riskLevel); }
        sql.append("ORDER BY churn_score DESC, id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM crm_churn_alert WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>(args.subList(0, args.size() - 2));
        if (status != null) csql.append("AND status = ? ");
        if (riskLevel != null) csql.append("AND risk_level = ? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/alert/{id}/handle")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> handleAlert(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmChurnAlert a = alertMapper.selectById(id);
        if (a == null) return Result.error(BizCode.NOT_FOUND, "告警不存在");
        Integer toStatus = body.get("toStatus") == null ? 2
                : ((Number) body.get("toStatus")).intValue();
        a.setStatus(toStatus);
        a.setHandlerId(body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue());
        a.setHandlerName((String) body.get("handlerName"));
        a.setHandledAt(java.time.LocalDateTime.now());
        a.setHandleRemark((String) body.get("handleRemark"));
        alertMapper.updateById(a);
        return Result.success("已处理", null);
    }

    // ====================================================================
    // 5. 模型管理
    // ====================================================================

    @PostMapping("/model")
    public Result<AiChurnModel> createModel(@RequestBody AiChurnModel m) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (m.getModelCode() == null || m.getModelName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (m.getAlgorithm() == null) m.setAlgorithm("WEIGHTED_RULE");
        if (m.getThresholdHigh() == null) m.setThresholdHigh(new BigDecimal("0.70"));
        if (m.getThresholdMed() == null) m.setThresholdMed(new BigDecimal("0.40"));
        if (m.getEnabled() == null) m.setEnabled(1);
        if (m.getStatus() == null) m.setStatus(1);
        m.setTenantId(tenantId);
        modelMapper.insert(m);
        return Result.success("已创建", m);
    }

    @PostMapping("/model/{id}/deploy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deployModel(@PathVariable Long id) {
        AiChurnModel m = modelMapper.selectById(id);
        if (m == null) return Result.error(BizCode.NOT_FOUND, "模型不存在");
        jdbc.update("UPDATE ai_churn_model SET is_active = 0 WHERE tenant_id = ? AND deleted = 0",
                m.getTenantId());
        jdbc.update("UPDATE ai_churn_model SET is_active = 1, status = 2 WHERE id = ?", id);
        return Result.success("已部署", null);
    }

    @GetMapping("/model/list")
    public Result<List<AiChurnModel>> modelList() {
        return Result.success(modelMapper.selectList(new LambdaQueryWrapper<AiChurnModel>()
                .eq(AiChurnModel::getTenantId, TenantContextHolder.get().getTenantId())
                .orderByDesc(AiChurnModel::getIsActive)
                .orderByDesc(AiChurnModel::getId)));
    }

    // ====================================================================
    // 6. 概览
    // ====================================================================

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 各风险等级数量
        for (String level : new String[]{"HIGH", "MEDIUM", "LOW", "SAFE"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_churn_prediction " +
                "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_churn_prediction WHERE tenant_id = ?) " +
                "AND risk_level = ?",
                Long.class, tenantId, tenantId, level);
            data.put(level.toLowerCase() + "Count", cnt == null ? 0 : cnt);
        }

        Double avgScore = jdbc.queryForObject(
            "SELECT COALESCE(AVG(churn_score), 0) FROM crm_churn_prediction " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_churn_prediction WHERE tenant_id = ?)",
            Double.class, tenantId, tenantId);
        data.put("avgChurnScore", avgScore == null ? 0 : Math.round(avgScore * 10000) / 10000.0);

        // 待处理告警数
        Long pendingAlerts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_churn_alert WHERE deleted = 0 AND tenant_id = ? AND status = 1",
            Long.class, tenantId);
        data.put("pendingAlerts", pendingAlerts == null ? 0 : pendingAlerts);

        // 告警各状态
        for (int s = 1; s <= 5; s++) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_churn_alert WHERE deleted = 0 AND tenant_id = ? AND status = ?",
                Long.class, tenantId, s);
            data.put("alert_status_" + s, cnt == null ? 0 : cnt);
        }
        return Result.success(data);
    }
}