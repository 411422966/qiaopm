package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmMemberItemPreference;
import com.meiyetec.mapper.CrmMemberItemPreferenceMapper;
import com.meiyetec.service.ItemPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * M15 项目偏好 Service 实现
 *
 * <p>基于消费历史的显式偏好计算（非 ML 评分）。
 * 偏好分 = 归一化(次数分 × 0.4 + 金额分 × 0.6)
 *
 * <p>自动调用路径（建议）：
 * <ul>
 *   <li>POS 支付成功后 → computeForMember(memberId)</li>
 *   <li>会员画像页面加载时 → 展示 getTopPreferences</li>
 *   <li>推荐接口 → recommendServiceIds</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemPreferenceServiceImpl implements ItemPreferenceService {

    private final CrmMemberItemPreferenceMapper prefMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String ITEM_TYPE_SERVICE = "SERVICE";
    private static final String ITEM_TYPE_PRODUCT = "PRODUCT";
    private static final String ITEM_TYPE_CATEGORY = "CATEGORY";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int computeForMember(Long memberId, int windowDays) {
        if (memberId == null) return 0;
        if (windowDays <= 0) windowDays = 365;

        // 1. 从 pos_order + pos_order_item 聚合服务消费
        String sql = """
            SELECT oi.service_id, oi.service_name, COUNT(*) AS order_count,
                   SUM(oi.final_price) AS total_amount, MAX(o.pay_time) AS last_order_at
            FROM pos_order_item oi
            JOIN pos_order o ON o.id = oi.order_id
            WHERE o.member_id = ? AND o.status = 3 AND o.deleted = 0 AND oi.deleted = 0
              AND oi.service_id IS NOT NULL
              AND o.pay_time > DATE_SUB(NOW(), INTERVAL ? DAY)
            GROUP BY oi.service_id, oi.service_name
            """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, memberId, windowDays);
        } catch (Exception e) {
            log.warn("[ItemPref] 查询失败 memberId={}: {}", memberId, e.getMessage());
            throw new RuntimeException("[ItemPref] 查询失败: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            // 无消费历史 → 将现有偏好记录置 0
            jdbc.update("UPDATE crm_member_item_preference SET preference_score = 0 " +
                    "WHERE member_id = ? AND deleted = 0", memberId);
            return 0;
        }

        // 2. 找最大值用于归一化
        int maxCount = 0;
        long maxAmount = 0;
        for (Map<String, Object> r : rows) {
            int cnt = ((Number) r.get("order_count")).intValue();
            long amt = ((Number) r.get("total_amount")).longValue();
            if (cnt > maxCount) maxCount = cnt;
            if (amt > maxAmount) maxAmount = amt;
        }

        // 3. 计算每个服务项的偏好分并 upsert
        int saved = 0;
        for (Map<String, Object> r : rows) {
            Long itemId = ((Number) r.get("service_id")).longValue();
            String itemName = (String) r.get("service_name");
            int count = ((Number) r.get("order_count")).intValue();
            long amountFen = ((Number) r.get("total_amount")).longValue();
            LocalDateTime lastOrderAt = r.get("last_order_at") == null
                    ? LocalDateTime.now()
                    : ((java.sql.Timestamp) r.get("last_order_at")).toLocalDateTime();

            // 归一化分：次数分(40%) + 金额分(60%)
            double countScore = maxCount > 0 ? (double) count / maxCount * 100 : 0;
            double amountScore = maxAmount > 0 ? (double) amountFen / maxAmount * 100 : 0;
            double rawScore = countScore * 0.4 + amountScore * 0.6;
            int preferenceScore = (int) Math.round(rawScore);

            // 标签推断
            String tags = inferTags(count, amountFen, preferenceScore);

            // 查是否存在
            CrmMemberItemPreference existing = prefMapper.selectOne(
                    new LambdaQueryWrapper<CrmMemberItemPreference>()
                            .eq(CrmMemberItemPreference::getMemberId, memberId)
                            .eq(CrmMemberItemPreference::getItemType, ITEM_TYPE_SERVICE)
                            .eq(CrmMemberItemPreference::getItemId, itemId)
                            .eq(CrmMemberItemPreference::getDeleted, 0)
                            .last("LIMIT 1"));

            if (existing != null) {
                existing.setPreferenceScore(preferenceScore);
                existing.setTotalOrderCount(count);
                existing.setTotalAmountFen(amountFen);
                existing.setLastOrderAt(lastOrderAt);
                existing.setPreferenceTags(tags);
                existing.setCalcWindowDays(windowDays);
                existing.setDeleted(0);
                prefMapper.updateById(existing);
            } else {
                CrmMemberItemPreference p = new CrmMemberItemPreference();
                p.setTenantId(1L); //将由 Controller 层注入正确值
                p.setMemberId(memberId);
                p.setItemType(ITEM_TYPE_SERVICE);
                p.setItemId(itemId);
                p.setItemName(itemName);
                p.setPreferenceScore(preferenceScore);
                p.setTotalOrderCount(count);
                p.setTotalAmountFen(amountFen);
                p.setLastOrderAt(lastOrderAt);
                p.setPreferenceTags(tags);
                p.setCalcWindowDays(windowDays);
                prefMapper.insert(p);
            }
            saved++;
        }
        log.info("[ItemPref] 计算完成: memberId={} 保存 {} 条偏好记录", memberId, saved);
        return saved;
    }

    @Override
    public int batchComputeForMembers(List<Long> memberIds, int windowDays) {
        int success = 0;
        for (Long mid : memberIds) {
            try { if (computeForMember(mid, windowDays) >= 0) success++; } catch (Exception e) {
                log.warn("[ItemPref] batch 失败 mid={}: {}", mid, e.getMessage());
            }
        }
        return success;
    }

    @Override
    public List<CrmMemberItemPreference> getTopPreferences(Long memberId, Long tenantId, int limit) {
        return prefMapper.selectTopByMember(memberId, tenantId, limit);
    }

    @Override
    public List<CrmMemberItemPreference> getPreferencesByType(Long memberId, Long tenantId, String itemType) {
        return prefMapper.selectByMemberAndType(memberId, tenantId, itemType);
    }

    @Override
    public List<Long> recommendServiceIds(Long memberId, Long tenantId, int topN) {
        List<CrmMemberItemPreference> prefs = getTopPreferences(memberId, tenantId, topN);
        return prefs.stream()
                .filter(p -> ITEM_TYPE_SERVICE.equals(p.getItemType()))
                .map(CrmMemberItemPreference::getItemId)
                .toList();
    }

    @Override
    public Map<String, Object> summary(Long memberId, Long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("topServices", getTopPreferences(memberId, tenantId, 5));
        try {
            Integer totalServices = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT item_id) FROM crm_member_item_preference " +
                    "WHERE member_id = ? AND tenant_id = ? AND deleted = 0 AND item_type = 'SERVICE'",
                    Integer.class, memberId, tenantId);
            data.put("uniqueServiceCount", totalServices == null ? 0 : totalServices);

            Double avgScore = jdbc.queryForObject(
                    "SELECT AVG(preference_score) FROM crm_member_item_preference " +
                    "WHERE member_id = ? AND tenant_id = ? AND deleted = 0",
                    Double.class, memberId, tenantId);
            data.put("avgPreferenceScore", avgScore == null ? 0 : Math.round(avgScore * 10.0) / 10.0);
        } catch (Exception e) {
            data.put("uniqueServiceCount", 0);
            data.put("avgPreferenceScore", 0.0);
        }
        return data;
    }

    /** 从统计数据推断偏好标签 */
    private String inferTags(int count, long amountFen, int score) {
        List<String> tags = new ArrayList<>();
        if (count >= 5) tags.add("高频");
        else if (count >= 2) tags.add("复购");
        if (amountFen >= 50000) tags.add("高消费"); // ≥500元
        else if (amountFen >= 20000) tags.add("中消费");
        if (score >= 80) tags.add("强烈偏好");
        else if (score < 30) tags.add("低偏好");
        return String.join(",", tags);
    }
}
