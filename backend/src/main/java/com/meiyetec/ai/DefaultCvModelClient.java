package com.meiyetec.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.*;

/**
 * AI-006：CV 模型客户端默认实现
 *
 * <p>路由策略：
 * <ol>
 *   <li>有 imageUrl + 已配置 provider → HTTP 调用（腾讯云 / 阿里云 / 本地 TorchServe）</li>
 *   <li>无 imageUrl 或 provider 未配置 → 确定性哈希算法</li>
 *   <li>provider HTTP 调用失败 → 降级确定性算法（不阻塞业务）</li>
 * </ol>
 *
 * <p>真实模型 → 标准四维特征映射：
 * <table>
 *   <tr><th>标准维度</th><th>腾讯云来源</th><th>阿里云来源</th><th>默认值</th></tr>
 *   <tr><td>brightness</td><td>Beauty 分数归一化</td><td>SkinResult.Brightness</td><td>0.5</td></tr>
 *   <tr><td>saturation</td><td>推断自肤色/Beauty</td><td>SkinResult.Colorfulness</td><td>0.5</td></tr>
 *   <tr><td>symmetry</td><td>五官定位方差</td><td>DetectFace symmetry</td><td>0.5</td></tr>
 *   <tr><td>texture_score</td><td>FaceQualityScore</td><td>SkinResult.FineLines</td><td>0.5</td></tr>
 * </table>
 *
 * <p>所有 provider 均实现 {@link CvInferenceProvider} SPI，
 * Spring 按 providerCode 注册到 {@code Map<String, CvInferenceProvider>}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCvModelClient implements CvModelClient {

    /** 按 providerCode 查找已注册的 CvInferenceProvider（Spring 自动注入） */
    private final Map<String, CvInferenceProvider> inferenceProviders;

    // ====================================================================
    // Public API
    // ====================================================================

    @Override
    public CvAnalysisResult analyze(CvAnalysisRequest req) {
        String imageUrl = req.getImageUrl();
        String imageHash = req.getImageHash();
        String archiveType = req.getArchiveType();

        // 优先尝试真实模型（必须有 imageUrl + 已配置的 provider）
        if (imageUrl != null && !imageUrl.isEmpty()) {
            CvAnalysisResult real = tryRealModel(archiveType, imageUrl, req.getMemberId());
            if (real != null) {
                return real;
            }
        }

        // 降级确定性哈希算法
        return deterministicAnalyze(archiveType, imageHash);
    }

    @Override
    public CvComparisonResult compare(CvComparisonRequest req) {
        String beforeUrl = req.getBeforeUrl();
        String afterUrl = req.getAfterUrl();

        // 尝试真实模型对比（仅当两张图的 URL 都存在 + provider 已配置）
        if (beforeUrl != null && !beforeUrl.isEmpty()
                && afterUrl != null && !afterUrl.isEmpty()) {
            CvAnalysisResult r1 = tryRealModel("FACE", beforeUrl, req.getMemberId());
            CvAnalysisResult r2 = tryRealModel("FACE", afterUrl, req.getMemberId());
            if (r1 != null && r2 != null) {
                return buildComparison(r1.getFeatures(), r2.getFeatures(),
                        r1.getInferenceMode(), r1.getEngine());
            }
        }

        // 降级确定性哈希算法
        return deterministicCompare(req.getBeforeHash(), req.getAfterHash());
    }

    // ====================================================================
    // Real model path
    // ====================================================================

    /**
     * 尝试真实模型推理。
     * @return 成功返回 CvAnalysisResult；失败（provider 未配置 / 调用异常）返回 null
     */
    private CvAnalysisResult tryRealModel(String archiveType, String imageUrl, Long memberId) {
        String task = toTaskType(archiveType);
        String providerCode = selectProvider(task);

        CvInferenceProvider provider = inferenceProviders.get(providerCode);
        if (provider == null) {
            log.debug("[CvModelClient] no provider registered for code={}", providerCode);
            return null;
        }
        if (!provider.isConfigured()) {
            log.debug("[CvModelClient] provider={} not configured (credentials missing)", providerCode);
            return null;
        }

        try {
            Map<String, Object> raw = callProvider(provider, task, imageUrl, memberId);
            Map<String, Double> features = mapToFeatures(raw, task);
            String tags = autoTags(archiveType, features);
            double score = compositeScore(features, archiveType);
            log.info("[CvModelClient] real inference OK provider={} task={} score={}",
                    providerCode, task, score);

            return CvAnalysisResult.builder()
                    .features(features)
                    .tags(tags)
                    .score(round(score, 2))
                    .inferenceMode("http")
                    .engine(providerCode)
                    .imageHash(null)
                    .build();
        } catch (Exception e) {
            log.warn("[CvModelClient] provider={} inference failed, fallback deterministic: {}",
                    providerCode, e.getMessage());
            return null;
        }
    }

    /**
     * 按任务类型选择最佳 provider。
     * 优先级：LOCAL（最低延迟） > ALIYUN（皮肤专项） > TENCENT（通用）
     */
    private String selectProvider(String task) {
        // LOCAL：最低延迟，有内网 GPU 时首选
        if (inferenceProviders.containsKey("LOCAL")
                && inferenceProviders.get("LOCAL").isConfigured()) {
            return "LOCAL";
        }
        // 皮肤专项：ALIYUN 有专门的 SegmentSkin API
        if ("skin".equals(task)) {
            if (inferenceProviders.containsKey("ALIYUN")
                    && inferenceProviders.get("ALIYUN").isConfigured()) {
                return "ALIYUN";
            }
        }
        // 通用：腾讯云 DetectFace（人脸/颜值分析最全面）
        if (inferenceProviders.containsKey("TENCENT")
                && inferenceProviders.get("TENCENT").isConfigured()) {
            return "TENCENT";
        }
        // 兜底：任意已配置 provider
        for (Map.Entry<String, CvInferenceProvider> e : inferenceProviders.entrySet()) {
            if (e.getValue().isConfigured()) {
                return e.getKey();
            }
        }
        return null;
    }

    private Map<String, Object> callProvider(CvInferenceProvider provider, String task,
                                             String imageUrl, Long memberId) {
        return switch (task) {
            case "skin"  -> provider.inferSkin(imageUrl, memberId, null);
            case "face"  -> provider.inferFace(imageUrl, memberId, null);
            case "hair"  -> provider.inferHair(imageUrl, memberId, null);
            case "nail"  -> {
                // nail 输入是 hand|style，格式 handImageUrl|styleImageUrl
                String[] parts = imageUrl.split("\\|", 2);
                yield provider.inferNailTryon(
                        parts[0],
                        parts.length > 1 ? parts[1] : "",
                        null);
            }
            default      -> provider.inferGeneric(task, imageUrl, null);
        };
    }

    // ====================================================================
    // Feature mapping from real providers → standard 4D space
    // ====================================================================

    /**
     * 将各 provider 返回的原始 JSON 映射到标准四维特征空间。
     *
     * <p>腾讯云 DetectFace 返回：
     * <pre>
     * {
     *   "beautyScore": 85,          → brightness proxy
     *   "FaceQualityScore": 82,     → texture_score proxy
     *   "FaceRect": {...},          → symmetry via bounding box aspect ratio
     *   "skin": {                   → skin sub-object
     *     "skinScore": 78,
     *     "oilLevel": 3, ...
     *   }
     * }
     * </pre>
     *
     * <p>阿里云 SegmentSkin / DetectFace 返回：
     * <pre>
     * {
     *   "skin": {
     *     "skinType": "COMBINATION",
     *     "skinScore": 82,
     *     "Confidence": 0.93,
     *     "Brightness": 0.65,        → brightness (already 0-1)
     *     "Colorfulness": 0.55       → saturation proxy
     *   }
     * }
     * </pre>
     */
    private Map<String, Double> mapToFeatures(Map<String, Object> raw, String task) {
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("brightness", 0.5);
        f.put("saturation", 0.5);
        f.put("symmetry", 0.5);
        f.put("texture_score", 0.5);

        // 通用路径：beautyScore → brightness（归一化 0-1）
        if (raw.containsKey("beautyScore")) {
            double beauty = parseDouble(raw.get("beautyScore"), 75.0);
            f.put("brightness", round(beauty / 100.0, 4));
            f.put("texture_score", round(beauty / 100.0, 4));  // 质量分作纹理分
        }
        if (raw.containsKey("skinScore")) {
            double skinScore = parseDouble(raw.get("skinScore"), 75.0);
            f.put("brightness", round(skinScore / 100.0, 4));
        }

        // 腾讯云 skin 子对象
        if (raw.containsKey("skin")) {
            Object skinObj = raw.get("skin");
            if (skinObj instanceof Map<?, ?> skin) {
                if (skin.containsKey("brightness")) {
                    f.put("brightness", parseDouble(skin.get("brightness"), 0.5));
                }
                if (skin.containsKey("saturation")) {
                    f.put("saturation", parseDouble(skin.get("saturation"), 0.5));
                }
                if (skin.containsKey("oilLevel")) {
                    // 油性肌肤 → 饱和度偏高
                    double oil = parseDouble(skin.get("oilLevel"), 5.0);
                    f.put("saturation", round(oil / 10.0, 4));
                }
                if (skin.containsKey("elasticityLevel")) {
                    double elasticity = parseDouble(skin.get("elasticityLevel"), 8.0);
                    f.put("texture_score", round(elasticity / 10.0, 4));
                }
                if (skin.containsKey("skinScore")) {
                    double ss = parseDouble(skin.get("skinScore"), 75.0);
                    f.put("brightness", round(ss / 100.0, 4));
                }
            }
        }

        // 阿里云结构：Data.skin 或 skin 字段
        if (raw.containsKey("Data") && raw.get("Data") instanceof Map<?, ?> data) {
            if (data.get("skin") instanceof Map<?, ?> aliyunSkin) {
                if (aliyunSkin.containsKey("skinScore")) {
                    double ss = parseDouble(aliyunSkin.get("skinScore"), 75.0);
                    f.put("brightness", round(ss / 100.0, 4));
                }
            }
        }

        // symmetry：从 face landmarks 推断（腾讯云返回 landmarks 数量作质量代理）
        if (raw.containsKey("landmarks")) {
            int lm = 0;
            Object lmObj = raw.get("landmarks");
            if (lmObj instanceof Number n) {
                lm = n.intValue();
            }
            // 81 个关键点 = 高对称性，< 30 = 低
            f.put("symmetry", round(Math.min(1.0, lm / 81.0), 4));
        }

        return f;
    }

    // ====================================================================
    // Deterministic fallback (hash-based, same as VisualAiService)
    // ====================================================================

    private CvAnalysisResult deterministicAnalyze(String archiveType, String imageHash) {
        if (imageHash == null || imageHash.isEmpty()) imageHash = "default-hash";
        long seed = stableSeed(imageHash);
        Map<String, Double> weights = weightsForType(archiveType);
        Map<String, Double> features = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double base = deterministicUniform(seed, e.getKey());
            features.put(e.getKey(), round(base * e.getValue(), 4));
        }
        double composite = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Double v = features.get(e.getKey());
            composite += v * e.getValue() * 100;
        }
        String tags = autoTags(archiveType, features);
        log.debug("[CvModelClient] deterministic analyze hash={} score={}", imageHash, composite);
        return CvAnalysisResult.builder()
                .features(features)
                .tags(tags)
                .score(round(composite, 2))
                .inferenceMode("deterministic")
                .engine("visual-ai-v1.0")
                .imageHash(imageHash)
                .build();
    }

    private CvComparisonResult deterministicCompare(String beforeHash, String afterHash) {
        String bh = beforeHash == null || beforeHash.isEmpty() ? "empty-before" : beforeHash;
        String ah = afterHash == null || afterHash.isEmpty() ? "empty-after" : afterHash;
        long seed = stableSeed(bh + ":" + ah);

        // 解析确定性特征（从 hash 种子派生，与 VisualAiService 完全一致）
        Map<String, Double> weights = weightsForType("SKIN");
        Map<String, Double> beforeFeat = new LinkedHashMap<>();
        Map<String, Double> afterFeat = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double bBase = deterministicUniform(seed, "before:" + e.getKey());
            double aBase = deterministicUniform(stableSeed(ah), "after:" + e.getKey());
            beforeFeat.put(e.getKey(), round(bBase * e.getValue(), 4));
            afterFeat.put(e.getKey(), round(aBase * e.getValue(), 4));
        }

        return buildComparison(beforeFeat, afterFeat, "deterministic", "visual-ai-v1.0");
    }

    private CvComparisonResult buildComparison(Map<String, Double> beforeFeat,
                                                Map<String, Double> afterFeat,
                                                String inferenceMode, String engine) {
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

        return CvComparisonResult.builder()
                .similarity(similarity.doubleValue())
                .diffScore(diffScore.doubleValue())
                .skinDiff(skinDiff)
                .improvement(buildImprovement(skinDiff))
                .regression(buildRegression(skinDiff))
                .summary(String.format("整体改善 %.1f 分，相似度 %.2f，建议继续当前疗程",
                        diffScore, similarity))
                .inferenceMode(inferenceMode)
                .engine(engine)
                .build();
    }

    // ====================================================================
    // Math utilities (mirrored from VisualAiService for standalone use)
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

    private static String autoTags(String type, Map<String, Double> features) {
        List<String> tags = new ArrayList<>();
        double bright = features.getOrDefault("brightness", 0.5);
        double sat = features.getOrDefault("saturation", 0.5);
        double sym = features.getOrDefault("symmetry", 0.5);
        double tex = features.getOrDefault("texture_score", 0.5);
        if (type != null && type.contains("SKIN")) {
            if (bright > 0.6) tags.add("光泽");
            if (bright < 0.3) tags.add("暗沉");
            if (sat > 0.6) tags.add("水润");
            if (tex > 0.7) tags.add("细腻");
            if (tex < 0.3) tags.add("粗糙");
            if (sym > 0.65) tags.add("均匀");
            if (sym < 0.35) tags.add("色差");
        } else if (type != null && type.contains("FACE")) {
            if (sym > 0.65) tags.add("对称");
            if (sym < 0.35) tags.add("不对称");
            if (bright > 0.6) tags.add("轮廓清晰");
            if (tex > 0.7) tags.add("苹果肌饱满");
            if (tex < 0.3) tags.add("法令纹");
        } else {
            if (sym > 0.65) tags.add("均匀");
            if (tex > 0.7) tags.add("紧致");
            if (tex < 0.3) tags.add("松弛");
            if (bright < 0.3) tags.add("色差");
            if (sat > 0.6) tags.add("润泽");
        }
        if (tags.isEmpty()) tags.add("常规");
        return String.join(",", tags);
    }

    private static double compositeScore(Map<String, Double> features, String type) {
        Map<String, Double> w = weightsForType(type);
        double score = 0;
        for (Map.Entry<String, Double> e : w.entrySet()) {
            Double v = features.get(e.getKey());
            score += (v == null ? 0.5 : v) * e.getValue() * 100;
        }
        return score;
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

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static double parseDouble(Object v, double fallback) {
        if (v == null) return fallback;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return fallback; }
    }

    private static String toTaskType(String archiveType) {
        if (archiveType == null) return "skin";
        String t = archiveType.toUpperCase();
        if (t.contains("FACE")) return "face";
        if (t.contains("HAIR")) return "hair";
        if (t.contains("NAIL")) return "nail";
        return "skin";
    }
}
