package com.meiyetec.service;

import com.meiyetec.ai.CvModelClient;
import com.meiyetec.ai.CvModelClient.CvAnalysisRequest;
import com.meiyetec.ai.CvModelClient.CvAnalysisResult;
import com.meiyetec.entity.MhVisualArchive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.*;

/**
 * R3.0-H P1.6：视觉档案 AI 特征分析
 * <p>
 * AI-006 改造：将原 {@code VisualArchiveService.mockAiAnalyze} 中的 Random 模拟替换为
 * <b>确定性哈希算法</b>（同一 hash 永远产出相同特征 → 可复现、可回测、可缓存）。
 * <p>
 * AI-006 升级：支持注入 {@link CvModelClient} SPI，优先调用真实 CV 模型（腾讯云/阿里云/本地 CNN），
 * 未配置时自动降级为确定性哈希算法（向后完全兼容）。
 *
 * <p>路由规则（{@link #analyzeWithImageUrl}）：
 * <ol>
 *   <li>有 imageUrl + provider 已配置 → {@link CvModelClient}（真实 HTTP 调用）</li>
 *   <li>有 imageUrl 但 provider 未配置 → 确定性哈希算法</li>
 *   <li>无 imageUrl → 确定性哈希算法（与旧行为完全一致）</li>
 * </ol>
 */
@Slf4j
@Service
public class VisualAiService {

    /** AI-006：CV 模型客户端 SPI — 注入真实模型路由（腾讯云/阿里云/本地 CNN） */
    @Autowired(required = false)
    private CvModelClient cvModelClient;

    // ====================================================================
    // 1. 特征分析（AI-006：优先真实模型，降级确定性算法）
    // ====================================================================

    /**
     * AI-006：分析视觉档案特征（支持 imageUrl + imageHash 双输入）
     *
     * <p>路由规则：
     * <ol>
     *   <li>有 imageUrl + provider 已配置 → {@link CvModelClient#analyze}（真实 HTTP 调用）</li>
     *   <li>有 imageUrl 但 provider 未配置 → 确定性哈希算法</li>
     *   <li>无 imageUrl → 确定性哈希算法（与旧行为完全一致）</li>
     * </ol>
     *
     * @param archiveType 档案类型（SKIN / FACE / BODY / HAIR）
     * @param imageUrl    图像 URL（可选，真实模型必需）
     * @param imageHash   图像哈希（确定性降级用）
     * @return 特征 + 标签 + 综合分（格式与旧 {@code analyzeFeatures} 完全一致）
     */
    public Map<String, Object> analyzeWithImageUrl(String archiveType, String imageUrl, String imageHash) {
        if (cvModelClient != null) {
            try {
                CvAnalysisResult r = cvModelClient.analyze(CvAnalysisRequest.builder()
                        .archiveType(archiveType)
                        .imageUrl(imageUrl)
                        .imageHash(imageHash)
                        .build());
                if (r != null) {
                    log.info("[VisualAi] real model analyze: engine={} mode={} score={}",
                            r.getEngine(), r.getInferenceMode(), r.getScore());
                    return buildAnalysisResponse(r);
                }
            } catch (Exception e) {
                log.warn("[VisualAi] CvModelClient.analyze threw: {}, fallback deterministic",
                        e.getMessage());
            }
        }
        // 确定性降级（与旧 mockAiAnalyze 行为一致）
        return analyzeFeaturesDeterministic(archiveType, imageHash);
    }

    /**
     * 旧签名兼容：仅传入 archiveType + imageHash，内部委托 {@link #analyzeWithImageUrl}。
     * provider 未配置时走确定性算法。
     */
    public Map<String, Object> analyzeFeatures(String archiveType, String imageHash) {
        return analyzeWithImageUrl(archiveType, null, imageHash);
    }

    // ====================================================================
    // 2. 对比分析（AI-006：优先真实模型对比，降级确定性算法）
    // ====================================================================

    /**
     * AI-006：对比两个档案的差异（支持 imageUrl）
     *
     * <p>路由规则：
     * <ol>
     *   <li>两张图都有 imageUrl + provider 已配置 → {@link CvModelClient#compare}（真实模型对比）</li>
     *   <li>否则 → 确定性哈希算法（解析 {@code aiFeaturesJson} 或用 hash 派生）</li>
     * </ol>
     *
     * @param before 档案 A（before）
     * @param after  档案 B（after）
     * @return similarity / skinDiff / improvement / regression / summary
     */
    public Map<String, Object> compareArchives(MhVisualArchive before, MhVisualArchive after) {
        String beforeUrl = before == null ? null : before.getImageUrl();
        String afterUrl = after == null ? null : after.getImageUrl();
        String beforeHash = before == null ? null : before.getImageHash();
        String afterHash = after == null ? null : after.getImageHash();

        if (cvModelClient != null && beforeUrl != null && afterUrl != null) {
            try {
                var r = cvModelClient.compare(
                        CvModelClient.CvComparisonRequest.builder()
                                .beforeUrl(beforeUrl)
                                .beforeHash(beforeHash)
                                .afterUrl(afterUrl)
                                .afterHash(afterHash)
                                .build());
                if (r != null) {
                    log.info("[VisualAi] real model compare: engine={} mode={} similarity={}",
                            r.getEngine(), r.getInferenceMode(), r.getSimilarity());
                    return buildComparisonResponse(r);
                }
            } catch (Exception e) {
                log.warn("[VisualAi] CvModelClient.compare threw: {}, fallback deterministic", e.getMessage());
            }
        }

        // 确定性降级：解析已有 aiFeaturesJson（无则用 hash 派生）
        return compareArchivesDeterministic(before, after);
    }

    // ====================================================================
    // 确定性算法（无 Random — 与旧 mockAiAnalyze/mockAiCompare 行为完全一致）
    // ====================================================================

    private Map<String, Object> analyzeFeaturesDeterministic(String archiveType, String imageHash) {
        if (imageHash == null || imageHash.isEmpty()) imageHash = "default-hash";
        long seed = stableSeed(imageHash);
        Map<String, Double> weights = weightsForType(archiveType);
        Map<String, Object> features = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double base = deterministicUniform(seed, e.getKey());
            features.put(e.getKey(), round(base * e.getValue()));
        }
        double composite = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Double v = (Double) features.get(e.getKey());
            composite += v * e.getValue() * 100;
        }
        BigDecimal score = BigDecimal.valueOf(composite).setScale(2, RoundingMode.HALF_UP);
        String tags = autoTags(archiveType, features);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("features", features);
        res.put("tags", tags);
        res.put("score", score);
        res.put("engine", "visual-ai-v1.0");
        res.put("deterministic", true);
        res.put("imageHash", imageHash);
        return res;
    }

    private Map<String, Object> compareArchivesDeterministic(MhVisualArchive before, MhVisualArchive after) {
        String beforeHash = before == null ? "empty-before" : before.getImageHash();
        String afterHash = after == null ? "empty-after" : after.getImageHash();
        long seed = stableSeed(beforeHash + ":" + afterHash);
        Map<String, Double> beforeFeat = parseFeatures(before);
        Map<String, Double> afterFeat = parseFeatures(after);
        if (beforeFeat.isEmpty()) {
            Map<String, Double> w = weightsForType("SKIN");
            for (String k : w.keySet()) {
                beforeFeat.put(k, deterministicUniform(seed, "before:" + k) * w.get(k));
            }
        }
        if (afterFeat.isEmpty()) {
            Map<String, Double> w = weightsForType("SKIN");
            for (String k : w.keySet()) {
                afterFeat.put(k, deterministicUniform(stableSeed(afterHash), "after:" + k) * w.get(k));
            }
        }
        double cosSim = cosineSim(beforeFeat, afterFeat);
        double improvement = 0, totalWeight = 0;
        Map<String, Double> skinDiff = new LinkedHashMap<>();
        for (String dim : new String[]{"brightness", "saturation", "symmetry", "texture_score"}) {
            double b = beforeFeat.getOrDefault(dim, 0.5);
            double a = afterFeat.getOrDefault(dim, 0.5);
            double diff = (a - b) * 10;
            skinDiff.put(dim, round(diff, 2));
            double w = Math.abs(diff) > 0.5 ? 1.0 : 0.5;
            improvement += Math.max(0, diff) * w;
            totalWeight += w;
        }
        if (totalWeight > 0) improvement /= totalWeight;
        BigDecimal diffScore = BigDecimal.valueOf(improvement * 10 + 5).setScale(2, RoundingMode.HALF_UP);
        BigDecimal similarity = BigDecimal.valueOf(cosSim).setScale(4, RoundingMode.HALF_UP);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("similarity", similarity);
        res.put("diffScore", diffScore);
        res.put("skinDiff", skinDiff);
        res.put("improvement", buildImprovement(skinDiff));
        res.put("regression", buildRegression(skinDiff));
        res.put("summary", String.format("整体改善 %.1f 分，相似度 %.2f，建议继续当前疗程", diffScore, similarity));
        res.put("engine", "visual-ai-v1.0");
        return res;
    }

    // ====================================================================
    // Response builder helpers（将 CvAnalysisResult / CvComparisonResult 转为 Map）
    // ====================================================================

    private Map<String, Object> buildAnalysisResponse(CvAnalysisResult r) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("features", r.getFeatures());
        res.put("tags", r.getTags());
        res.put("score", r.getScore());
        res.put("engine", r.getEngine());
        res.put("inferenceMode", r.getInferenceMode());
        res.put("deterministic", "deterministic".equals(r.getInferenceMode()));
        res.put("imageHash", r.getImageHash());
        return res;
    }

    private Map<String, Object> buildComparisonResponse(CvModelClient.CvComparisonResult r) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("similarity", r.getSimilarity());
        res.put("diffScore", r.getDiffScore());
        res.put("skinDiff", r.getSkinDiff());
        res.put("improvement", r.getImprovement());
        res.put("regression", r.getRegression());
        res.put("summary", r.getSummary());
        res.put("engine", r.getEngine());
        res.put("inferenceMode", r.getInferenceMode());
        return res;
    }

    // ====================================================================
    // Math utilities
    // ====================================================================

    private static long stableSeed(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes("UTF-8"));
            long val = 0;
            for (int i = 0; i < 8; i++) val = (val << 8) | (digest[i] & 0xFF);
            return val;
        } catch (Exception e) {
            return input.hashCode();
        }
    }

    private static double deterministicUniform(long seed, String key) {
        long h = seed;
        for (char c : key.toCharArray()) h = h * 31L + c;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return ((h & 0x7FFFFFFFFFFFFFFFL) % 1_000_000L) / 1_000_000.0;
    }

    private static Map<String, Double> weightsForType(String type) {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("brightness", 0.30); w.put("saturation", 0.20);
        w.put("symmetry", 0.25);   w.put("texture_score", 0.25);
        if (type != null) {
            String t = type.toUpperCase();
            if (t.contains("SKIN")) {
                w.put("brightness", 0.30); w.put("saturation", 0.15);
                w.put("symmetry", 0.15);   w.put("texture_score", 0.40);
            } else if (t.contains("FACE")) {
                w.put("brightness", 0.20); w.put("saturation", 0.10);
                w.put("symmetry", 0.50);   w.put("texture_score", 0.20);
            } else if (t.contains("BODY")) {
                w.put("brightness", 0.25); w.put("saturation", 0.25);
                w.put("symmetry", 0.20);   w.put("texture_score", 0.30);
            }
        }
        return w;
    }

    private static String autoTags(String type, Map<String, Object> features) {
        List<String> tags = new ArrayList<>();
        double bright = ((Number) features.getOrDefault("brightness", 0.5)).doubleValue();
        double sat = ((Number) features.getOrDefault("saturation", 0.5)).doubleValue();
        double sym = ((Number) features.getOrDefault("symmetry", 0.5)).doubleValue();
        double tex = ((Number) features.getOrDefault("texture_score", 0.5)).doubleValue();
        if (type != null && type.contains("SKIN")) {
            if (bright > 0.6) tags.add("光泽");
            if (bright < 0.3) tags.add("暗沉");
            if (sat > 0.6) tags.add("水润");
            if (tex > 0.7) tags.add("细腻");
            if (tex < 0.3) tags.add("粗糙");
            if (sym > 0.65) tags.add("均匀");
            if (sym < 0.35) tags.add("色差");
        } else if (type != null && type.contains("FACE")) {
            if (sym > 0.6) tags.add("对称");
            if (sym < 0.4) tags.add("不对称");
            if (bright > 0.6) tags.add("轮廓清晰");
            if (tex > 0.7) tags.add("苹果肌饱满");
            if (tex < 0.3) tags.add("法令纹");
        } else {
            if (sym > 0.6) tags.add("均匀");
            if (tex > 0.7) tags.add("紧致");
            if (tex < 0.3) tags.add("松弛");
            if (bright < 0.3) tags.add("色差");
            if (sat > 0.6) tags.add("润泽");
        }
        if (tags.isEmpty()) tags.add("常规");
        return String.join(",", tags);
    }

    private static double cosineSim(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.5;
        double dot = 0, na = 0, nb = 0;
        for (String k : a.keySet()) {
            double av = a.getOrDefault(k, 0.0);
            double bv = b.getOrDefault(k, 0.0);
            dot += av * bv; na += av * av; nb += bv * bv;
        }
        if (na == 0 || nb == 0) return 0.5;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double round(double v) { return round(v, 4); }
    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static Map<String, Double> parseFeatures(MhVisualArchive a) {
        Map<String, Double> m = new HashMap<>();
        if (a == null) return m;
        try {
            String json = a.getAiFeaturesJson();
            if (json == null || json.isEmpty()) return m;
            for (String kv : json.replace("{", "").replace("}", "").split(",")) {
                String[] pair = kv.split(":");
                if (pair.length == 2) {
                    m.put(pair[0].trim(), Double.parseDouble(pair[1].trim()));
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    private static String buildImprovement(Map<String, Double> diff) {
        List<String> items = new ArrayList<>();
        if (diff.getOrDefault("brightness", 0.0) > 0.5) items.add("亮度提升");
        if (diff.getOrDefault("saturation", 0.0) > 0.5) items.add("含水量提升");
        if (diff.getOrDefault("texture_score", 0.0) > 0.5) items.add("细腻度提升");
        if (diff.getOrDefault("symmetry", 0.0) > 0.5) items.add("均匀度提升");
        if (items.isEmpty()) items.add("整体改善");
        return String.join("、", items);
    }

    private static String buildRegression(Map<String, Double> diff) {
        List<String> items = new ArrayList<>();
        if (diff.getOrDefault("brightness", 0.0) < -0.5) items.add("亮度下降");
        if (diff.getOrDefault("saturation", 0.0) < -0.5) items.add("含水量下降");
        if (diff.getOrDefault("texture_score", 0.0) < -0.5) items.add("细腻度下降");
        if (diff.getOrDefault("symmetry", 0.0) < -0.5) items.add("均匀度下降");
        if (items.isEmpty()) return "暂无明显退步项";
        return String.join("、", items);
    }
}
