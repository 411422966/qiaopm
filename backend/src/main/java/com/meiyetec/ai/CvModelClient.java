package com.meiyetec.ai;

import com.meiyetec.entity.MhVisualArchive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI-006：视觉档案 CV 模型客户端 SPI
 *
 * <p>统一抽象层：同一接口可切换任意后端（腾讯云 / 阿里云 / 本地 CNN），
 * 未配置时自动降级为确定性哈希算法（生产与开发行为一致）。
 *
 * <p>使用方式：
 * <pre>
 * // 单图分析
 * CvAnalysisResult r = cvModelClient.analyze(CvAnalysisRequest.builder()
 *     .archiveType("SKIN")
 *     .imageUrl("https://cdn.example.com/member/face.jpg")
 *     .imageHash("abc123")
 *     .memberId(1001L)
 *     .build());
 *
 * // 两图对比
 * CvComparisonResult r = cvModelClient.compare(CvComparisonRequest.builder()
 *     .beforeUrl("https://cdn.example.com/before.jpg")
 *     .beforeHash("hash1")
 *     .afterUrl("https://cdn.example.com/after.jpg")
 *     .afterHash("hash2")
 *     .build());
 * </pre>
 *
 * <p>路由规则：
 * <ul>
 *   <li>有 imageUrl + 已配置 provider → 真实 HTTP 调用（腾讯云 / 阿里云 / 本地 TorchServe）</li>
 *   <li>无 imageUrl 或 provider 未配置 → 确定性哈希算法（可复现、可缓存）</li>
 *   <li>provider HTTP 调用失败 → 降级确定性算法（不阻塞业务）</li>
 * </ul>
 */
public interface CvModelClient {

    /**
     * 单图特征分析
     *
     * @param req  请求参数
     * @return 标准化分析结果（features / tags / score / inferenceMode）
     */
    CvAnalysisResult analyze(CvAnalysisRequest req);

    /**
     * 两图对比分析
     *
     * @param req  请求参数
     * @return 标准化对比结果（similarity / skinDiff / improvement / regression）
     */
    CvComparisonResult compare(CvComparisonRequest req);

    // ====================================================================
    // Request / Response DTOs
    // ====================================================================

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CvAnalysisRequest {
        /** 档案类型（SKIN / FACE / BODY / HAIR） */
        private String archiveType;
        /** 图像 URL（用于真实模型调用） */
        private String imageUrl;
        /**
         * 图像哈希（用于确定性降级）。
         * 若 imageUrl 非空但 model 未配置，以 imageHash 为种子。
         * 若 imageUrl 也为空，直接以 imageHash 为种子。
         */
        private String imageHash;
        /** 会员 ID（可选，传给 provider 用于 1:N 搜索） */
        private Long memberId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CvAnalysisResult {
        /** 四维特征向量（与 VisualAiService 一致：brightness / saturation / symmetry / texture_score） */
        private Map<String, Double> features;
        /** 自动标签（逗号分隔字符串） */
        private String tags;
        /** 综合分（0-100，保留两位小数） */
        private Double score;
        /** 推理模式：http=真实调用，deterministic=确定性哈希，local=本地模型 */
        private String inferenceMode;
        /** 提供者（TENCENT / ALIYUN / LOCAL / visual-ai-v1.0） */
        private String engine;
        /** 输入的 imageHash（用于调试） */
        private String imageHash;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CvComparisonRequest {
        private String beforeUrl;
        private String beforeHash;
        private String afterUrl;
        private String afterHash;
        private Long memberId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CvComparisonResult {
        /** 余弦相似度（0-1，保留 4 位小数） */
        private Double similarity;
        /** 各维度差异（brightness / saturation / symmetry / texture_score 各维度差值 ×10） */
        private Map<String, Double> skinDiff;
        /** 改善项描述 */
        private String improvement;
        /** 退步项描述 */
        private String regression;
        /** 整体改善分（0-100） */
        private Double diffScore;
        /** 摘要 */
        private String summary;
        /** 推理模式 */
        private String inferenceMode;
        /** 引擎 */
        private String engine;
    }
}
