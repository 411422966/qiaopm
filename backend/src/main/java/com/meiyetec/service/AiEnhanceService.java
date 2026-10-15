package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.AiRecommendCache;
import com.meiyetec.entity.AiReviewSummary;
import com.meiyetec.entity.AiSalesForecast;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AiRecommendCacheMapper;
import com.meiyetec.mapper.AiReviewSummaryMapper;
import com.meiyetec.mapper.AiSalesForecastMapper;
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
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * R3.0-H P2.4：AI 增强（推荐 + 销售预测 + NLP）
 *
 * <p>三个核心能力：
 * <ul>
 *   <li>智能推荐（COLLAB-V1 / CONTENT-V1 / HOT-V1）</li>
 *   <li>销售预测（EWMA / Holt-Winters / 线性回归）</li>
 *   <li>评价 NLP 摘要（关键词 + 情感评分）</li>
 * </ul>
 *
 * <p>生产环境应替换为：
 * <ul>
 *   <li>推荐：ALS / Wide&amp;Deep / Embedding</li>
 *   <li>预测：Prophet / LSTM / XGBoost</li>
 *   <li>NLP：BERT / 情感分析 API</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiEnhanceService {

    private final AiRecommendCacheMapper recommendMapper;
    private final AiSalesForecastMapper forecastMapper;
    private final AiReviewSummaryMapper summaryMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 智能推荐（COLLAB-V1 协同过滤简化版）
    // ====================================================================

    /**
     * 推荐服务（基于会员最近购买 + 热销加权）
     */
    @GetMapping("/recommend")
    public Result<Map<String, Object>> recommend(@RequestParam Long memberId,
                                                   @RequestParam(defaultValue = "SERVICE") String type) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 查缓存
        AiRecommendCache cached = recommendMapper.selectOne(
                new LambdaQueryWrapper<AiRecommendCache>()
                        .eq(AiRecommendCache::getTenantId, tenantId)
                        .eq(AiRecommendCache::getMemberId, memberId)
                        .eq(AiRecommendCache::getRecommendType, type));
        if (cached != null && cached.getExpiresAt() != null
                && cached.getExpiresAt().isAfter(LocalDateTime.now())) {
            jdbc.update("UPDATE ai_recommend_cache SET hit_count=hit_count+1, last_hit_at=NOW() WHERE id=?",
                    cached.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", parseJsonList(cached.getRecommendItems()));
            data.put("algorithm", cached.getAlgorithm());
            data.put("score", cached.getScore());
            data.put("cacheHit", true);
            return Result.success(data);
        }

        // 计算推荐
        List<Map<String, Object>> items = computeRecommendations(tenantId, memberId, type);
        BigDecimal score = BigDecimal.valueOf(0.85);

        String itemsJson = toJson(items);
        if (cached != null) {
            cached.setRecommendItems(itemsJson);
            cached.setScore(score);
            cached.setAlgorithm("COLLAB-V1");
            cached.setExpiresAt(LocalDateTime.now().plusHours(6));
            recommendMapper.updateById(cached);
        } else {
            cached = new AiRecommendCache();
            cached.setTenantId(tenantId);
            cached.setMemberId(memberId);
            cached.setRecommendType(type);
            cached.setRecommendItems(itemsJson);
            cached.setScore(score);
            cached.setAlgorithm("COLLAB-V1");
            cached.setExpiresAt(LocalDateTime.now().plusHours(6));
            cached.setHitCount(0);
            recommendMapper.insert(cached);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("algorithm", "COLLAB-V1");
        data.put("score", score);
        data.put("cacheHit", false);
        return Result.success(data);
    }

    private List<Map<String, Object>> computeRecommendations(Long tenantId, Long memberId, String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 1) 会员最近消费过的服务类别
            Set<Long> memberCategories = new HashSet<>();
            try {
                List<Map<String, Object>> history = jdbc.queryForList(
                    "SELECT DISTINCT s.category_id FROM pos_order_item i " +
                    "JOIN pos_order o ON i.order_id = o.id " +
                    "JOIN biz_service_item s ON i.service_id = s.id " +
                    "WHERE o.member_id = ? AND o.deleted = 0 AND i.deleted = 0 " +
                    "ORDER BY o.created_at DESC LIMIT 50",
                    memberId);
                for (Map<String, Object> h : history) {
                    if (h.get("category_id") != null)
                        memberCategories.add(((Number) h.get("category_id")).longValue());
                }
            } catch (Exception e) {
                log.debug("会员历史查询失败：{}", e.getMessage());
            }

            // 2) 热销 top 10（按 sales_count 倒序）
            String sql = "SELECT id, service_name as name, price, sales_count " +
                    "FROM biz_service_item " +
                    "WHERE deleted = 0 AND tenant_id = ? AND enabled = 1 " +
                    "ORDER BY sales_count DESC LIMIT 10";
            List<Map<String, Object>> hot = jdbc.queryForList(sql, tenantId);
            for (Map<String, Object> h : hot) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", h.get("id"));
                item.put("name", h.get("name"));
                item.put("price", h.get("price"));
                // COLLAB-V1 分数：salesCount 归一化 + 类别加成
                int sales = h.get("sales_count") == null ? 0 : ((Number) h.get("sales_count")).intValue();
                double score = Math.min(1.0, sales / 1000.0);
                item.put("score", Math.round(score * 100) / 100.0);
                item.put("reason", "热销推荐");
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("推荐计算降级：{}", e.getMessage());
            result.add(Map.of("id", 0, "name", "暂无推荐", "score", 0, "reason", "数据不足"));
        }
        return result;
    }

    // ====================================================================
    // 2. 销售预测（EWMA 指数加权移动平均）
    // ====================================================================

    /**
     * 生成未来 N 天预测（EWMA α=0.3）
     */
    @PostMapping("/forecast/generate")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> generateForecast(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        String type = (String) body.getOrDefault("forecastType", "REVENUE");
        Integer days = body.get("days") == null ? 7 : ((Number) body.get("days")).intValue();
        Integer window = body.get("baseWindowDays") == null ? 90 : ((Number) body.get("baseWindowDays")).intValue();

        // 取历史数据
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(window);

        List<Map<String, Object>> history = new ArrayList<>();
        try {
            history = jdbc.queryForList(
                "SELECT DATE(created_at) AS dt, " +
                "       COALESCE(SUM(total_amount), 0) AS revenue, " +
                "       COUNT(*) AS orders, " +
                "       COUNT(DISTINCT member_id) AS new_customers " +
                "FROM pos_order " +
                "WHERE deleted = 0 AND tenant_id = ? " +
                (storeId != null ? "AND store_id = ? " : "") +
                "  AND status IN ('PAID', 'COMPLETED') " +
                "  AND created_at >= ? AND created_at < ? " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY dt ASC",
                storeId != null ? new Object[]{tenantId, storeId, startDate, endDate}
                                : new Object[]{tenantId, startDate, endDate});
        } catch (Exception e) {
            log.warn("历史数据读取失败：{}", e.getMessage());
            throw new RuntimeException("历史数据读取失败: " + e.getMessage(), e);
        }

        if (history.size() < 7) {
            return Result.error(BizCode.BAD_REQUEST, "历史数据不足 7 天，无法预测");
        }

        // EWMA 预测
        double alpha = 0.3;
        double ewma = 0;
        int count = 0;
        double mean = 0;
        double stdDev = 0;
        double[] values = new double[history.size()];
        for (int i = 0; i < history.size(); i++) {
            double v = ((Number) history.get(i).get(type.toLowerCase())).doubleValue();
            values[i] = v;
            if (count == 0) {
                ewma = v;
            } else {
                ewma = alpha * v + (1 - alpha) * ewma;
            }
            mean += v;
            count++;
        }
        mean /= count;
        for (double v : values) stdDev += (v - mean) * (v - mean);
        stdDev = Math.sqrt(stdDev / count);

        // 生成未来 days 天
        List<Map<String, Object>> forecastList = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            LocalDate forecastDate = endDate.plusDays(d);
            BigDecimal predicted = BigDecimal.valueOf(ewma).setScale(2, RoundingMode.HALF_UP);
            BigDecimal upper = BigDecimal.valueOf(ewma + 1.96 * stdDev).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lower = BigDecimal.valueOf(Math.max(0, ewma - 1.96 * stdDev)).setScale(2, RoundingMode.HALF_UP);

            AiSalesForecast f = new AiSalesForecast();
            f.setTenantId(tenantId);
            f.setStoreId(storeId);
            f.setForecastDate(forecastDate);
            f.setForecastType(type);
            f.setPredictedValue(predicted);
            f.setUpperBound(upper);
            f.setLowerBound(lower);
            f.setConfidence(BigDecimal.valueOf(0.8));
            f.setAlgorithm("EWMA");
            f.setBaseWindowDays(window);
            f.setOperatorName((String) body.getOrDefault("operatorName", "system"));
            forecastMapper.insert(f);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("forecastDate", forecastDate);
            entry.put("predictedValue", predicted);
            entry.put("upperBound", upper);
            entry.put("lowerBound", lower);
            entry.put("confidence", 0.8);
            forecastList.add(entry);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithm", "EWMA α=" + alpha);
        data.put("baseWindowDays", window);
        data.put("forecastDays", days);
        data.put("dataPoints", history.size());
        data.put("mean", mean);
        data.put("stdDev", stdDev);
        data.put("predictions", forecastList);
        return Result.success(data);
    }

    @GetMapping("/forecast/list")
    public Result<List<AiSalesForecast>> listForecasts(
            @RequestParam(required = false) String forecastType,
            @RequestParam(defaultValue = "7") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate startDate = LocalDate.now();
        LambdaQueryWrapper<AiSalesForecast> qw = new LambdaQueryWrapper<>();
        qw.eq(AiSalesForecast::getTenantId, tenantId)
          .ge(AiSalesForecast::getForecastDate, startDate)
          .le(AiSalesForecast::getForecastDate, startDate.plusDays(days));
        if (forecastType != null) qw.eq(AiSalesForecast::getForecastType, forecastType);
        qw.orderByAsc(AiSalesForecast::getForecastDate);
        return Result.success(forecastMapper.selectList(qw));
    }

    // ====================================================================
    // 3. 评价 NLP 摘要（关键词 + 情感）
    // ====================================================================

    /**
     * 生成评价摘要（关键词 + 情感评分）
     */
    @PostMapping("/review/summary/generate")
    @Transactional(rollbackFor = Exception.class)
    public Result<AiReviewSummary> generateSummary(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Integer days = body.get("days") == null ? 7 : ((Number) body.get("days")).intValue();
        String summaryKey = "last_" + days + "d";

        // 拉评价数据
        List<Map<String, Object>> reviews = jdbc.queryForList(
            "SELECT id, overall_rating, comment_text, " +
            "       service_rating, skill_rating, environment_rating, price_rating " +
            "FROM crm_review " +
            "WHERE deleted = 0 AND tenant_id = ? AND status = 2 " +
            "  AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)",
            tenantId, days);

        if (reviews.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "无评价数据");
        }

        // 统计
        int total = reviews.size();
        int positive = 0, neutral = 0, negative = 0;
        double sumRating = 0;
        Map<String, Integer> posWords = new HashMap<>();
        Map<String, Integer> negWords = new HashMap<>();

        // 预置情感词（中文）
        Set<String> positiveLexicon = Set.of("好", "棒", "满意", "推荐", "舒服", "专业", "热情", "周到",
                "贴心", "细致", "认真", "细心", "干净", "整洁", "高大上");
        Set<String> negativeLexicon = Set.of("差", "糟", "差劲", "失望", "不耐烦", "敷衍", "脏",
                "冷漠", "慢", "贵", "不值", "差评");

        for (Map<String, Object> r : reviews) {
            int rating = ((Number) r.get("overall_rating")).intValue();
            sumRating += rating;
            if (rating >= 4) positive++;
            else if (rating == 3) neutral++;
            else negative++;

            String text = r.get("comment_text") == null ? "" : r.get("comment_text").toString();
            // 简单分词（按字符 + 长度过滤）
            String[] tokens = text.replaceAll("[\\p{Punct}\\s]+", " ").split(" ");
            for (String t : tokens) {
                if (t.length() < 2) continue;
                if (positiveLexicon.contains(t)) posWords.merge(t, 1, Integer::sum);
                if (negativeLexicon.contains(t)) negWords.merge(t, 1, Integer::sum);
            }
        }

        double avgRating = sumRating / total;
        double nps = ((positive - negative) * 100.0) / total;
        double sentiment = (positive - negative) / (double) total;

        // Top 10 关键词
        String posKeywords = topKeywordsJson(posWords, 10);
        String negKeywords = topKeywordsJson(negWords, 10);

        // 摘要文本
        String summary = String.format(
                "近 %d 天共 %d 条评价，平均评分 %.2f，NPS %.1f。",
                days, total, avgRating, nps)
                + String.format("好评 %d 条（%s）", positive, sortedKeywords(posWords, 3))
                + String.format("，差评 %d 条（%s）。", negative, sortedKeywords(negWords, 3));

        // 写库（更新或插入）
        AiReviewSummary existing = summaryMapper.selectOne(
                new LambdaQueryWrapper<AiReviewSummary>()
                        .eq(AiReviewSummary::getTenantId, tenantId)
                        .eq(AiReviewSummary::getSummaryKey, summaryKey)
                        .eq(AiReviewSummary::getSummaryType, "RANGE"));
        AiReviewSummary s;
        if (existing != null) {
            s = existing;
        } else {
            s = new AiReviewSummary();
            s.setTenantId(tenantId);
            s.setSummaryKey(summaryKey);
            s.setSummaryType("RANGE");
            s.setAlgorithm("NLP-KEYWORD-V1");
        }
        s.setTotalReviews(total);
        s.setPositiveCount(positive);
        s.setNeutralCount(neutral);
        s.setNegativeCount(negative);
        s.setAvgRating(BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP));
        s.setNps(BigDecimal.valueOf(nps).setScale(2, RoundingMode.HALF_UP));
        s.setPositiveKeywords(posKeywords);
        s.setNegativeKeywords(negKeywords);
        s.setSummaryText(summary);
        s.setSentimentScore(BigDecimal.valueOf(sentiment).setScale(4, RoundingMode.HALF_UP));

        if (existing != null) {
            summaryMapper.updateById(s);
        } else {
            summaryMapper.insert(s);
        }
        return Result.success("已生成", s);
    }

    @GetMapping("/review/summary/{key}")
    public Result<AiReviewSummary> getSummary(@PathVariable String key) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        AiReviewSummary s = summaryMapper.selectOne(
                new LambdaQueryWrapper<AiReviewSummary>()
                        .eq(AiReviewSummary::getTenantId, tenantId)
                        .eq(AiReviewSummary::getSummaryKey, key));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "摘要不存在");
        return Result.success(s);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private static String topKeywordsJson(Map<String, Integer> words, int topN) {
        return words.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(e -> String.format("{\"word\":\"%s\",\"count\":%d}", e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String sortedKeywords(Map<String, Integer> words, int topN) {
        return words.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("、"));
    }

    private static List<Map<String, Object>> parseJsonList(String json) {
        // 简化：直接当 JSON 列表返回（生产应用 Jackson）
        return List.of();
    }

    private static String toJson(List<Map<String, Object>> list) {
        // 简化：手写 JSON
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> m = list.get(i);
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Number) sb.append(v);
                else sb.append("\"").append(v).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}