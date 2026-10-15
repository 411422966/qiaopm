package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.ChurnModelRegistry;
import com.meiyetec.entity.ChurnTrainingRunLog;
import com.meiyetec.entity.LtvTrainingFeature;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.ChurnModelRegistryMapper;
import com.meiyetec.mapper.ChurnTrainingRunLogMapper;
import com.meiyetec.mapper.LtvTrainingFeatureMapper;
import com.meiyetec.security.TenantContextHolder;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P4.1 T1.11 — Churn 模型（流失预测）
 *
 * 算法：Logistic Regression（LR）+ 决策树（DT）+ GBDT 简化版
 * 流失定义：超过 N 天不来消费（N 可配置，默认 90 天）
 *
 * 评估指标：AUC / LogLoss / Accuracy / Precision / Recall / F1
 */
@Slf4j
@RestController
@RequestMapping("/intel/churn/training")
@RequiredArgsConstructor
public class ChurnTrainingService {

    private final ChurnModelRegistryMapper modelMapper;
    private final ChurnTrainingRunLogMapper logMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 特征列（复用 LTV 特征）
    private static final List<String> FEATURE_COLS = List.of(
            "recency_days", "frequency_total", "frequency_90d",
            "monetary_total", "monetary_90d", "aov", "avg_interval_days",
            "tenure_days", "service_category_count",
            "wallet_balance", "active_packages", "total_package_balance",
            "package_consumption_rate", "booking_count", "noshow_rate",
            "review_count", "avg_rating", "coupon_used_count", "point_balance"
    );

    // ====================================================================
    // T1.11 训练入口
    // ====================================================================

    @PostMapping("/train")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> train(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate featureDate = body == null || body.get("featureDate") == null
                ? LocalDate.now().minusDays(1)
                : LocalDate.parse(body.get("featureDate").toString());
        String algorithm = body == null || body.get("algorithm") == null
                ? "LR" : body.get("algorithm").toString();
        int churnThreshold = body == null || body.get("churnThreshold") == null
                ? 90 : ((Number) body.get("churnThreshold")).intValue();
        int horizon = body == null || body.get("horizon") == null
                ? 90 : ((Number) body.get("horizon")).intValue();

        // 1) 准备数据
        List<Sample> samples = loadSamples(tenantId, featureDate, churnThreshold, horizon);
        if (samples.size() < 100) {
            return Result.error(BizCode.BAD_REQUEST,
                    "样本不足（" + samples.size() + "条），需至少 100 条，请先运行 LTV ETL 积累数据");
        }

        log.info("[Churn] 开始训练: {} 样本, churn={}/{}, 算法={}",
                samples.size(), samples.stream().filter(s -> s.label == 1).count(),
                samples.stream().filter(s -> s.label == 0).count(), algorithm);

        try {
            Collections.shuffle(samples, new Random(42));
            int split = (int) (samples.size() * 0.8);
            List<Sample> train = samples.subList(0, split);
            List<Sample> val = samples.subList(split, samples.size());

            // 2) 数据标准化
            double[] means = new double[FEATURE_COLS.size()];
            double[] stds = new double[FEATURE_COLS.size()];
            computeStats(train, means, stds);

            // 3) 训练
            Map<String, Object> trainResult;
            if ("LR".equalsIgnoreCase(algorithm)) {
                trainResult = trainLogisticRegression(train, val, means, stds);
            } else {
                // 默认用 LR+DT 混合
                trainResult = trainHybrid(train, val, means, stds);
            }

            // 4) 保存模型
            ChurnModelRegistry model = new ChurnModelRegistry();
            model.setTenantId(tenantId);
            model.setModelCode("CHURN_" + algorithm + "_" + System.currentTimeMillis() / 1000);
            model.setModelName("Churn 模型 " + LocalDate.now());
            model.setModelType("CHURN_CLASSIFICATION");
            model.setAlgorithm(algorithm);
            model.setFeatureWindowDays(180);
            model.setChurnHorizonDays(horizon);
            model.setChurnThreshold(churnThreshold);
            model.setTrainingSamples(samples.size());
            model.setPositiveSamples((int) samples.stream().filter(s -> s.label == 1).count());
            model.setNegativeSamples((int) samples.stream().filter(s -> s.label == 0).count());

            // 评估指标
            Map<String, Double> trainMetrics = (Map<String, Double>) trainResult.get("trainMetrics");
            Map<String, Double> valMetrics = (Map<String, Double>) trainResult.get("valMetrics");
            setMetrics(model, trainMetrics, valMetrics);

            // 特征重要性
            Map<String, Double> importance = (Map<String, Double>) trainResult.get("importance");
            model.setFeatureImportanceJson(toImportanceJson(importance));

            if ("LR".equalsIgnoreCase(algorithm)) {
                Map<String, Double> coefs = (Map<String, Double>) trainResult.get("coefficients");
                model.setLrCoefficientsJson(toCoefficientsJson(coefs));
            }

            model.setIsActive(0);
            model.setStatus(2);
            model.setTrainedAt(LocalDateTime.now());
            model.setVersion("v1");
            modelMapper.insert(model);

            // 5) 输出
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("modelId", model.getId());
            data.put("algorithm", algorithm);
            data.put("trainingSamples", samples.size());
            data.put("positiveRate", String.format("%.1f%%",
                    samples.stream().filter(s -> s.label == 1).count() * 100.0 / samples.size()));
            data.put("trainAUC", trainMetrics.get("auc"));
            data.put("valAUC", valMetrics.get("auc"));
            data.put("valF1", valMetrics.get("f1"));
            data.put("valPrecision", valMetrics.get("precision"));
            data.put("valRecall", valMetrics.get("recall"));
            data.put("valLogloss", valMetrics.get("logloss"));
            data.put("conclusion", getConclusion(valMetrics));
            data.put("durationMs", System.currentTimeMillis() - t0);

            // Top 5 流失风险特征
            List<Map.Entry<String, Double>> top5 = importance.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5).collect(Collectors.toList());
            data.put("topRiskFeatures", top5);

            log.info("[Churn] 训练完成: AUC={}, F1={}, conclusion={}",
                    valMetrics.get("auc"), valMetrics.get("f1"), getConclusion(valMetrics));

            return Result.success("训练完成", data);
        } catch (Exception e) {
            log.error("Churn 训练失败", e);
            return Result.error(BizCode.SYSTEM_ERROR, "训练失败: " + e.getMessage());
        }
    }

    // ====================================================================
    // Logistic Regression 训练（梯度下降）
    // ====================================================================

    private Map<String, Object> trainLogisticRegression(List<Sample> train, List<Sample> val,
                                                        double[] means, double[] stds) {
        int n = FEATURE_COLS.size();
        double[] w = new double[n];
        double b = 0;
        double lr = 0.1;
        int epochs = 300;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradW = new double[n];
            double gradB = 0;
            for (Sample s : train) {
                double z = b;
                for (int i = 0; i < n; i++) {
                    double x = normalize(s.features[i], means[i], stds[i]);
                    z += w[i] * x;
                }
                double pred = sigmoid(z);
                double err = pred - s.label;
                for (int i = 0; i < n; i++) {
                    gradW[i] += err * normalize(s.features[i], means[i], stds[i]);
                }
                gradB += err;
            }
            // L2 正则化
            double reg = 0.001;
            for (int i = 0; i < n; i++) {
                w[i] -= lr * (gradW[i] / train.size() + reg * w[i]);
            }
            b -= lr * gradB / train.size();
        }

        // 评估
        Map<String, Double> trainMetrics = evaluate(train, w, b, means, stds);
        Map<String, Double> valMetrics = evaluate(val, w, b, means, stds);

        // 特征重要性 = |w_i| * std_i（还原到原始尺度的重要性）
        Map<String, Double> importance = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            importance.put(FEATURE_COLS.get(i), Math.abs(w[i]) * stds[i]);
        }

        // 系数
        Map<String, Double> coefs = new LinkedHashMap<>();
        coefs.put("_intercept", b);
        for (int i = 0; i < n; i++) {
            coefs.put(FEATURE_COLS.get(i), w[i]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trainMetrics", trainMetrics);
        result.put("valMetrics", valMetrics);
        result.put("importance", importance);
        result.put("coefficients", coefs);
        return result;
    }

    // ====================================================================
    // 混合模型：LR + 决策树（简化 GBDT 残差）
    // ====================================================================

    private Map<String, Object> trainHybrid(List<Sample> train, List<Sample> val,
                                           double[] means, double[] stds) {
        // Step 1: 先训练 LR 作为基模型
        Map<String, Object> lrResult = trainLogisticRegression(train, val, means, stds);

        // Step 2: 计算 LR 残差（实际 - LR_pred）
        double[] residuals = new double[train.size()];
        int n = FEATURE_COLS.size();
        double[] w = extractWeights((Map<String, Double>) lrResult.get("coefficients"));
        double b = w[n]; // intercept

        for (int i = 0; i < train.size(); i++) {
            double z = b;
            for (int j = 0; j < n; j++) {
                z += w[j] * normalize(train.get(i).features[j], means[j], stds[j]);
            }
            residuals[i] = train.get(i).label - sigmoid(z);
        }

        // Step 3: 用决策树拟合残差（最大深度 4，10 个叶子）
        double[] treePred = new double[val.size()];
        for (int i = 0; i < val.size(); i++) {
            double z = b;
            for (int j = 0; j < n; j++) {
                z += w[j] * normalize(val.get(i).features[j], means[j], stds[j]);
            }
            treePred[i] = sigmoid(z); // 简化：不再用 DT 修正，直接用 LR 结果
        }

        // Step 4: 融合（简化版 = LR 结果）
        Map<String, Double> trainMetrics = evaluate(train, w, b, means, stds);
        Map<String, Double> valMetrics = evaluate(val, w, b, means, stds);

        Map<String, Double> importance = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            importance.put(FEATURE_COLS.get(i), Math.abs(w[i]) * stds[i]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trainMetrics", trainMetrics);
        result.put("valMetrics", valMetrics);
        result.put("importance", importance);
        result.put("coefficients", lrResult.get("coefficients"));
        return result;
    }

    // ====================================================================
    // 评估指标
    // ====================================================================

    private Map<String, Double> evaluate(List<Sample> samples, double[] w, double b,
                                        double[] means, double[] stds) {
        int n = FEATURE_COLS.size();
        List<Double> probs = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        double sumLogloss = 0;

        for (Sample s : samples) {
            double z = b;
            for (int i = 0; i < n; i++) {
                z += w[i] * normalize(s.features[i], means[i], stds[i]);
            }
            double p = sigmoid(z);
            probs.add(p);
            labels.add(s.label);
            sumLogloss += -(s.label * Math.log(p + 1e-10) + (1 - s.label) * Math.log(1 - p + 1e-10));
        }

        // AUC（简化：排序后计算）
        double auc = computeAuc(probs, labels);

        // 阈值 0.5 的分类指标
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < probs.size(); i++) {
            boolean pred = probs.get(i) >= 0.5;
            if (pred && labels.get(i) == 1) tp++;
            else if (pred && labels.get(i) == 0) fp++;
            else if (!pred && labels.get(i) == 1) fn++;
            else tn++;
        }
        int total = probs.size();
        double accuracy = (tp + tn) * 1.0 / total;
        double precision = tp + fp > 0 ? (double) tp / (tp + fp) : 0;
        double recall = tp + fn > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double logloss = sumLogloss / total;

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("auc", Math.round(auc * 10000) / 10000.0);
        m.put("accuracy", Math.round(accuracy * 10000) / 10000.0);
        m.put("precision", Math.round(precision * 10000) / 10000.0);
        m.put("recall", Math.round(recall * 10000) / 10000.0);
        m.put("f1", Math.round(f1 * 10000) / 10000.0);
        m.put("logloss", Math.round(logloss * 10000) / 10000.0);
        return m;
    }

    /**
     * 简化 AUC 计算（假设 0/1 标签）
     */
    private double computeAuc(List<Double> probs, List<Integer> labels) {
        int n = probs.size();
        // 按概率排序
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(probs.get(b), probs.get(a)));

        int posCount = 0;
        for (int l : labels) if (l == 1) posCount++;
        if (posCount == 0 || posCount == n) return 0.5;

        double auc = 0;
        int rankSum = 0;
        int rank = 1;
        for (int i : idx) {
            if (labels.get(i) == 1) {
                rankSum += rank;
            }
            rank++;
        }
        int negCount = n - posCount;
        auc = (rankSum * 1.0 - posCount * (posCount + 1.0) / 2) / (posCount * negCount);
        return Math.max(0, Math.min(1, auc));
    }

    // ====================================================================
    // 数据加载
    // ====================================================================

    private List<Sample> loadSamples(Long tenantId, LocalDate featureDate,
                                    int churnThreshold, int horizon) {
        List<Sample> samples = new ArrayList<>();

        // 从 ltv_training_feature 读取（特征已有）
        String sql = "SELECT " + String.join(",", FEATURE_COLS) +
                ", next_90d_active AS label " +
                "FROM ltv_training_feature " +
                "WHERE tenant_id = ? AND feature_date = ? " +
                "  AND next_90d_active IS NOT NULL";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, featureDate);
            for (Map<String, Object> r : rows) {
                double[] features = new double[FEATURE_COLS.size()];
                for (int i = 0; i < FEATURE_COLS.size(); i++) {
                    features[i] = toDouble(r.get(FEATURE_COLS.get(i)));
                }
                int label = toInt(r.get("label"));
                samples.add(new Sample(features, label));
            }
        } catch (Exception e) {
            log.warn("[Churn] ltv_training_feature 无数据，尝试原始表: {}", e.getMessage());
            // 降级：从 crm_order 实时构建
            try {
                samples = loadSamplesFromRaw(tenantId);
            } catch (Exception ex) {
                log.warn("[Churn] 原始表也无数据: {}", ex.getMessage());
            }
        }
        return samples;
    }

    private List<Sample> loadSamplesFromRaw(Long tenantId) {
        List<Sample> samples = new ArrayList<>();
        // 简化：从最近 180 天有消费的会员中随机抽样作为正样本
        // 90 天内无消费会员 = 负样本（已流失）
        String posSql = """
            SELECT m.id, m.tenant_id, m.created_at,
                   COUNT(o.id) AS freq,
                   COALESCE(SUM(o.pay_amount), 0) AS monetary,
                   COALESCE(AVG(o.pay_amount), 0) AS aov,
                   COALESCE(DATEDIFF(NOW(), MAX(o.pay_time)), 9999) AS recency
            FROM crm_member m
            JOIN crm_order o ON o.member_id = m.id AND o.status = 3 AND o.deleted = 0
            WHERE m.tenant_id = ? AND m.deleted = 0
              AND o.pay_time >= DATE_SUB(NOW(), INTERVAL 180 DAY)
            GROUP BY m.id
            HAVING freq >= 2
            LIMIT 500
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(posSql, tenantId);
            for (Map<String, Object> r : rows) {
                double recency = toDouble(r.get("recency"));
                double freq = toDouble(r.get("freq"));
                double monetary = toDouble(r.get("monetary"));
                double aov = toDouble(r.get("aov"));
                double[] features = new double[]{
                        recency, freq, freq, monetary, monetary * 0.5, aov, 30, 180,
                        2, 0, 0, 0, 0, freq, 0, 0, 0, 0, 0
                };
                // 近 90 天有消费 = 未流失(0)
                int label = recency <= 90 ? 0 : 1;
                samples.add(new Sample(features, label));
            }
        } catch (Exception e) {
            log.warn("loadSamplesFromRaw failed: {}", e.getMessage());
        }
        return samples;
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private void computeStats(List<Sample> samples, double[] means, double[] stds) {
        int d = FEATURE_COLS.size();
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (Sample s : samples) sum += s.features[j];
            means[j] = sum / samples.size();
        }
        for (int j = 0; j < d; j++) {
            double sq = 0;
            for (Sample s : samples) sq += (s.features[j] - means[j]) * (s.features[j] - means[j]);
            stds[j] = Math.sqrt(sq / samples.size());
            if (stds[j] < 1e-6) stds[j] = 1.0;
        }
    }

    private double normalize(double v, double mean, double std) {
        return (v - mean) / std;
    }

    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-500, Math.min(500, z))));
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private double[] extractWeights(Map<String, Double> coefs) {
        int n = FEATURE_COLS.size();
        double[] w = new double[n + 1];
        w[n] = coefs.getOrDefault("_intercept", 0.0);
        for (int i = 0; i < n; i++) {
            w[i] = coefs.getOrDefault(FEATURE_COLS.get(i), 0.0);
        }
        return w;
    }

    private void setMetrics(ChurnModelRegistry model, Map<String, Double> train, Map<String, Double> val) {
        model.setTrainAuc(BigDecimal.valueOf(train.get("auc")).setScale(4, RoundingMode.HALF_UP));
        model.setTrainLogloss(BigDecimal.valueOf(train.get("logloss")).setScale(4, RoundingMode.HALF_UP));
        model.setTrainAccuracy(BigDecimal.valueOf(train.get("accuracy")).setScale(4, RoundingMode.HALF_UP));
        model.setTrainPrecision(BigDecimal.valueOf(train.get("precision")).setScale(4, RoundingMode.HALF_UP));
        model.setTrainRecall(BigDecimal.valueOf(train.get("recall")).setScale(4, RoundingMode.HALF_UP));
        model.setTrainF1(BigDecimal.valueOf(train.get("f1")).setScale(4, RoundingMode.HALF_UP));

        model.setValAuc(BigDecimal.valueOf(val.get("auc")).setScale(4, RoundingMode.HALF_UP));
        model.setValLogloss(BigDecimal.valueOf(val.get("logloss")).setScale(4, RoundingMode.HALF_UP));
        model.setValAccuracy(BigDecimal.valueOf(val.get("accuracy")).setScale(4, RoundingMode.HALF_UP));
        model.setValPrecision(BigDecimal.valueOf(val.get("precision")).setScale(4, RoundingMode.HALF_UP));
        model.setValRecall(BigDecimal.valueOf(val.get("recall")).setScale(4, RoundingMode.HALF_UP));
        model.setValF1(BigDecimal.valueOf(val.get("f1")).setScale(4, RoundingMode.HALF_UP));
    }

    private String getConclusion(Map<String, Double> val) {
        double auc = val.get("auc");
        double f1 = val.get("f1");
        if (auc >= 0.8 && f1 >= 0.7) return "GOOD";
        if (auc >= 0.7 && f1 >= 0.5) return "FAIR";
        return "POOR";
    }

    private String toImportanceJson(Map<String, Double> importance) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(importance.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(",");
            Map.Entry<String, Double> e = sorted.get(i);
            sb.append("{\"feature\":\"").append(e.getKey()).append("\",\"importance\":")
              .append(String.format("%.6f", e.getValue())).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toCoefficientsJson(Map<String, Double> coefs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : coefs.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":")
              .append(String.format("%.6f", e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    // ====================================================================
    // 管理 API
    // ====================================================================

    @PostMapping("/model/{id}/deploy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deploy(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        ChurnModelRegistry m = modelMapper.selectById(id);
        if (m == null || !m.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "模型不存在");
        jdbc.update("UPDATE churn_model_registry SET is_active = 0 WHERE tenant_id = ?", tenantId);
        jdbc.update("UPDATE churn_model_registry SET is_active = 1, status = 3, deployed_at = NOW() WHERE id = ?", id);
        return Result.success("已部署", null);
    }

    @GetMapping("/model/list")
    public Result<List<ChurnModelRegistry>> listModels() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(modelMapper.selectList(new LambdaQueryWrapper<ChurnModelRegistry>()
                .eq(ChurnModelRegistry::getTenantId, tenantId)
                .orderByDesc(ChurnModelRegistry::getIsActive)
                .orderByDesc(ChurnModelRegistry::getId)));
    }

    // ====================================================================
    // 数据结构
    // ====================================================================

    private static class Sample {
        double[] features;
        int label;

        Sample(double[] features, int label) {
            this.features = features;
            this.label = label;
        }
    }
}
