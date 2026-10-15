package com.meiyetec.ai;

import java.util.Map;

/**
 * M17-03 AI 视觉识别：推理提供者抽象
 *
 * <p>统一的 CV 推理接口，所有真实云服务/本地模型均实现此接口。
 * Spring 按 provider 代码自动注入到 Map<String, CvInferenceProvider>，
 * AIVisionService 根据模型编码查找对应 provider 执行推理。
 *
 * <p>Provider 代码约定：
 * <ul>
 *   <li>{@code TENCENT} → {@link TencentCvProvider}</li>
 *   <li>{@code ALIYUN}  → {@link AliyunCvProvider}</li>
 *   <li>{@code BAIDU}   → {@link BaiduCvProvider}</li>
 *   <li>{@code LOCAL}   → {@link LocalCvProvider}</li>
 * </ul>
 */
public interface CvInferenceProvider {

    /**
     * Provider 唯一代码（TENCENT / ALIYUN / BAIDU / LOCAL）
     */
    String providerCode();

    /**
     * 是否启用（配置了真实凭证时返回 true）
     */
    boolean isConfigured();

    /**
     * 皮肤检测推理
     *
     * @param imageUrl  图像 URL
     * @param memberId  会员 ID（可选）
     * @param config    额外配置（如 region、model version）
     * @return 推理结果（键值对，与 AIVisionService.callCvModel 兼容）
     */
    Map<String, Object> inferSkin(String imageUrl, Long memberId, Map<String, String> config);

    /**
     * 人脸识别推理
     *
     * @param imageUrl  图像 URL
     * @param memberId  会员 ID（用于 1:N 搜索）
     * @param config    额外配置
     * @return 推理结果
     */
    Map<String, Object> inferFace(String imageUrl, Long memberId, Map<String, String> config);

    /**
     * 美甲试戴推理
     *
     * @param handImageUrl   手部图像 URL
     * @param styleImageUrl  美甲款式图像 URL
     * @param config         额外配置
     * @return 推理结果（含 resultImage）
     */
    Map<String, Object> inferNailTryon(String handImageUrl, String styleImageUrl, Map<String, String> config);

    /**
     * 头发分析推理
     */
    Map<String, Object> inferHair(String imageUrl, Long memberId, Map<String, String> config);

    /**
     * 通用图像推理（厂商未覆盖的类型走这里）
     */
    Map<String, Object> inferGeneric(String modelCode, String imageUrl, Map<String, String> config);
}
