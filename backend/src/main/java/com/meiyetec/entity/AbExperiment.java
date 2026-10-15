package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

/**
 * A/B 实验定义
 *
 * <p>AI-007：在实体上挂载 {@link #computeSignificance(Map)} 方法（卡方检验），
 * 可以直接基于 {@link AbAssignment} 数据的聚合计算显著性。
 *
 * <p>历史字段保持向后兼容：status 取值 DRAFT/RUNNING/PAUSED/ENDED
 */
@Data
@TableName("ab_experiment")
public class AbExperiment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String expKey;
    private String expName;
    private String description;
    /** DRAFT / RUNNING / PAUSED / ENDED */
    private String status;
    /** JSON [{key, name, weight}] */
    private String variants;
    /** USER_ID / STORE_ID / REGION / HASH */
    private String targetRule;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** CONVERSION / GMV / RETENTION */
    private String primaryMetric;
    private String owner;

    // ============= AI-007：M14 联动 + 实时显著性 =============
    /** 关联的 M14 定价实验 ID（可空；为空表示纯灰度实验，不写入 pricing_experiment_result） */
    private Long pricingExpId;
    /** 卡方统计量 */
    private Double chiSquare;
    /** P-value（0..1） */
    private Double chiPValue;
    /** 自由度 */
    private Integer chiDf;
    /** 最近一次重算时间 */
    private LocalDateTime chiUpdatedAt;
    /** INSUFFICIENT_DATA / WIN_VARIANT_X / NO_SIGNIFICANCE / EVEN_DISTRIBUTION */
    private String conclusion;
    /** 表现最佳的 variant key（实时统计） */
    private String bestVariant;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * AI-007 主方法：卡方检验（Chi-Square Test of Independence）
     *
     * <p>对每个 variant 计算「转化」与「未转化」的分布，构建 K × 2 列联表：
     * <pre>
     *              not_converted   converted    |  total
     * variant_A        a              b          |  n_A
     * variant_B        c              d          |  n_B
     * ...
     *   total       totalNot        totalYes      totalN
     * </pre>
     *
     * <p>计算 χ² = Σ ((O - E)² / E)，自由度 df = K - 1，
     * 并用 Wilson-Hilferty 变换近似 P-value。
     *
     * @param observed 每个变体的统计（key = variantKey, value = sampleSize + conversions）
     * @return 显著性结果
     */
    public ChiSquareResult computeSignificance(Map<String, VariantStats> observed) {
        if (observed == null || observed.isEmpty()) {
            return ChiSquareResult.insufficient(Collections.emptyMap());
        }

        // 1. 汇总
        long totalAssigned = 0;
        long totalConverted = 0;
        for (VariantStats vs : observed.values()) {
            totalAssigned += Math.max(0, vs.getSampleSize());
            totalConverted += Math.max(0, vs.getConversions());
        }
        long totalNotConverted = totalAssigned - totalConverted;

        // 2. 退化情形
        Map<String, Double> rates = conversionRates(observed);
        if (observed.size() < 2) {
            return new ChiSquareResult(0.0, 0, 1.0, "EVEN_DISTRIBUTION",
                    rates.isEmpty() ? null : rates.keySet().iterator().next(),
                    rates, false);
        }
        if (totalAssigned == 0 || totalConverted == 0
                || totalConverted == totalAssigned
                || totalAssigned < MIN_SAMPLES) {
            return new ChiSquareResult(0.0, observed.size() - 1, 1.0,
                    "INSUFFICIENT_DATA", bestByRate(observed),
                    rates, false);
        }

        // 3. 卡方统计量
        double chiSquare = 0.0;
        for (Map.Entry<String, VariantStats> e : observed.entrySet()) {
            VariantStats vs = e.getValue();
            long n = Math.max(0, vs.getSampleSize());
            long x = Math.max(0, vs.getConversions());
            long y = n - x;
            if (n == 0) continue;

            double eYes = (double) n * totalConverted / totalAssigned;
            double eNo = (double) n * totalNotConverted / totalAssigned;

            if (eYes > 0) chiSquare += Math.pow(x - eYes, 2) / eYes;
            if (eNo > 0) chiSquare += Math.pow(y - eNo, 2) / eNo;
        }

        int df = observed.size() - 1;
        // Wilson-Hilferty 近似 χ² → P-value（正态 CDF 的尾部面积）
        double pValue = chiSquareSurvival(chiSquare, df);

        // 4. 结论（p < 0.05 → 显著，最佳 variant 即为赢家）
        String best = bestByRate(observed);
        String conclusion;
        boolean significant;
        if (pValue < 0.05 && best != null) {
            conclusion = "WIN_" + best.toUpperCase(Locale.ROOT);
            significant = true;
        } else {
            conclusion = "NO_SIGNIFICANCE";
            significant = false;
        }

        return new ChiSquareResult(chiSquare, df, pValue, conclusion, best, rates, significant);
    }

    /**
     * 便捷：从 {@code AbAssignment} 列表构造观察数据
     */
    public static Map<String, VariantStats> aggregateAssignments(List<AbAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return Collections.emptyMap();
        Map<String, VariantStats> map = new LinkedHashMap<>();
        for (AbAssignment a : assignments) {
            VariantStats vs = map.computeIfAbsent(a.getVariantKey(), k -> new VariantStats());
            vs.incSampleSize();
            if ("CONVERTED".equalsIgnoreCase(a.getConversionStatus())) {
                vs.incConversions();
            }
        }
        return map;
    }

    private static Map<String, Double> conversionRates(Map<String, VariantStats> observed) {
        Map<String, Double> r = new LinkedHashMap<>();
        for (Map.Entry<String, VariantStats> e : observed.entrySet()) {
            r.put(e.getKey(), e.getValue().getConversionRate());
        }
        return r;
    }

    private static String bestByRate(Map<String, VariantStats> observed) {
        return observed.entrySet().stream()
                .max(Comparator.comparingDouble(en -> en.getValue().getConversionRate()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * χ² 生存函数 P(X &gt; chi)
     * <p>Wilson-Hilferty 变换：X ~ χ²(df) ⟹ ((X/df)^(1/3)) ~ N(μ, σ²)
     * <ul>
     *   <li>μ = 1 - 2 / (9·df)</li>
     *   <li>σ² = 2 / (9·df)</li>
     * </ul>
     * 再用 Cody erfc 算 Φ(-z)，近似 χ² 的上尾概率。
     */
    public static double chiSquareSurvival(double chi, int df) {
        if (df < 1 || chi <= 0) return 1.0;
        double x = Math.pow(chi / df, 1.0 / 3.0);
        double mu = 1 - 2.0 / (9.0 * df);
        double sd = Math.sqrt(2.0 / (9.0 * df));
        double z = (x - mu) / sd;
        return 0.5 * erfc(z / Math.sqrt(2.0));
    }

    /**
     * Cody 数值 erfc（最大误差 |ε| ≤ 1.5e-7）
     */
    public static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0 / (1.0 + 0.5 * z);
        double ans = t * Math.exp(-z * z - 1.26551223 +
                t * (1.00002368 +
                        t * (0.37409196 +
                                t * (0.09678418 +
                                        t * (-0.18628806 +
                                                t * (0.27886807 +
                                                        t * (-1.13520398 +
                                                                t * (1.48851587 +
                                                                        t * (-0.82215223 +
                                                                                t * 0.17087277)))))))));
        return x >= 0 ? ans : 2.0 - ans;
    }

    /** 最少样本量阈值，少于此值直接返回 INSUFFICIENT_DATA */
    public static final int MIN_SAMPLES = 30;

    // ============================================================
    //  嵌套类：观察数据 + 检验结果
    // ============================================================

    /**
     * 单个 variant 的观察计数
     */
    @Data
    public static class VariantStats {
        private long sampleSize;
        private long conversions;

        public VariantStats() {}
        public VariantStats(long sampleSize, long conversions) {
            this.sampleSize = sampleSize;
            this.conversions = conversions;
        }

        public void incSampleSize() { this.sampleSize++; }
        public void incConversions() { this.conversions++; }
        public void incConversions(int by) { this.conversions += by; }

        /** 转化率（0..1） */
        public double getConversionRate() {
            return sampleSize == 0 ? 0.0 : (double) conversions / sampleSize;
        }
    }

    /**
     * 卡方检验结果
     *
     * <p>字段：
     * <ul>
     *   <li>{@code chiSquare}：χ² 统计量</li>
     *   <li>{@code df}：自由度</li>
     *   <li>{@code pValue}：上尾概率 P(X² ≥ χ²)</li>
     *   <li>{@code conclusion}：WIN_VARIANT_X / NO_SIGNIFICANCE / INSUFFICIENT_DATA / EVEN_DISTRIBUTION</li>
     *   <li>{@code bestVariant}：转化率最高的 variant（即使不显著也算 best）</li>
     *   <li>{@code conversionRate}：每个 variant 的转化率</li>
     *   <li>{@code significant}：p &lt; 0.05</li>
     * </ul>
     */
    @Data
    public static class ChiSquareResult {
        private double chiSquare;
        private int df;
        private double pValue;
        private String conclusion;
        private String bestVariant;
        private Map<String, Double> conversionRate;
        private boolean significant;

        public ChiSquareResult() {}

        public ChiSquareResult(double chiSquare, int df, double pValue,
                                String conclusion, String bestVariant,
                                Map<String, Double> conversionRate, boolean significant) {
            this.chiSquare = chiSquare;
            this.df = df;
            this.pValue = pValue;
            this.conclusion = conclusion;
            this.bestVariant = bestVariant;
            this.conversionRate = conversionRate;
            this.significant = significant;
        }

        public static ChiSquareResult insufficient(Map<String, Double> rates) {
            return new ChiSquareResult(0.0, 0, 1.0, "INSUFFICIENT_DATA", null, rates, false);
        }
    }
}
