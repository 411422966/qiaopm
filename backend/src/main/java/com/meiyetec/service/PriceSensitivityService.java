package com.meiyetec.service;

import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * M15-03 价格敏感度分析（Price Sensitivity）
 *
 * <p>核心：会员对价格折扣的响应强度 = 不同折扣档位下的转化率/复购率差异
 *
 * <p>数据来源（按优先级）：
 * <ol>
 *   <li>{@code pricing_experiment_result} + {@code pricing_experiment}  ── A/B 实验真实数据</li>
 *   <li>{@code pos_order} + {@code pos_order_item}                     ── 历史订单按价格档位聚合</li>
 * </ol>
 *
 * <p>分群策略：
 * <pre>
 *   高敏感：折扣档转化率提升 ≥ 50%        ── 折扣敏感客群（优惠券杀手）
 *   中敏感：转化率提升 10%~50%             ── 弹性客群（看折扣力度消费）
 *   低敏感：转化率提升 < 10% 或负提升      ── 价格钝感客群（核心 VIP）
 *   未知：样本不足（< 30 条）
 * </pre>
 *
 * <p>所有统计均为「同租户」隔离；tenantId 由 TenantContextHolder 提供。
 * 返回结构均为 {@link Result}，无副作用、可重复调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSensitivityService {

    private final JdbcTemplate jdbc;

    /** 高敏感门槛：折扣档转化率较原价档提升 ≥ 50% */
    private static final BigDecimal HIGH_SENSITIVITY_LIFT = new BigDecimal("0.50");
    /** 中敏感门槛：提升 ≥ 10% */
    private static final BigDecimal MEDIUM_SENSITIVITY_LIFT = new BigDecimal("0.10");
    /** 最低样本门槛 */
    private static final int MIN_SAMPLE_THRESHOLD = 30;

    // ============================================================
    // 1. 总览
    // ============================================================
    public Result<Map<String, Object>> overview() {
        Long tenantId = tenantId();

        Map<String, Object> data = new LinkedHashMap<>();

        // ① 总体弹性：所有 A/B 实验的平均提升幅度
        String sqlOverallLift = """
            SELECT
              AVG(CASE WHEN lift_percent IS NULL THEN NULL ELSE lift_percent END) AS avgLift,
              COUNT(*) AS expTotal,
              SUM(CASE WHEN conclusion='WIN_VARIANT' THEN 1 ELSE 0 END) AS variantWin,
              SUM(CASE WHEN conclusion='WIN_CONTROL' THEN 1 ELSE 0 END) AS controlWin,
              SUM(CASE WHEN conclusion='NO_SIGNIFICANCE' THEN 1 ELSE 0 END) AS noSignificance,
              SUM(CASE WHEN conclusion='PENDING' THEN 1 ELSE 0 END) AS pending
            FROM pricing_experiment
            WHERE tenant_id = ? AND deleted = 0
            """;
        Map<String, Object> overallRow = jdbc.queryForMap(sqlOverallLift, tenantId);

        BigDecimal avgLift = toBigDecimal(overallRow.get("avgLift"));
        long expTotal = toLong(overallRow.get("expTotal"));
        long variantWin = toLong(overallRow.get("variantWin"));
        long controlWin = toLong(overallRow.get("controlWin"));
        long noSig = toLong(overallRow.get("noSignificance"));
        long pending = toLong(overallRow.get("pending"));

        // 弹性系数：基于平均提升幅度（百分比 / 100）。例 +25% → 1.25。
        BigDecimal elasticity = avgLift == null ? BigDecimal.ZERO
                : avgLift.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        data.put("avgLiftPercent", avgLift == null ? "0.00%" : avgLift.toString() + "%");
        data.put("elasticityCoefficient", elasticity);
        data.put("elasticityLabel", labelForElasticity(elasticity));
        data.put("experimentTotal", expTotal);
        data.put("variantWin", variantWin);
        data.put("controlWin", controlWin);
        data.put("noSignificance", noSig);
        data.put("pending", pending);

        // ② 会员敏感度分群（从 segments 接口聚合，避免重复 SQL）
        Result<Map<String, Object>> segResult = segments();
        Map<String, Object> segData = segResult.getData() == null ? Map.of() : segResult.getData();
        data.put("segments", segData);

        // ③ 关键洞察（基于弹性 + 分群结果生成 3~5 条建议）
        List<String> insights = new ArrayList<>();
        if (expTotal == 0) {
            insights.add("暂无 A/B 实验数据，建议先在「M14 定价实验」配置至少一个测试。");
        } else if (avgLift != null && avgLift.compareTo(HIGH_SENSITIVITY_LIFT.multiply(BigDecimal.valueOf(100))) >= 0) {
            insights.add(String.format(
                    "整体价格弹性高达 %s%%，客户对折扣高度敏感 → 折扣是有效拉新/激活工具。",
                    avgLift.setScale(1, RoundingMode.HALF_UP).toString()));
        } else if (avgLift != null && avgLift.compareTo(BigDecimal.valueOf(10)) < 0) {
            insights.add(String.format(
                    "整体提价弹性仅 %s%%，折扣对转化率拉动有限 → 建议聚焦服务/体验升级而非打折。",
                    avgLift.setScale(1, RoundingMode.HALF_UP).toString()));
        }
        if (variantWin > 0) {
            insights.add(String.format("已有 %d 个实验「变异组胜出」→ 可考虑将胜出折扣常态化为套餐/会员权益。", variantWin));
        }
        if (noSig > expTotal / 2 && expTotal > 0) {
            insights.add("超过半数实验结果不显著 → 建议延长实验周期或加大折扣梯度差异。");
        }
        if (segData != null && segData.get("highCount") != null) {
            long high = ((Number) segData.get("highCount")).longValue();
            if (high > 0) {
                insights.add(String.format("识别出 %d 位「折扣敏感客群」→ 优先推送大额折扣券激活复购。", high));
            }
        }
        if (insights.isEmpty()) insights.add("数据正在累积，建议持续观察。");
        data.put("insights", insights);

        return Result.success(data);
    }

    // ============================================================
    // 2. 单项目价格-转化曲线
    // ============================================================
    public Result<Map<String, Object>> curve(Long projectId) {
        if (projectId == null) throw new BusinessException(BizCode.BAD_REQUEST, "projectId 必填");
        Long tenantId = tenantId();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);

        // 优先用定价实验数据（A/B 实验本身有 variant 标签）
        String sqlExp = """
            SELECT variant, is_control,
                   AVG(discount_amount) AS avgDiscount,
                   AVG(final_price)     AS avgFinalPrice,
                   AVG(original_price)  AS avgOriginalPrice,
                   COUNT(*) AS sampleSize,
                   SUM(CASE WHEN conversion_status='CONVERTED' THEN 1 ELSE 0 END) AS conversions
            FROM pricing_experiment_result
            WHERE tenant_id = ? AND project_id = ? AND deleted = 0
            GROUP BY variant, is_control
            ORDER BY variant
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sqlExp, tenantId, projectId);

        // 如果该项目没实验数据，回退到 POS 历史订单按 final_price 分档聚合
        if (rows.isEmpty()) {
            rows = fallbackCurveFromPos(tenantId, projectId);
            data.put("source", "POS_HISTORY");
        } else {
            data.put("source", "PRICING_EXPERIMENT");
        }

        // 拼装图表数据点：{ discountPercent, sampleSize, conversionRate }
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long samples = toLong(r.get("sampleSize"));
            long convs = toLong(r.get("conversions"));
            BigDecimal rate = samples == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(convs).divide(BigDecimal.valueOf(samples), 4, RoundingMode.HALF_UP);
            BigDecimal discount = toBigDecimal(r.get("avgDiscount"));
            BigDecimal orig = toBigDecimal(r.get("avgOriginalPrice"));
            BigDecimal discountPct = BigDecimal.ZERO;
            if (orig != null && orig.compareTo(BigDecimal.ZERO) > 0 && discount != null) {
                discountPct = discount.divide(orig, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("variant", r.get("variant"));
            p.put("isControl", r.get("isControl"));
            p.put("discountPercent", discountPct);
            p.put("discountAmount", discount);
            p.put("originalPrice", orig);
            p.put("finalPrice", toBigDecimal(r.get("avgFinalPrice")));
            p.put("sampleSize", samples);
            p.put("conversions", convs);
            p.put("conversionRate", rate);
            p.put("conversionRatePct", rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
            points.add(p);
        }
        // 按折扣% 升序
        points.sort(Comparator.comparing(p -> toBigDecimal(p.get("discountPercent")), Comparator.nullsLast(BigDecimal::compareTo)));
        data.put("points", points);

        // 简单价格弹性 = (rate_highDiscount - rate_control) / rate_control / discountPct_high
        BigDecimal elasticity = computeElasticityFromCurve(points);
        data.put("priceElasticity", elasticity);
        return Result.success(data);
    }

    /** POS 订单回退聚合：按 final_price / original_price 算平均折扣%，按状态分桶算转化率 */
    private List<Map<String, Object>> fallbackCurveFromPos(Long tenantId, Long projectId) {
        String sql = """
            SELECT
              CASE WHEN i.final_price IS NULL OR i.final_price = 0 THEN 'POS_OTHER' END AS variant,
              1 AS is_control,
              AVG(GREATEST(i.unit_price - IFNULL(i.final_price, i.unit_price), 0)) AS avgDiscount,
              AVG(IFNULL(i.final_price, i.unit_price)) AS avgFinalPrice,
              AVG(i.unit_price) AS avgOriginalPrice,
              COUNT(*) AS sampleSize,
              SUM(CASE WHEN o.status = 2 THEN 1 ELSE 0 END) AS conversions
            FROM pos_order_item i
            JOIN pos_order o ON o.id = i.order_id
            WHERE o.tenant_id = ? AND i.service_id = ? AND o.deleted = 0 AND i.deleted = 0
              AND o.status IN (2, 3)
            GROUP BY variant
            """;
        try {
            return jdbc.queryForList(sql, tenantId, projectId);
        } catch (Exception e) {
            log.warn("[M15-03] fallbackCurveFromPos failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private BigDecimal computeElasticityFromCurve(List<Map<String, Object>> points) {
        // 取最低折扣点和最高折扣点比较
        if (points.size() < 2) return BigDecimal.ZERO;
        Map<String, Object> low = points.get(0);
        Map<String, Object> high = points.get(points.size() - 1);
        BigDecimal rateLow = toBigDecimal(low.get("conversionRate"));
        BigDecimal rateHigh = toBigDecimal(high.get("conversionRate"));
        BigDecimal discLow = toBigDecimal(low.get("discountPercent"));
        BigDecimal discHigh = toBigDecimal(high.get("discountPercent"));
        if (discHigh == null || discHigh.compareTo(BigDecimal.ZERO) <= 0 || rateLow.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal dRate = rateHigh.subtract(rateLow).divide(rateLow, 4, RoundingMode.HALF_UP);
        BigDecimal dDisc = discHigh.subtract(discLow).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        if (dDisc.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return dRate.divide(dDisc, 4, RoundingMode.HALF_UP);
    }

    // ============================================================
    // 3. 会员敏感度分群
    // ============================================================
    public Result<Map<String, Object>> segments() {
        Long tenantId = tenantId();

        // 每个会员：基于历史实验记录计算「高折扣档 vs 低折扣档」转化率差
        String sql = """
            SELECT member_id,
                   COUNT(*) AS samples,
                   SUM(CASE WHEN conversion_status='CONVERTED' THEN 1 ELSE 0 END) AS conversions,
                   AVG(discount_amount) AS avgDiscount,
                   MAX(CASE WHEN is_control=1 THEN 1 ELSE 0 END) AS hasControl,
                   MAX(CASE WHEN is_control=0 THEN 1 ELSE 0 END) AS hasVariant
            FROM pricing_experiment_result
            WHERE tenant_id = ? AND deleted = 0 AND member_id IS NOT NULL
            GROUP BY member_id
            HAVING samples >= ?
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, MIN_SAMPLE_THRESHOLD);

        long highCount = 0, mediumCount = 0, lowCount = 0;
        BigDecimal totalElasticity = BigDecimal.ZERO;
        long totalSampledMembers = 0;
        List<Map<String, Object>> sample = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            long samples = toLong(r.get("samples"));
            long convs = toLong(r.get("conversions"));
            long hasCtrl = toLong(r.get("hasControl"));
            long hasVar = toLong(r.get("hasVariant"));
            if (hasCtrl == 0 || hasVar == 0) continue;  // 必须同时有控制组+变异组记录

            // 用会员历史「折扣率」 vs 「转化率」做粗略估计（实际生产可走 per-member lift）
            BigDecimal avgDisc = toBigDecimal(r.get("avgDiscount"));
            BigDecimal rate = samples == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(convs).divide(BigDecimal.valueOf(samples), 4, RoundingMode.HALF_UP);
            // 简化启发式：avgDisc 大 → 倾向高敏感（折扣越大他才转化 → 价格敏感）
            String segment;
            if (avgDisc != null && avgDisc.compareTo(new BigDecimal("200")) >= 0) {
                segment = "HIGH"; highCount++;
            } else if (avgDisc != null && avgDisc.compareTo(new BigDecimal("50")) >= 0) {
                segment = "MEDIUM"; mediumCount++;
            } else {
                segment = "LOW"; lowCount++;
            }
            totalElasticity = totalElasticity.add(rate);
            totalSampledMembers++;

            if (sample.size() < 50) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("memberId", r.get("member_id"));
                m.put("samples", samples);
                m.put("conversions", convs);
                m.put("conversionRate", rate);
                m.put("avgDiscountAmount", avgDisc);
                m.put("segment", segment);
                sample.add(m);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("highCount", highCount);
        data.put("mediumCount", mediumCount);
        data.put("lowCount", lowCount);
        data.put("totalSampledMembers", totalSampledMembers);
        data.put("highSensitivityRate", totalSampledMembers == 0 ? "0.00%"
                : BigDecimal.valueOf(highCount).divide(BigDecimal.valueOf(totalSampledMembers), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        data.put("sampleList", sample);

        BigDecimal avgElasticity = totalSampledMembers == 0 ? BigDecimal.ZERO
                : totalElasticity.divide(BigDecimal.valueOf(totalSampledMembers), 4, RoundingMode.HALF_UP);
        data.put("avgMemberConversionRate", avgElasticity);
        return Result.success(data);
    }

    // ============================================================
    // 4. 项目 × 价格档位转化率矩阵
    // ============================================================
    public Result<Map<String, Object>> matrix(Integer topN) {
        Long tenantId = tenantId();
        int limit = topN == null || topN <= 0 ? 20 : Math.min(topN, 100);

        String sql = """
            SELECT i.service_id AS projectId,
                   MAX(i.service_name) AS projectName,
                   COUNT(*) AS sampleSize,
                   SUM(CASE WHEN o.status=2 THEN 1 ELSE 0 END) AS conversions,
                   AVG(i.unit_price) AS avgPrice,
                   AVG(IFNULL(i.discount_amount, 0)) AS avgDiscount
            FROM pos_order_item i
            JOIN pos_order o ON o.id = i.order_id
            WHERE o.tenant_id = ? AND o.deleted = 0 AND i.deleted = 0
              AND o.status IN (2, 3)
            GROUP BY i.service_id
            ORDER BY sampleSize DESC
            LIMIT ?
            """;
        List<Map<String, Object>> top = jdbc.queryForList(sql, tenantId, limit);
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (Map<String, Object> r : top) {
            long samples = toLong(r.get("sampleSize"));
            long convs = toLong(r.get("conversions"));
            BigDecimal rate = samples == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(convs).divide(BigDecimal.valueOf(samples), 4, RoundingMode.HALF_UP);

            // 价格档位：低(<200)/中(200~500)/高(>=500)
            BigDecimal avgPrice = toBigDecimal(r.get("avgPrice"));
            String tier;
            if (avgPrice == null) tier = "UNKNOWN";
            else if (avgPrice.compareTo(new BigDecimal("200")) < 0) tier = "LOW";
            else if (avgPrice.compareTo(new BigDecimal("500")) < 0) tier = "MID";
            else tier = "HIGH";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", r.get("projectId"));
            row.put("projectName", r.get("projectName"));
            row.put("sampleSize", samples);
            row.put("conversions", convs);
            row.put("conversionRate", rate);
            row.put("conversionRatePct", rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
            row.put("avgPrice", avgPrice);
            row.put("priceTier", tier);
            row.put("avgDiscountAmount", toBigDecimal(r.get("avgDiscount")));
            matrix.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", matrix);
        data.put("priceTiers", List.of("LOW(<200)", "MID(200-500)", "HIGH(>=500)"));
        return Result.success(data);
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private Long tenantId() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); }
        catch (Exception e) { return null; }
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); }
        catch (Exception e) { return 0L; }
    }

    private static String labelForElasticity(BigDecimal e) {
        if (e == null) return "未知";
        BigDecimal abs = e.abs();
        if (abs.compareTo(new BigDecimal("1.5")) >= 0) return "高度敏感（强弹性）";
        if (abs.compareTo(new BigDecimal("0.8")) >= 0) return "中度敏感";
        if (abs.compareTo(new BigDecimal("0.3")) >= 0) return "低度敏感";
        return "价格钝感（弱弹性）";
    }
}
