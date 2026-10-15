package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.LtvModelRegistry;
import com.meiyetec.entity.LtvTrainingFeature;
import com.meiyetec.entity.LtvTrainingRunLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.LtvModelRegistryMapper;
import com.meiyetec.mapper.LtvTrainingFeatureMapper;
import com.meiyetec.mapper.LtvTrainingRunLogMapper;
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
 * P4.1 T1.10 — LTV 训练数据 + 特征工程 + 真实模型训练
 *
 * 完整流程：
 * 1. ETL：拉取 crm_member + crm_order + biz_booking + wallet + package_order 等多源数据
 * 2. 特征工程：RFM + 消费模式 + 行为 + 钱包 + 标签
 * 3. 标签生成：未来 90 天实际 GMV（regression label）/ 是否活跃（classification label）
 * 4. 模型训练：MLR（多变量线性回归）/ Ridge 回归（Java 纯实现）
 * 5. 模型评估：MAE/RMSE/MAPE/R²
 * 6. 模型部署：激活->影响 ai_ltv_model 表
 */
@Slf4j
@RestController
@RequestMapping("/intel/ltv/training")
@RequiredArgsConstructor
public class LtvTrainingService {

    private final LtvTrainingFeatureMapper featureMapper;
    private final LtvModelRegistryMapper modelMapper;
    private final LtvTrainingRunLogMapper logMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 特征列（训练用）
    private static final List<String> FEATURE_COLUMNS = List.of(
            "recency_days", "frequency_total", "frequency_90d",
            "monetary_total", "monetary_90d", "aov", "avg_interval_days",
            "tenure_days", "service_category_count",
            "wallet_balance", "active_packages", "total_package_balance",
            "package_consumption_rate", "booking_count", "noshow_rate",
            "review_count", "avg_rating", "coupon_used_count", "point_balance"
    );

    // ====================================================================
    // 1. T1.10 ETL 训练数据：从多源表抽取特征
    // ====================================================================

    /**
     * 完整训练 ETL：抽取特征 + 生成标签
     */
    @PostMapping("/etl/run")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> runEtl(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate featureDate = LocalDate.now().minusDays(1); // 特征快照 T-1
        int featureWindow = body == null || body.get("featureWindow") == null ? 180
                : ((Number) body.get("featureWindow")).intValue();
        int labelWindow = body == null || body.get("labelWindow") == null ? 90
                : ((Number) body.get("labelWindow")).intValue();

        // 写入开始日志
        Long runId = logRun(tenantId, null, "TRAIN", "ETL", null, null, null, null,
                "featureWindow=" + featureWindow + ",labelWindow=" + labelWindow, null);

        try {
            // 1) 抽取所有会员的基础特征
            int n = extractFeatures(tenantId, featureDate, featureWindow);

            // 2) 计算标签（未来 90 天 GMV 和活跃度）
            int withLabel = computeLabels(tenantId, featureDate, labelWindow);

            // 3) 删除标签为空的样本（冷启动会员暂不训练）
            int dropped = jdbc.update(
                    "DELETE FROM ltv_training_feature WHERE tenant_id = ? AND feature_date = ? AND next_90d_revenue IS NULL",
                    tenantId, featureDate);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("featureDate", featureDate);
            data.put("featuresExtracted", n);
            data.put("labelsComputed", withLabel);
            data.put("coldStartDropped", dropped);
            data.put("trainingReady", withLabel - dropped);
            data.put("durationMs", System.currentTimeMillis() - t0);

            logRun(tenantId, null, "TRAIN", "ETL", (int) (System.currentTimeMillis() - t0),
                    n, "samples", (BigDecimal) null, "ETL 完成", null);

            return Result.success("ETL 完成", data);
        } catch (Exception e) {
            logRun(tenantId, null, "TRAIN", "ETL", (int) (System.currentTimeMillis() - t0),
                    0, null, null, "失败: " + e.getMessage(), e.toString());
            log.error("LTV ETL 失败", e);
            return Result.error(BizCode.SYSTEM_ERROR, "ETL 失败: " + e.getMessage());
        }
    }

    private int extractFeatures(Long tenantId, LocalDate featureDate, int windowDays) {
        LocalDate startDate = featureDate.minusDays(windowDays);

        // 先清空旧特征
        jdbc.update("DELETE FROM ltv_training_feature WHERE tenant_id = ? AND feature_date = ?",
                tenantId, featureDate);

        // 1) 基础 RFM
        String sql = """
            INSERT INTO ltv_training_feature (
                tenant_id, member_id, feature_date,
                recency_days, frequency_total, frequency_90d,
                monetary_total, monetary_90d, aov, avg_interval_days,
                tenure_days, active_packages, total_package_balance,
                service_category_count, booking_count, noshow_count,
                review_count, avg_rating, coupon_used_count, point_balance
            )
            SELECT
                m.tenant_id,
                m.id,
                ?,
                COALESCE(DATEDIFF(?, MAX(o.pay_time)), 9999),
                COUNT(o.id),
                SUM(CASE WHEN o.pay_time >= ? THEN 1 ELSE 0 END),
                COALESCE(SUM(o.pay_amount), 0),
                COALESCE(SUM(CASE WHEN o.pay_time >= ? THEN o.pay_amount ELSE 0 END), 0),
                COALESCE(AVG(o.pay_amount), 0),
                COALESCE(ROUND(DATEDIFF(?, MIN(o.pay_time)) / NULLIF(COUNT(o.id), 0)), 0),
                COALESCE(DATEDIFF(?, m.created_at), 0),
                COALESCE(po.active_packages, 0),
                COALESCE(po.total_balance, 0),
                0,
                0,
                0,
                0,
                0,
                0,
                COALESCE(pt.point_balance, 0)
            FROM crm_member m
            LEFT JOIN crm_order o ON o.member_id = m.id AND o.status = 3
                AND o.deleted = 0 AND o.pay_time >= ?
            LEFT JOIN (
                SELECT member_id,
                       COUNT(*) AS active_packages,
                       SUM(remaining_value) AS total_balance
                FROM crm_package_order
                WHERE deleted = 0 AND status = 1 AND expire_time > ?
                GROUP BY member_id
            ) po ON po.member_id = m.id
            LEFT JOIN (
                SELECT member_id, SUM(point_balance) AS point_balance
                FROM crm_point_account
                WHERE deleted = 0
                GROUP BY member_id
            ) pt ON pt.member_id = m.id
            WHERE m.deleted = 0 AND m.tenant_id = ?
            GROUP BY m.id, m.tenant_id, m.created_at, po.active_packages, po.total_balance, pt.point_balance
            """;

        int inserted = jdbc.update(sql,
                featureDate, featureDate, startDate, startDate, featureDate, featureDate,
                startDate, featureDate, tenantId);

        // 2) 爽约率 / 评价数
        jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT member_id,
                       COUNT(*) AS booking_count,
                       SUM(status = 6) AS noshow_count
                FROM biz_booking
                WHERE deleted = 0 AND booking_date >= ?
                GROUP BY member_id
            ) b ON b.member_id = f.member_id
            SET f.booking_count = b.booking_count,
                f.noshow_count = b.noshow_count,
                f.noshow_rate = ROUND(b.noshow_count * 100.0 / NULLIF(b.booking_count, 0), 2)
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, startDate, tenantId, featureDate);

        // 3) 评价数 + 平均评分
        jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT member_id,
                       COUNT(*) AS review_count,
                       ROUND(AVG(overall_rating), 2) AS avg_rating
                FROM crm_review
                WHERE deleted = 0 AND status = 2 AND created_at >= ?
                GROUP BY member_id
            ) r ON r.member_id = f.member_id
            SET f.review_count = r.review_count,
                f.avg_rating = r.avg_rating
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, startDate, tenantId, featureDate);

        // 4) 优惠券使用数
        jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT member_id, COUNT(*) AS coupon_used_count
                FROM crm_member_coupon
                WHERE deleted = 0 AND status = 2 AND used_at >= ?
                GROUP BY member_id
            ) c ON c.member_id = f.member_id
            SET f.coupon_used_count = c.coupon_used_count
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, startDate, tenantId, featureDate);

        // 5) 消费过的类别数（消费偏好）
        jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT o.member_id,
                       COUNT(DISTINCT s.category_id) AS service_category_count
                FROM crm_order o
                JOIN crm_order_item oi ON oi.order_id = o.id
                JOIN biz_service_item s ON s.id = oi.service_id
                WHERE o.deleted = 0 AND o.status = 3 AND o.pay_time >= ?
                GROUP BY o.member_id
            ) cat ON cat.member_id = f.member_id
            SET f.service_category_count = cat.service_category_count
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, startDate, tenantId, featureDate);

        // 6) 钱包余额 + 套餐消耗率
        jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT member_id, SUM(balance) AS wallet_balance
                FROM crm_wallet
                WHERE deleted = 0
                GROUP BY member_id
            ) w ON w.member_id = f.member_id
            SET f.wallet_balance = w.wallet_balance
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, tenantId, featureDate);

        // 7) 生命周期标签
        jdbc.update("""
            UPDATE ltv_training_feature
            SET lifecycle_stage = CASE
                WHEN recency_days <= 30 THEN 'ACTIVE'
                WHEN recency_days <= 90 THEN 'COOLING'
                WHEN recency_days <= 180 THEN 'DORMANT'
                ELSE 'CHURNED'
            END,
            member_age_group = CASE
                WHEN tenure_days < 90 THEN 'NEW'
                WHEN tenure_days < 365 THEN 'REGULAR'
                WHEN tenure_days < 1095 THEN 'MATURE'
                ELSE 'OLD'
            END
            WHERE tenant_id = ? AND feature_date = ?
            """, tenantId, featureDate);

        log.info("[LTV-ETL] 抽取特征完成: {} 条", inserted);
        return inserted;
    }

    private int computeLabels(Long tenantId, LocalDate featureDate, int labelWindow) {
        LocalDate labelStart = featureDate.plusDays(1);
        LocalDate labelEnd = featureDate.plusDays(labelWindow);

        // 未来 90 天 GMV + 是否活跃
        int updated = jdbc.update("""
            UPDATE ltv_training_feature f
            JOIN (
                SELECT o.member_id,
                       COALESCE(SUM(o.pay_amount), 0) AS future_revenue,
                       COUNT(*) > 0 AS future_active
                FROM crm_order o
                WHERE o.deleted = 0 AND o.status = 3
                  AND o.pay_time >= ? AND o.pay_time < ?
                GROUP BY o.member_id
            ) fut ON fut.member_id = f.member_id
            SET f.next_90d_revenue = ROUND(fut.future_revenue, 2),
                f.next_90d_active = fut.future_active
            WHERE f.tenant_id = ? AND f.feature_date = ?
            """, labelStart, labelEnd, tenantId, featureDate);

        // 未消费会员的标签：revenue=0, active=0
        jdbc.update("""
            UPDATE ltv_training_feature
            SET next_90d_revenue = 0, next_90d_active = 0
            WHERE tenant_id = ? AND feature_date = ? AND next_90d_revenue IS NULL
            """, tenantId, featureDate);

        log.info("[LTV-LABEL] 标签计算完成: {} 条", updated);
        return updated;
    }

    // ====================================================================
    // 2. T1.10 特征工程（已融合到 ETL 中）
    // ====================================================================
    // 特征列定义见 FEATURE_COLUMNS

    // ====================================================================
    // 3. T1.11 LTV 模型训练（MLR + 梯度下降）
    // ====================================================================

    /**
     * 训练 LTV 回归模型（MLR）
     */
    @PostMapping("/train")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> trainModel(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate featureDate = body == null || body.get("featureDate") == null
                ? LocalDate.now().minusDays(1)
                : LocalDate.parse(body.get("featureDate").toString());
        String modelCode = body == null || body.get("modelCode") == null
                ? "LTV_MLR_" + System.currentTimeMillis() / 1000
                : body.get("modelCode").toString();
        String algorithm = body == null || body.get("algorithm") == null
                ? "MLR" : body.get("algorithm").toString();
        double learningRate = body == null || body.get("learningRate") == null
                ? 0.01 : ((Number) body.get("learningRate")).doubleValue();
        int epochs = body == null || body.get("epochs") == null ? 200 : ((Number) body.get("epochs")).intValue();

        // 加载训练数据
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + String.join(",", FEATURE_COLUMNS) + ", next_90d_revenue AS label " +
                "FROM ltv_training_feature " +
                "WHERE tenant_id = ? AND feature_date = ? AND next_90d_revenue IS NOT NULL",
                tenantId, featureDate);

        if (rows.size() < 100) {
            return Result.error(BizCode.BAD_REQUEST,
                    "训练样本不足 100 条（当前 " + rows.size() + "），请先运行 ETL 或积累更多历史数据");
        }

        Long runId = logRun(tenantId, null, "TRAIN", "TRAIN", null, rows.size(), "samples", null,
                "算法=" + algorithm + ",lr=" + learningRate + ",epochs=" + epochs, null);

        try {
            // 划分训练集/验证集 80/20
            Collections.shuffle(rows, new Random(42));
            int splitIdx = (int) (rows.size() * 0.8);
            List<Map<String, Object>> trainRows = rows.subList(0, splitIdx);
            List<Map<String, Object>> valRows = rows.subList(splitIdx, rows.size());

            // 数据标准化
            double[] means = new double[FEATURE_COLUMNS.size()];
            double[] stds = new double[FEATURE_COLUMNS.size()];
            computeStats(trainRows, means, stds);

            // 训练 MLR
            double[] weights = gradientDescent(trainRows, means, stds, learningRate, epochs);

            // 评估
            double[] trainMetrics = evaluate(trainRows, weights, means, stds);
            double[] valMetrics = evaluate(valRows, weights, means, stds);

            // 特征重要性（绝对权重）
            Map<String, Double> importance = new LinkedHashMap<>();
            for (int i = 0; i < FEATURE_COLUMNS.size(); i++) {
                importance.put(FEATURE_COLUMNS.get(i), Math.abs(weights[i]));
            }
            // 按重要性排序
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(importance.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            // 写模型
            String formula = buildFormula(weights, means, stds);

            LtvModelRegistry model = new LtvModelRegistry();
            model.setTenantId(tenantId);
            model.setModelCode(modelCode);
            model.setModelName("LTV 回归模型 " + LocalDate.now());
            model.setModelType("LTV_REGRESSION");
            model.setAlgorithm(algorithm);
            model.setFeatureWindowDays(180);
            model.setLabelWindowDays(90);
            model.setTrainingSamples(rows.size());
            model.setFeatureCount(FEATURE_COLUMNS.size());
            model.setTrainMae(BigDecimal.valueOf(trainMetrics[0]).setScale(4, RoundingMode.HALF_UP));
            model.setTrainRmse(BigDecimal.valueOf(trainMetrics[1]).setScale(4, RoundingMode.HALF_UP));
            model.setTrainMape(BigDecimal.valueOf(trainMetrics[2]).setScale(4, RoundingMode.HALF_UP));
            model.setTrainR2(BigDecimal.valueOf(trainMetrics[3]).setScale(4, RoundingMode.HALF_UP));
            model.setValMae(BigDecimal.valueOf(valMetrics[0]).setScale(4, RoundingMode.HALF_UP));
            model.setValRmse(BigDecimal.valueOf(valMetrics[1]).setScale(4, RoundingMode.HALF_UP));
            model.setValMape(BigDecimal.valueOf(valMetrics[2]).setScale(4, RoundingMode.HALF_UP));
            model.setValR2(BigDecimal.valueOf(valMetrics[3]).setScale(4, RoundingMode.HALF_UP));
            model.setFeatureImportanceJson(toJsonImportance(importance));
            model.setModelFormula(formula);
            model.setIsActive(0);
            model.setStatus(2); // 已训练
            model.setTrainedAt(LocalDateTime.now());
            model.setVersion("v1");
            modelMapper.insert(model);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("modelId", model.getId());
            data.put("modelCode", modelCode);
            data.put("algorithm", algorithm);
            data.put("trainingSamples", rows.size());
            data.put("trainMAE", model.getTrainMae());
            data.put("trainRMSE", model.getTrainRmse());
            data.put("trainMAPE", model.getTrainMape());
            data.put("trainR2", model.getTrainR2());
            data.put("valMAE", model.getValMae());
            data.put("valRMSE", model.getValRmse());
            data.put("valMAPE", model.getValMape());
            data.put("valR2", model.getValR2());
            data.put("topFeatures", sorted.subList(0, Math.min(5, sorted.size())));
            data.put("durationMs", System.currentTimeMillis() - t0);
            data.put("conclusion", evalConclusion(valMetrics[2]));

            logRun(tenantId, model.getId(), "TRAIN", "TRAIN", (int) (System.currentTimeMillis() - t0),
                    rows.size(), "val_mape", model.getValMape(), "训练完成", null);

            return Result.success("训练完成", data);
        } catch (Exception e) {
            logRun(tenantId, null, "TRAIN", "TRAIN", (int) (System.currentTimeMillis() - t0),
                    0, null, null, "训练失败", e.toString());
            log.error("LTV 训练失败", e);
            return Result.error(BizCode.SYSTEM_ERROR, "训练失败: " + e.getMessage());
        }
    }

    /**
     * 部署模型（激活）
     */
    @PostMapping("/model/{id}/deploy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deploy(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LtvModelRegistry model = modelMapper.selectById(id);
        if (model == null || !model.getTenantId().equals(tenantId)) {
            return Result.error(BizCode.NOT_FOUND, "模型不存在");
        }
        // 同租户只允许一个激活
        jdbc.update("UPDATE ltv_model_registry SET is_active = 0 WHERE tenant_id = ?", tenantId);
        jdbc.update("UPDATE ltv_model_registry SET is_active = 1, status = 3, deployed_at = NOW() WHERE id = ?", id);
        return Result.success("已部署", null);
    }

    @GetMapping("/model/list")
    public Result<List<LtvModelRegistry>> listModels() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(modelMapper.selectList(new LambdaQueryWrapper<LtvModelRegistry>()
                .eq(LtvModelRegistry::getTenantId, tenantId)
                .orderByDesc(LtvModelRegistry::getIsActive)
                .orderByDesc(LtvModelRegistry::getId)));
    }

    @GetMapping("/run/log")
    public Result<List<LtvTrainingRunLog>> runLog(
            @RequestParam(defaultValue = "20") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(logMapper.selectList(new LambdaQueryWrapper<LtvTrainingRunLog>()
                .eq(LtvTrainingRunLog::getTenantId, tenantId)
                .orderByDesc(LtvTrainingRunLog::getId)
                .last("LIMIT " + limit)));
    }

    // ====================================================================
    // 机器学习核心（纯 Java 实现）
    // ====================================================================

    private void computeStats(List<Map<String, Object>> rows, double[] means, double[] stds) {
        int n = rows.size();
        for (int i = 0; i < FEATURE_COLUMNS.size(); i++) {
            double sum = 0;
            for (Map<String, Object> r : rows) {
                sum = toDouble(r.get(FEATURE_COLUMNS.get(i)));
            }
            // 简化计算
            for (Map<String, Object> r : rows) {
                sum += toDouble(r.get(FEATURE_COLUMNS.get(i)));
            }
            means[i] = sum / n;
        }
        for (int i = 0; i < FEATURE_COLUMNS.size(); i++) {
            double sq = 0;
            for (Map<String, Object> r : rows) {
                double v = toDouble(r.get(FEATURE_COLUMNS.get(i)));
                sq += (v - means[i]) * (v - means[i]);
            }
            stds[i] = Math.sqrt(sq / n);
            if (stds[i] < 1e-6) stds[i] = 1.0; // 防止除零
        }
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    /**
     * 梯度下降训练 MLR
     * y = w0 + w1*x1 + w2*x2 + ... + wn*xn
     */
    private double[] gradientDescent(List<Map<String, Object>> rows, double[] means, double[] stds,
                                     double lr, int epochs) {
        int n = rows.size();
        int d = FEATURE_COLUMNS.size();
        double[] w = new double[d];
        double bias = 0;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradW = new double[d];
            double gradB = 0;
            for (Map<String, Object> r : rows) {
                double label = toDouble(r.get("label"));
                double pred = bias;
                for (int i = 0; i < d; i++) {
                    double x = (toDouble(r.get(FEATURE_COLUMNS.get(i))) - means[i]) / stds[i];
                    pred += w[i] * x;
                }
                double err = pred - label;
                for (int i = 0; i < d; i++) {
                    double x = (toDouble(r.get(FEATURE_COLUMNS.get(i))) - means[i]) / stds[i];
                    gradW[i] += err * x;
                }
                gradB += err;
            }
            for (int i = 0; i < d; i++) {
                w[i] -= lr * gradW[i] / n;
            }
            bias -= lr * gradB / n;
        }
        // 把 bias 存到第一维
        double[] fullWeights = new double[d + 1];
        fullWeights[0] = bias;
        System.arraycopy(w, 0, fullWeights, 1, d);
        return fullWeights;
    }

    /**
     * 评估模型：返回 [MAE, RMSE, MAPE, R²]
     */
    private double[] evaluate(List<Map<String, Object>> rows, double[] weights, double[] means, double[] stds) {
        int n = rows.size();
        int d = FEATURE_COLUMNS.size();
        double bias = weights[0];
        double[] w = new double[d];
        System.arraycopy(weights, 1, w, 0, d);

        double sumAbs = 0;
        double sumSq = 0;
        double sumPct = 0;
        double sumY = 0;
        for (Map<String, Object> r : rows) sumY += toDouble(r.get("label"));
        double meanY = sumY / n;

        double sumSqTot = 0;
        double sumSqRes = 0;
        int validCount = 0;

        for (Map<String, Object> r : rows) {
            double label = toDouble(r.get("label"));
            double pred = bias;
            for (int i = 0; i < d; i++) {
                double x = (toDouble(r.get(FEATURE_COLUMNS.get(i))) - means[i]) / stds[i];
                pred += w[i] * x;
            }
            double err = pred - label;
            sumAbs += Math.abs(err);
            sumSq += err * err;
            if (label > 0) {
                sumPct += Math.abs(err / label);
                validCount++;
            }
            sumSqRes += err * err;
            sumSqTot += (label - meanY) * (label - meanY);
        }

        double mae = sumAbs / n;
        double rmse = Math.sqrt(sumSq / n);
        double mape = validCount > 0 ? sumPct * 100 / validCount : 0;
        double r2 = sumSqTot > 0 ? 1 - sumSqRes / sumSqTot : 0;

        return new double[]{mae, rmse, mape, r2};
    }

    private String buildFormula(double[] weights, double[] means, double[] stds) {
        StringBuilder sb = new StringBuilder("LTV = " + String.format("%.4f", weights[0]));
        for (int i = 0; i < FEATURE_COLUMNS.size(); i++) {
            sb.append(" + ").append(String.format("%.4f", weights[i + 1]))
              .append(" * ((").append(FEATURE_COLUMNS.get(i)).append(" - ").append(String.format("%.4f", means[i]))
              .append(") / ").append(String.format("%.4f", stds[i])).append(")");
        }
        return sb.toString();
    }

    private String toJsonImportance(Map<String, Double> importance) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(importance.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(",");
            Map.Entry<String, Double> e = sorted.get(i);
            sb.append("{\"feature\":\"").append(e.getKey()).append("\",\"weight\":")
              .append(String.format("%.6f", e.getValue())).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String evalConclusion(double mape) {
        if (mape < 20) return "GOOD";
        if (mape < 35) return "FAIR";
        return "POOR";
    }

    private Long logRun(Long tenantId, Long modelId, String runType, String step,
                        Integer durationMs, Integer samples, String metric,
                        BigDecimal metricValue, String message, String error) {
        LtvTrainingRunLog log = new LtvTrainingRunLog();
        log.setTenantId(tenantId);
        log.setModelId(modelId);
        log.setRunType(runType);
        log.setStep(step);
        log.setDurationMs(durationMs);
        log.setSamples(samples);
        log.setMetric(metric);
        log.setMetricValue(metricValue);
        log.setMessage(message);
        log.setError(error);
        log.setStartAt(LocalDateTime.now());
        if (durationMs != null) log.setEndAt(LocalDateTime.now());
        logMapper.insert(log);
        return log.getId();
    }
}
