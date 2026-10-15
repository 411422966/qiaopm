package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P3 · T-P01：RFM 标签模型与会员分群
 *  - RFM 实时计算（按会员）
 *  - RFM 全量批量计算
 *  - 标签持久化到 crm_member_tag_value
 *  - 8 个标准分群（CHAMPION / LOYAL / POTENTIAL / NEW / AT_RISK / LOST / CANT_LOSE / HIBERNATING）
 */
@Service
@RequiredArgsConstructor
public class RfmService {

    private final CrmRfmSnapshotMapper rfmSnapshotMapper;
    private final CrmMemberTagValueMapper tagValueMapper;
    private final CrmTagDictionaryMapper tagDictMapper;
    private final MktSegmentMapper segmentMapper;
    private final AiTagModelMapper modelMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // RFM 分群阈值（可由 model.rule_json 覆盖）
    private static final int[][] THRESHOLDS = {
            {30, 90, 180, 365},    // recency 天数
            {3, 5, 8, 15},         // frequency 次数
            {500, 1500, 5000, 15000} // monetary 元
    };

    // ====================================================================
    // 单会员 RFM 计算
    // ====================================================================

    public Result<Map<String, Object>> computeForMember(@PathVariable Long memberId) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 拉会员
        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, tenant_id, brand_id FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // 拉消费记录
        Map<String, Object> stats = jdbc.queryForMap(
            "SELECT " +
            "  COALESCE(DATEDIFF(NOW(), MAX(pay_time)), 9999) AS recency, " +
            "  COUNT(*) AS frequency, " +
            "  COALESCE(SUM(pay_amount), 0) AS monetary " +
            "FROM crm_order WHERE member_id = ? AND status = 3 AND deleted = 0", memberId);

        int recency = ((Number) stats.getOrDefault("recency", 9999)).intValue();
        int frequency = ((Number) stats.getOrDefault("frequency", 0)).intValue();
        BigDecimal monetary = new BigDecimal(stats.getOrDefault("monetary", 0).toString());

        // 三维度评分（1-5）
        int rScore = scoreR(recency);
        int fScore = scoreF(frequency);
        int mScore = scoreM(monetary);
        String rfmScore = "" + rScore + fScore + mScore;

        // 分群 + 解释
        String[] seg = classify(rScore, fScore, mScore, recency);

        // 落库快照
        CrmRfmSnapshot snap = new CrmRfmSnapshot();
        snap.setTenantId(tenantId);
        snap.setBrandId(member.get("brand_id") == null ? null : ((Number) member.get("brand_id")).longValue());
        snap.setMemberId(memberId);
        snap.setRecencyDays(recency);
        snap.setFrequency(frequency);
        snap.setMonetary(monetary);
        snap.setRScore(rScore);
        snap.setFScore(fScore);
        snap.setMScore(mScore);
        snap.setRfmScore(rfmScore);
        snap.setSegment(seg[0]);
        snap.setSegmentName(seg[1]);
        snap.setExplanation(seg[2]);
        snap.setSnapshotDate(LocalDate.now());
        snap.setCalcMode("ONLINE");
        snap.setCalcDurationMs((int) (System.currentTimeMillis() - t0));
        snap.setOperatorName("online");
        rfmSnapshotMapper.insert(snap);

        // 写标签
        upsertTag(memberId, tenantId, snap.getBrandId(),
                "RFM-" + seg[0], seg[1], "RFM",
                snap.getRfmScore(),
                BigDecimal.valueOf((rScore + fScore + mScore) * 100 / 15.0).setScale(2, RoundingMode.HALF_UP),
                "AUTO", null, seg[2]);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("recencyDays", recency);
        data.put("frequency", frequency);
        data.put("monetary", monetary);
        data.put("rScore", rScore);
        data.put("fScore", fScore);
        data.put("mScore", mScore);
        data.put("rfmScore", rfmScore);
        data.put("segment", seg[0]);
        data.put("segmentName", seg[1]);
        data.put("explanation", seg[2]);
        data.put("durationMs", snap.getCalcDurationMs());
        return Result.success(data);
    }

    /**
     * 批量 RFM 计算（生产环境通常 @Scheduled 定时调度）
     */
    public Result<Map<String, Object>> batchCompute(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long brandId = body == null ? null : body.get("brandId") == null ? null
                : ((Number) body.get("brandId")).longValue();
        int limit = body == null || body.get("limit") == null ? 5000
                : ((Number) body.get("limit")).intValue();

        // 拉会员
        StringBuilder sql = new StringBuilder(
            "SELECT id, brand_id FROM crm_member WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (brandId != null) { sql.append("AND brand_id = ? "); args.add(brandId); }
        sql.append("LIMIT ?");
        args.add(limit);

        List<Map<String, Object>> members = jdbc.queryForList(sql.toString(), args.toArray());

        int success = 0, fail = 0;
        for (Map<String, Object> m : members) {
            try {
                computeForMember((Long) m.get("id"));
                success++;
            } catch (Exception e) {
                fail++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", members.size());
        data.put("success", success);
        data.put("fail", fail);
        data.put("durationMs", (int) (System.currentTimeMillis() - t0));
        return Result.success(data);
    }

    // ====================================================================
    // 标签 CRUD
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<CrmMemberTagValue> upsertTagEndpoint(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String tagCode = (String) body.get("tagCode");
        String tagName = (String) body.get("tagName");
        String category = (String) body.getOrDefault("tagCategory", "RFM");
        if (memberId == null || tagCode == null) return Result.error(BizCode.BAD_REQUEST, "memberId / tagCode 必填");

        // 从字典查 tagName
        if (tagName == null) {
            CrmTagDictionary dict = tagDictMapper.selectOne(new LambdaQueryWrapper<CrmTagDictionary>()
                    .eq(CrmTagDictionary::getTagCode, tagCode));
            if (dict != null) {
                tagName = dict.getTagName();
            } else {
                tagName = tagCode;
            }
        }

        Long tenantId = TenantContextHolder.get().getTenantId();
        Long brandId = body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue();
        BigDecimal score = body.get("score") == null ? null
                : new BigDecimal(body.get("score").toString());
        String sourceType = (String) body.getOrDefault("sourceType", "MANUAL");

        Long tagId = upsertTag(memberId, tenantId, brandId,
                tagCode, tagName, category, (String) body.get("tagValue"),
                score, sourceType,
                body.get("modelId") == null ? null : ((Number) body.get("modelId")).longValue(),
                (String) body.get("remark"));

        CrmMemberTagValue tag = tagValueMapper.selectById(tagId);
        return Result.success("已更新", tag);
    }

    @Transactional(rollbackFor = Exception.class)
    Long upsertTag(Long memberId, Long tenantId, Long brandId,
                    String tagCode, String tagName, String category,
                    String tagValue, BigDecimal score,
                    String sourceType, Long modelId, String explanation) {
        // 查字典补全
        if (explanation == null) {
            CrmTagDictionary dict = tagDictMapper.selectOne(new LambdaQueryWrapper<CrmTagDictionary>()
                    .eq(CrmTagDictionary::getTagCode, tagCode));
            if (dict != null) {
                explanation = dict.getDescription();
            }
        }
        CrmMemberTagValue exist = tagValueMapper.selectOne(new LambdaQueryWrapper<CrmMemberTagValue>()
                .eq(CrmMemberTagValue::getTenantId, tenantId)
                .eq(CrmMemberTagValue::getMemberId, memberId)
                .eq(CrmMemberTagValue::getTagCode, tagCode));
        if (exist != null) {
            exist.setTagName(tagName);
            exist.setTagCategory(category);
            exist.setTagValue(tagValue);
            exist.setScore(score);
            exist.setEffectiveAt(LocalDateTime.now());
            tagValueMapper.updateById(exist);
            return exist.getId();
        }
        CrmMemberTagValue tag = new CrmMemberTagValue();
        tag.setTenantId(tenantId);
        tag.setBrandId(brandId);
        tag.setMemberId(memberId);
        tag.setTagCode(tagCode);
        tag.setTagName(tagName);
        tag.setTagCategory(category);
        tag.setSourceType(sourceType);
        tag.setModelId(modelId);
        tag.setTagValue(tagValue);
        tag.setScore(score);
        tag.setEffectiveAt(LocalDateTime.now());
        tag.setOperatorName(sourceType);
        tagValueMapper.insert(tag);
        return tag.getId();
    }

    public Result<Void> deleteTag(@PathVariable Long id) {
        tagValueMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    public Result<List<Map<String, Object>>> memberTags(@PathVariable Long memberId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT t.id, t.tag_code, t.tag_name, t.tag_category, t.tag_value, t.score, " +
            "       t.source_type, t.effective_at, t.expire_at, " +
            "       d.description, d.rule " +
            "FROM crm_member_tag_value t " +
            "LEFT JOIN crm_tag_dictionary d ON d.tag_code = t.tag_code " +
            "WHERE t.deleted = 0 AND t.member_id = ? " +
            "ORDER BY t.score DESC", memberId);
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // 标签字典
    // ====================================================================

    public Result<List<Map<String, Object>>> dictList(@RequestParam(required = false) String category) {
        String sql = "SELECT id, tag_code, tag_name, category, description, rule, color, sort_order " +
                "FROM crm_tag_dictionary WHERE enabled = 1 " +
                (category != null && !category.isEmpty() ? "AND category = ? " : "") +
                "ORDER BY category, sort_order";
        List<Map<String, Object>> rows = category != null && !category.isEmpty()
                ? jdbc.queryForList(sql, category)
                : jdbc.queryForList(sql);
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // 人群（按 RFM 分群聚合）
    // ====================================================================

    public Result<List<Map<String, Object>>> segmentOverview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 取最新一次快照的分群汇总
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT segment, segment_name, " +
            "       COUNT(*) AS member_count, " +
            "       ROUND(AVG(frequency), 1) AS avg_freq, " +
            "       ROUND(AVG(monetary), 2) AS avg_amount, " +
            "       ROUND(AVG(recency_days), 1) AS avg_recency " +
            "FROM crm_rfm_snapshot " +
            "WHERE tenant_id = ? AND snapshot_date = (SELECT MAX(snapshot_date) FROM crm_rfm_snapshot WHERE tenant_id = ?) " +
            "GROUP BY segment, segment_name " +
            "ORDER BY member_count DESC", tenantId, tenantId);
        return Result.success(Keys.camel(rows));
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> segmentMembers(
            @PathVariable String segment,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String sql = "SELECT s.member_id, m.member_no, m.name, m.phone, m.level_id, " +
                "       s.segment, s.segment_name, s.recency_days, s.frequency, s.monetary, " +
                "       s.rfm_score, s.explanation " +
                "FROM crm_rfm_snapshot s " +
                "JOIN crm_member m ON m.id = s.member_id " +
                "WHERE s.tenant_id = ? AND s.segment = ? " +
                "AND s.snapshot_date = (SELECT MAX(snapshot_date) FROM crm_rfm_snapshot WHERE tenant_id = ? AND segment = ?) " +
                "ORDER BY s.monetary DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>();
        args.add(tenantId); args.add(segment);
        args.add(tenantId); args.add(segment);
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> records = jdbc.queryForList(sql, args.toArray());
        String csql = "SELECT COUNT(*) FROM crm_rfm_snapshot s " +
                "WHERE s.tenant_id = ? AND s.segment = ? " +
                "AND s.snapshot_date = (SELECT MAX(snapshot_date) FROM crm_rfm_snapshot WHERE tenant_id = ? AND segment = ?)";
        Long total = jdbc.queryForObject(csql, Long.class, tenantId, segment, tenantId, segment);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<MktSegment> createSegment(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String code = (String) body.get("segmentCode");
        String name = (String) body.get("segmentName");
        if (code == null || name == null) return Result.error(BizCode.BAD_REQUEST, "编码 / 名称 必填");

        Long dup = segmentMapper.selectCount(new LambdaQueryWrapper<MktSegment>()
                .eq(MktSegment::getTenantId, tenantId)
                .eq(MktSegment::getSegmentCode, code));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "编码已存在");

        MktSegment seg = new MktSegment();
        seg.setTenantId(tenantId);
        seg.setBrandId(body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue());
        seg.setSegmentCode(code);
        seg.setSegmentName(name);
        seg.setDescription((String) body.get("description"));
        seg.setFilterJson((String) body.get("filterJson"));
        seg.setMemberCount(body.get("memberCount") == null ? 0 : ((Number) body.get("memberCount")).intValue());
        seg.setSourceType((String) body.getOrDefault("sourceType", "RULE"));
        seg.setSourceModelId(body.get("sourceModelId") == null ? null : ((Number) body.get("sourceModelId")).longValue());
        seg.setEnabled(body.get("enabled") == null ? 1 : ((Number) body.get("enabled")).intValue());
        seg.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        segmentMapper.insert(seg);
        return Result.success("已创建", seg);
    }

    public Result<List<Map<String, Object>>> segmentList() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, segment_code, segment_name, description, member_count, " +
            "       source_type, enabled, operator_name, create_time " +
            "FROM mkt_segment WHERE deleted = 0 AND tenant_id = ? " +
            "ORDER BY create_time DESC", tenantId);
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // 模型管理
    // ====================================================================

    public Result<List<Map<String, Object>>> modelList() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, model_code, model_name, model_type, schedule_cron, enabled, status, " +
            "       description, auc, recall, precision_score, operator_name, create_time " +
            "FROM ai_tag_model WHERE deleted = 0 " +
            "AND (tenant_id = ? OR tenant_id IS NULL) ORDER BY model_type, model_code",
            tenantId);
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // RFM 评分工具（5 分制，5=最佳）
    // ====================================================================
    private int scoreR(int recency) {
        if (recency <= THRESHOLDS[0][0]) return 5;
        if (recency <= THRESHOLDS[0][1]) return 4;
        if (recency <= THRESHOLDS[0][2]) return 3;
        if (recency <= THRESHOLDS[0][3]) return 2;
        return 1;
    }

    private int scoreF(int frequency) {
        if (frequency >= THRESHOLDS[1][3]) return 5;
        if (frequency >= THRESHOLDS[1][2]) return 4;
        if (frequency >= THRESHOLDS[1][1]) return 3;
        if (frequency >= THRESHOLDS[1][0]) return 2;
        return 1;
    }

    private int scoreM(BigDecimal monetary) {
        double m = monetary.doubleValue();
        double[] th = new double[THRESHOLDS[2].length];
        for (int i = 0; i < th.length; i++) th[i] = THRESHOLDS[2][i];
        if (m >= th[3]) return 5;
        if (m >= th[2]) return 4;
        if (m >= th[1]) return 3;
        if (m >= th[0]) return 2;
        return 1;
    }

    /**
     * 8 类分群规则
     * 返回 [code, name, explanation]
     */
    private String[] classify(int r, int f, int m, int recencyDays) {
        if (r >= 4 && f >= 4 && m >= 4)
            return new String[]{"CHAMPION", "冠军客户", "近期频繁高额消费，核心 VIP，建议专属顾问 + 高端项目"};
        if (f >= 4 && r >= 3 && m >= 3)
            return new String[]{"LOYAL", "忠诚客户", "高频消费金额中上，关系稳定，推荐会员升级"};
        if (r >= 4 && m >= 4 && f <= 3)
            return new String[]{"POTENTIAL", "潜力客户", "近期高额低频，可推送新项目 + 套餐"};
        if (r <= 1 && f <= 1)
            return new String[]{"NEW", "新客户", "新注册或首次消费，建立信任窗口期"};
        if (r >= 3 && r < 5 && f >= 2)
            return new String[]{"AT_RISK", "流失风险", "有过消费但近期未到店，需主动关怀"};
        if (r >= 4 && f <= 1 && m <= 2)
            return new String[]{"HIBERNATING", "冬眠客户", "长期未消费，激活成本高"};
        if (r >= 4)
            return new String[]{"LOST", "流失客户", "长期未消费，挽回困难，低成本触达"};
        return new String[]{"GENERAL", "普通客户", "近期/频次/金额均中等，按通用营销"};
    }
}