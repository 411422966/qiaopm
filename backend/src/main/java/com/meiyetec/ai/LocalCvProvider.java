package com.meiyetec.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 本地 CNN 模型推理提供者
 *
 * <p>对接本地部署的图像推理服务（如 TensorFlow Serving / TorchServe / ONNX Runtime）。
 * 低延迟，适合内网场景或隐私敏感数据，无需外传图像。
 *
 * <p>配置（application.yml）：
 * <pre>
 * app:
 *   dcep:
 *     ai:
 *       local:
 *         endpoint: http://192.168.1.100:8081/infer   # 必填；不填或为默认值时视为未配置
 *         timeout-ms: 5000
 * </pre>
 *
 * <p>推理服务接口约定（HTTP POST JSON）：
 * <pre>
 * POST {endpoint}
 * Content-Type: application/json
 *
 * 请求：
 * { "task": "skin|face|nail|hair|generic", "imageUrl": "..." }
 *
 * 响应（任意字段可选，缺失字段走 mock 填充）：
 * {
 *   "brightness": 0.72,
 *   "saturation": 0.55,
 *   "symmetry": 0.81,
 *   "textureScore": 0.68,
 *   "skinScore": 78,
 *   "skinType": "COMBINATION",
 *   "confidence": 0.91
 * }
 * </pre>
 */
@Slf4j
@Component
public class LocalCvProvider implements CvInferenceProvider {

    private static final String DEFAULT_ENDPOINT = "http://localhost:8081/infer";

    @Value("${app.dcep.ai.local.endpoint:#{null}}")
    private String endpoint;

    @Value("${app.dcep.ai.local.timeout-ms:5000}")
    private int timeoutMs;

    private static final String PROVIDER = "LOCAL";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String providerCode() { return PROVIDER; }

    @Override
    public boolean isConfigured() {
        // 配置了非空值且不是默认值 → 已配置
        return endpoint != null
                && !endpoint.isBlank()
                && !DEFAULT_ENDPOINT.equals(endpoint.trim());
    }

    @Override
    public Map<String, Object> inferSkin(String imageUrl, Long memberId, Map<String, String> config) {
        if (!isConfigured()) return mockSkinResult(imageUrl);
        return callLocalModel("skin", imageUrl, memberId, config);
    }

    @Override
    public Map<String, Object> inferFace(String imageUrl, Long memberId, Map<String, String> config) {
        if (!isConfigured()) return mockFaceResult(imageUrl);
        return callLocalModel("face", imageUrl, memberId, config);
    }

    @Override
    public Map<String, Object> inferNailTryon(String handImageUrl, String styleImageUrl, Map<String, String> config) {
        if (!isConfigured()) return mockNailResult(handImageUrl, styleImageUrl);
        return callLocalModel("nail", handImageUrl + "|" + styleImageUrl, null, config);
    }

    @Override
    public Map<String, Object> inferHair(String imageUrl, Long memberId, Map<String, String> config) {
        if (!isConfigured()) return mockHairResult(imageUrl);
        return callLocalModel("hair", imageUrl, memberId, config);
    }

    @Override
    public Map<String, Object> inferGeneric(String modelCode, String imageUrl, Map<String, String> config) {
        if (!isConfigured()) return mockSkinResult(imageUrl);
        return callLocalModel("generic", imageUrl, null, config);
    }

    // ====================================================================
    // 调用本地推理服务 + 响应标准化
    // ====================================================================

    private Map<String, Object> callLocalModel(String task, String input, Long memberId,
                                               Map<String, String> config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task", task);
        body.put("imageUrl", input);
        if (memberId != null) body.put("memberId", memberId);
        if (config != null) config.forEach(body::put);

        long t0 = System.currentTimeMillis();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String bodyJson = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(java.time.Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - t0;

            if (resp.statusCode() == 200) {
                log.info("[LocalCv] task={} -> HTTP 200 latency={}ms", task, elapsed);
                return normalizeLocalResponse(task, input, resp.body(), elapsed);
            } else {
                log.warn("[LocalCv] task={} -> HTTP {} body={}", task, resp.statusCode(), resp.body());
                return mockLocalResult(task, input, "http_" + resp.statusCode());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[LocalCv] task={} timed out after {}ms — {}", task, timeoutMs, e.getMessage());
            return mockLocalResult(task, input, "timeout");
        } catch (Exception e) {
            log.error("[LocalCv] task={} call failed: {}", task, e.getMessage(), e);
            return mockLocalResult(task, input, "exception:" + e.getClass().getSimpleName());
        }
    }

    /**
     * 将本地模型返回的原始 JSON 映射为标准格式。
     * 缺失字段自动用 mock 值填充，保证调用方永远拿到完整数据结构。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeLocalResponse(String task, String input,
                                                     String rawBody, long elapsedMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER);
        result.put("task", task);
        result.put("input", input);
        result.put("inferenceMode", "local_real");
        result.put("latencyMs", elapsedMs);
        result.put("timestamp", System.currentTimeMillis());
        result.put("rawResponse", rawBody);

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var raw = mapper.readValue(rawBody, Map.class);

            // 通用字段（各类任务均可能返回）
            if (raw.containsKey("skinScore"))
                result.put("skinScore", ((Number) raw.get("skinScore")).intValue());
            if (raw.containsKey("confidence"))
                result.put("confidence", ((Number) raw.get("confidence")).doubleValue());
            if (raw.containsKey("skinType"))
                result.put("skinType", String.valueOf(raw.get("skinType")));

            switch (task) {
                case "skin" -> {
                    Map<String, Object> skin = new LinkedHashMap<>();
                    skin.put("skinType",     raw.getOrDefault("skinType", "COMBINATION"));
                    skin.put("confidence",   raw.getOrDefault("confidence", 0.91));
                    skin.put("brightness",    raw.getOrDefault("brightness", 0.7));
                    skin.put("saturation",    raw.getOrDefault("saturation", 0.55));
                    skin.put("textureScore",  raw.getOrDefault("textureScore", 0.68));
                    skin.put("skinScore",     raw.getOrDefault("skinScore", 80));
                    result.put("skin", skin);
                }
                case "face" -> {
                    result.put("beautyScore", raw.getOrDefault("beautyScore", 85));
                    result.put("ageRange",     raw.getOrDefault("ageRange", "25-30"));
                    result.put("gender",       raw.getOrDefault("gender", "FEMALE"));
                    result.put("symmetry",     raw.getOrDefault("symmetry", 0.81));
                    result.put("confidence",   raw.getOrDefault("confidence", 0.97));
                    result.put("landmarks",    raw.getOrDefault("landmarks", 81));
                }
                case "nail" -> {
                    result.put("resultImage",  raw.getOrDefault("resultImage",
                            "https://cdn.example.com/local/tryon/" + System.currentTimeMillis() + ".jpg"));
                    result.put("nailDetected", raw.getOrDefault("nailDetected", true));
                    result.put("colorMatched", raw.getOrDefault("colorMatched", true));
                }
                case "hair" -> {
                    result.put("hairDensity",   raw.getOrDefault("hairDensity", 7));
                    result.put("hairLossLevel", raw.getOrDefault("hairLossLevel", 3));
                    result.put("scalpOiliness", raw.getOrDefault("scalpOiliness", 4));
                    result.put("dandruffLevel", raw.getOrDefault("dandruffLevel", 2));
                    result.put("confidence",    raw.getOrDefault("confidence", 0.91));
                }
                default -> {
                    // generic / fallback：直接 merge 原始字段
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedRaw = raw;
                    typedRaw.forEach((k, v) -> result.put(k, v));
                }
            }

            log.info("[LocalCv] normalized response task={} skinScore={} confidence={}",
                    task, result.get("skinScore"), result.get("confidence"));

        } catch (Exception e) {
            log.warn("[LocalCv] normalize failed, using raw mock: {}", e.getMessage());
            return mockLocalResult(task, input, "parse_error");
        }

        return result;
    }

    private Map<String, Object> mockLocalResult(String task, String input, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("task", task);
        result.put("input", input);
        result.put("confidence", 0.91);
        result.put("inferenceMode", "local_mock");
        result.put("fallbackReason", reason);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    // ====================================================================
    // Mock 响应（降级时返回）
    // ====================================================================

    private Map<String, Object> mockSkinResult(String imageUrl) {
        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("skinType", "COMBINATION");
        skin.put("oilLevel", 5);
        skin.put("moistureLevel", 6);
        skin.put("elasticityLevel", 8);
        skin.put("pigmentLevel", 4);
        skin.put("poreLevel", 5);
        skin.put("wrinkleLevel", 2);
        skin.put("skinScore", 82);
        skin.put("issues", List.of("轻微油光"));
        skin.put("confidence", 0.91);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("skin", skin);
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "local_not_configured");
        return result;
    }

    private Map<String, Object> mockFaceResult(String imageUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("beautyScore", 88);
        result.put("ageRange", "26-31");
        result.put("gender", "FEMALE");
        result.put("expression", "smile");
        result.put("confidence", 0.97);
        result.put("landmarks", 81);
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "local_not_configured");
        return result;
    }

    private Map<String, Object> mockNailResult(String hand, String style) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("resultImage", "https://cdn.example.com/local/tryon/" + System.currentTimeMillis() + ".jpg");
        result.put("nailDetected", true);
        result.put("colorMatched", true);
        result.put("handImageUrl", hand);
        result.put("styleImageUrl", style);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "local_not_configured");
        return result;
    }

    private Map<String, Object> mockHairResult(String imageUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("hairDensity", 8);
        result.put("hairLossLevel", 2);
        result.put("scalpOiliness", 5);
        result.put("dandruffLevel", 1);
        result.put("recommendations", List.of("控油护理", "增加营养"));
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "local_not_configured");
        return result;
    }
}
