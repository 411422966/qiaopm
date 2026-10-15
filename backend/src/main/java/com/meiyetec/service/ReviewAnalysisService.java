package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmReview;
import com.meiyetec.entity.CrmReviewDimensionConfig;
import com.meiyetec.entity.CrmServiceImprovement;
import com.meiyetec.entity.CrmServiceImprovementLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.CrmReviewDimensionConfigMapper;
import com.meiyetec.mapper.CrmServiceImprovementLogMapper;
import com.meiyetec.mapper.CrmServiceImprovementMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
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
 * R3.0-H P1.5：评价分析 + 服务改进闭环（补强）
 * <p>
 * - 维度配置 / 多维度分析 / 趋势 / 标签云 / NPS
 * - 评价驱动的服务改进任务（CRM→SOP 改进任务，含状态机 + 效果验证）
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/crm/review/loop")
@RequiredArgsConstructor
public class ReviewAnalysisService {

    private final CrmReviewDimensionConfigMapper dimMapper;
    private final CrmServiceImprovementMapper impMapper;
    private final CrmServiceImprovementLogMapper impLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 维度配置
    // ====================================================================

    @PostMapping("/dim")
    public Result<CrmReviewDimensionConfig> saveDim(@RequestBody CrmReviewDimensionConfig d) {
        if (d.getDimensionCode() == null || d.getDimensionName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getTenantId() == null) d.setTenantId(tenantId);
        if (d.getWeight() == null) d.setWeight(BigDecimal.ONE);
        if (d.getTargetScore() == null) d.setTargetScore(new BigDecimal("4.50"));
        if (d.getEnabled() == null) d.setEnabled(1);
        Long exist = dimMapper.selectCount(new LambdaQueryWrapper<CrmReviewDimensionConfig>()
                .eq(CrmReviewDimensionConfig::getTenantId, tenantId)
                .eq(CrmReviewDimensionConfig::getDimensionCode, d.getDimensionCode()));
        if (exist > 0) {
            jdbc.update(
                "UPDATE crm_review_dimension_config SET dimension_name=?, weight=?, target_score=?, enabled=?, sort_order=? " +
                "WHERE tenant_id=? AND dimension_code=?",
                d.getDimensionName(), d.getWeight(), d.getTargetScore(), d.getEnabled(),
                d.getSortOrder() == null ? 0 : d.getSortOrder(),
                tenantId, d.getDimensionCode());
        } else {
            dimMapper.insert(d);
        }
        return Result.success("已保存", d);
    }

    @GetMapping("/dim/list")
    public Result<List<CrmReviewDimensionConfig>> dimList() {
        return Result.success(dimMapper.selectList(
                new LambdaQueryWrapper<CrmReviewDimensionConfig>()
                        .eq(CrmReviewDimensionConfig::getTenantId,
                                TenantContextHolder.get().getTenantId())
                        .eq(CrmReviewDimensionConfig::getEnabled, 1)
                        .orderByAsc(CrmReviewDimensionConfig::getSortOrder)));
    }

    // ====================================================================
    // 2. 评价分析（核心：多维度 + NPS + 趋势 + 标签云）
    // ====================================================================

    /**
     * 2.1 多维度分析（每个维度：平均分 / 评价数 / 与目标分差距）
     *  返回 Map<维度编码, {score, count, target, gap}>
     */
    @GetMapping("/analysis/dimension")
    public Result<Map<String, Object>> dimensionAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime start = startDate == null ? LocalDate.now().withDayOfYear(1).atStartOfDay()
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now()
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        List<CrmReviewDimensionConfig> dims = dimMapper.selectList(
                new LambdaQueryWrapper<CrmReviewDimensionConfig>()
                        .eq(CrmReviewDimensionConfig::getTenantId, tenantId)
                        .eq(CrmReviewDimensionConfig::getEnabled, 1)
                        .orderByAsc(CrmReviewDimensionConfig::getSortOrder));

        Map<String, Object> result = new LinkedHashMap<>();
        for (CrmReviewDimensionConfig dim : dims) {
            String code = dim.getDimensionCode();
            // 跳过 OVERALL（综合单独算）
            if ("OVERALL".equals(code)) continue;
            String col = dimensionToColumn(code);  // SERVICE/SKILL/ENVIRONMENT/PRICE/...
            if (col == null) continue;

            StringBuilder where = new StringBuilder(
                "WHERE deleted=0 AND tenant_id=? AND status=2 AND " +
                col + " IS NOT NULL AND create_time >= ? AND create_time < ? ");
            List<Object> args = new ArrayList<>();
            args.add(tenantId); args.add(start); args.add(end);
            if (storeId != null) { where.append("AND store_id = ? "); args.add(storeId); }

            Map<String, Object> row = jdbc.queryForMap(
                "SELECT COUNT(*) cnt, COALESCE(AVG(" + col + "), 0) avg_score " +
                "FROM crm_review " + where, args.toArray());
            long count = ((Number) row.get("cnt")).longValue();
            BigDecimal avg = new BigDecimal(String.valueOf(row.get("avg_score")))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal target = dim.getTargetScore() == null ? new BigDecimal("4.50") : dim.getTargetScore();
            BigDecimal gap = avg.subtract(target).setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> data = new HashMap<>();
            data.put("dimensionName", dim.getDimensionName());
            data.put("score", avg);
            data.put("count", count);
            data.put("target", target);
            data.put("gap", gap);
            data.put("weight", dim.getWeight());
            data.put("needsImprovement", gap.compareTo(BigDecimal.ZERO) < 0);
            result.put(code, data);
        }
        return Result.success(result);
    }

    /** 2.2 NPS 计算（推荐者-贬损者） */
    @GetMapping("/analysis/nps")
    public Result<Map<String, Object>> nps(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime start = startDate == null ? LocalDate.now().withDayOfYear(1).atStartOfDay()
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now()
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        // 推荐者 = 5 星；贬损者 = 1-3 星
        String sql =
            "SELECT " +
            "  SUM(CASE WHEN overall_rating = 5 THEN 1 ELSE 0 END) AS promoters, " +
            "  SUM(CASE WHEN overall_rating IN (1,2,3) THEN 1 ELSE 0 END) AS detractors, " +
            "  SUM(CASE WHEN overall_rating = 4 THEN 1 ELSE 0 END) AS passives, " +
            "  COUNT(*) AS total, " +
            "  COALESCE(AVG(overall_rating), 0) AS avg_score " +
            "FROM crm_review " +
            "WHERE deleted=0 AND tenant_id=? AND status=2 AND create_time >= ? AND create_time < ?";
        Map<String, Object> row = jdbc.queryForMap(sql, tenantId, start, end);
        long promoters = ((Number) row.getOrDefault("promoters", 0)).longValue();
        long detractors = ((Number) row.getOrDefault("detractors", 0)).longValue();
        long passives = ((Number) row.getOrDefault("passives", 0)).longValue();
        long total = ((Number) row.getOrDefault("total", 0)).longValue();
        double avg = ((Number) row.getOrDefault("avg_score", 0)).doubleValue();

        // NPS = %promoters - %detractors
        double nps = total == 0 ? 0 : (promoters - detractors) * 100.0 / total;
        BigDecimal avgBd = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> data = new HashMap<>();
        data.put("nps", BigDecimal.valueOf(nps).setScale(2, RoundingMode.HALF_UP));
        data.put("avgRating", avgBd);
        data.put("promoters", promoters);
        data.put("passives", passives);
        data.put("detractors", detractors);
        data.put("total", total);
        data.put("promoterRate", total == 0 ? 0 : BigDecimal.valueOf(promoters * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP));
        data.put("detractorRate", total == 0 ? 0 : BigDecimal.valueOf(detractors * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP));
        data.put("passiveRate", total == 0 ? 0 : BigDecimal.valueOf(passives * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP));
        return Result.success(data);
    }

    /** 2.3 趋势分析（按日/周/月） */
    @GetMapping("/analysis/trend")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAY") String bucket) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime start = startDate == null ? LocalDate.now().minusDays(30).atStartOfDay()
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now()
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        String fmt = switch (bucket.toUpperCase()) {
            case "WEEK" -> "%x-%v";       // 年-周
            case "MONTH" -> "%Y-%m";      // 年-月
            default -> "%Y-%m-%d";        // 年-月-日
        };

        String sql =
            "SELECT DATE_FORMAT(create_time, '" + fmt + "') AS bucket, " +
            "       COUNT(*) cnt, " +
            "       COALESCE(AVG(overall_rating), 0) avg_score, " +
            "       SUM(CASE WHEN overall_rating <= 2 THEN 1 ELSE 0 END) low_count " +
            "FROM crm_review " +
            "WHERE deleted=0 AND tenant_id=? AND status=2 AND create_time >= ? AND create_time < ? " +
            "GROUP BY bucket ORDER BY bucket";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, start, end);
        for (Map<String, Object> r : rows) {
            Object avg = r.get("avg_score");
            if (avg != null) r.put("avgScore", BigDecimal.valueOf(((Number) avg).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return Result.success(rows);
    }

    /** 2.4 标签云分析 */
    @GetMapping("/analysis/tags")
    public Result<List<Map<String, Object>>> tagAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer overallRating) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime start = startDate == null ? LocalDate.now().withDayOfYear(1).atStartOfDay()
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now()
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        StringBuilder where = new StringBuilder(
            "WHERE deleted=0 AND tenant_id=? AND status=2 AND tags IS NOT NULL AND tags <> '' " +
            "AND create_time >= ? AND create_time < ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId); args.add(start); args.add(end);
        if (overallRating != null) { where.append("AND overall_rating = ? "); args.add(overallRating); }

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT tags FROM crm_review " + where, args.toArray());

        // 拆分统计 tags（逗号分隔）
        Map<String, TagStat> tagMap = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String tags = String.valueOf(r.get("tags"));
            if (tags.isEmpty()) continue;
            String[] arr = tags.split("[,，;；]");
            for (String t : arr) {
                final String tagName = t.trim();
                if (tagName.isEmpty()) continue;
                TagStat stat = tagMap.computeIfAbsent(tagName, k -> new TagStat(k));
                stat.count++;
            }
        }
        List<Map<String, Object>> result = tagMap.values().stream()
                .sorted((a, b) -> Long.compare(b.count, a.count))
                .limit(50)
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("tag", t.name);
                    m.put("count", t.count);
                    return m;
                })
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /** 2.5 服务/员工评分 Top / Bottom 排行 */
    @GetMapping("/analysis/rank")
    public Result<Map<String, Object>> ranking(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sourceType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime start = startDate == null ? LocalDate.now().withDayOfYear(1).atStartOfDay()
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now()
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        StringBuilder where = new StringBuilder(
            "WHERE deleted=0 AND tenant_id=? AND status=2 AND create_time >= ? AND create_time < ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId); args.add(start); args.add(end);
        if (sourceType != null && !sourceType.isEmpty()) {
            where.append("AND source_type = ? "); args.add(sourceType);
        }

        String sql =
            "SELECT source_type, source_id, source_no, COUNT(*) cnt, " +
            "       COALESCE(AVG(overall_rating), 0) avg_score, " +
            "       SUM(CASE WHEN overall_rating <= 2 THEN 1 ELSE 0 END) low_count " +
            "FROM crm_review " + where +
            "GROUP BY source_type, source_id, source_no " +
            "HAVING cnt >= 3 " +
            "ORDER BY avg_score DESC LIMIT 10";
        List<Map<String, Object>> top = jdbc.queryForList(sql, args.toArray());

        StringBuilder sql2 = new StringBuilder(sql);
        sql2.append(" ASC LIMIT 10 ".replace("DESC LIMIT 10", "ASC LIMIT 10"));
        // 简化：去掉 DESC
        String bottomSql = sql.replace("ORDER BY avg_score DESC LIMIT 10", "ORDER BY avg_score ASC LIMIT 10");
        List<Map<String, Object>> bottom = jdbc.queryForList(bottomSql, args.toArray());

        Map<String, Object> data = new HashMap<>();
        data.put("top", top);
        data.put("bottom", bottom);
        return Result.success(data);
    }

    // ====================================================================
    // 3. 服务改进任务（评价→改进闭环）
    // ====================================================================

    /** 3.1 创建改进任务（可手动 / 也可由低分自动触发） */
    @PostMapping("/improvement")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> createImprovement(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CrmServiceImprovement imp = new CrmServiceImprovement();
        imp.setTenantId(tenantId);
        imp.setBrandId(body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue());
        imp.setStoreId(body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
        imp.setImprovementNo("IMP" + System.currentTimeMillis());
        imp.setTitle((String) body.get("title"));
        imp.setSourceType((String) body.getOrDefault("sourceType", "REVIEW"));
        imp.setSourceId(body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue());
        imp.setSourceNo((String) body.get("sourceNo"));
        imp.setDimensionCode((String) body.get("dimensionCode"));
        imp.setCurrentScore(body.get("currentScore") == null ? null
                : new BigDecimal(body.get("currentScore").toString()));
        imp.setTargetScore(body.get("targetScore") == null ? null
                : new BigDecimal(body.get("targetScore").toString()));
        if (imp.getCurrentScore() != null && imp.getTargetScore() != null) {
            imp.setGapScore(imp.getCurrentScore().subtract(imp.getTargetScore())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        imp.setProblem((String) body.get("problem"));
        imp.setRootCause((String) body.get("rootCause"));
        imp.setActionPlan((String) body.get("actionPlan"));
        imp.setExpectedEffect((String) body.get("expectedEffect"));
        imp.setOwnerId(body.get("ownerId") == null ? null : ((Number) body.get("ownerId")).longValue());
        imp.setOwnerName((String) body.get("ownerName"));
        imp.setPriority(body.get("priority") == null ? 1 : ((Number) body.get("priority")).intValue());
        imp.setStatus(1);  // 待评估
        imp.setDeadline(body.get("deadline") == null ? null
                : LocalDateTime.parse((String) body.get("deadline")));
        imp.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        impMapper.insert(imp);
        writeLog(imp.getId(), tenantId, "CREATE", null, 1, "创建改进任务：" + imp.getTitle(),
                null, imp.getOperatorName());
        return Result.success("已创建", imp);
    }

    /** 3.2 状态流转：待评估→待审批→执行中→已完成 */
    @PostMapping("/improvement/{id}/transit")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> transit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CrmServiceImprovement imp = impMapper.selectById(id);
        if (imp == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");

        Integer toStatus = body.get("toStatus") == null ? null
                : ((Number) body.get("toStatus")).intValue();
        if (toStatus == null) return Result.error(BizCode.BAD_REQUEST, "toStatus 必填");

        // 状态机校验
        if (!isValidTransit(imp.getStatus(), toStatus)) {
            return Result.error(BizCode.CONFLICT,
                    "状态 " + imp.getStatus() + " → " + toStatus + " 不允许");
        }

        imp.setStatus(toStatus);
        if (toStatus == 3 && imp.getStartedAt() == null) imp.setStartedAt(LocalDateTime.now());
        if (toStatus == 4) imp.setCompletedAt(LocalDateTime.now());
        // 效果验证
        if (toStatus == 4) {
            if (body.get("postScore") != null) {
                imp.setPostScore(new BigDecimal(body.get("postScore").toString()));
            }
            imp.setEffectNote((String) body.get("effectNote"));
        }
        impMapper.updateById(imp);

        String operatorName = (String) body.getOrDefault("operatorName", "admin");
        writeLog(id, tenantId, "TRANSIT", null, toStatus,
                "状态流转：" + imp.getStatus() + " → " + toStatus, null, operatorName);
        return Result.success("已流转", null);
    }

    /** 3.3 改进任务列表 */
    @GetMapping("/improvement/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> impPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String dimensionCode,
            @RequestParam(required = false) Long ownerId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, improvement_no, title, source_type, source_id, source_no, " +
            "dimension_code, current_score, target_score, gap_score, status, priority, " +
            "owner_id, owner_name, started_at, completed_at, deadline, " +
            "post_score, effect_note, create_time " +
            "FROM crm_service_improvement WHERE deleted=0 AND tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (priority != null) { sql.append("AND priority = ? "); args.add(priority); }
        if (dimensionCode != null) { sql.append("AND dimension_code = ? "); args.add(dimensionCode); }
        if (ownerId != null) { sql.append("AND owner_id = ? "); args.add(ownerId); }
        sql.append("ORDER BY priority DESC, deadline ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM crm_service_improvement WHERE deleted=0 AND tenant_id=? ");
        List<Object> cargs = new ArrayList<>(args.subList(0, args.size() - 2));
        if (status != null) csql.append("AND status = ? ");
        if (priority != null) csql.append("AND priority = ? ");
        if (dimensionCode != null) csql.append("AND dimension_code = ? ");
        if (ownerId != null) csql.append("AND owner_id = ? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    /** 3.4 自动从低分评价触发改进 */
    @PostMapping("/improvement/auto-from-review/{reviewId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> autoFromReview(@PathVariable Long reviewId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CrmReview r = jdbc.queryForList(
            "SELECT * FROM crm_review WHERE id = ? AND deleted = 0", reviewId)
            .stream().findFirst().map(m -> {
                CrmReview rev = new CrmReview();
                rev.setId(((Number) m.get("id")).longValue());
                rev.setSourceType((String) m.get("source_type"));
                rev.setSourceId(m.get("source_id") == null ? null : ((Number) m.get("source_id")).longValue());
                rev.setSourceNo((String) m.get("source_no"));
                rev.setOverallRating(((Number) m.get("overall_rating")).intValue());
                rev.setContent((String) m.get("content"));
                rev.setStoreId(m.get("store_id") == null ? null : ((Number) m.get("store_id")).longValue());
                rev.setBrandId(m.get("brand_id") == null ? null : ((Number) m.get("brand_id")).longValue());
                return rev;
            }).orElse(null);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "评价不存在");
        if (r.getOverallRating() == null || r.getOverallRating() > 3)
            return Result.error(BizCode.BAD_REQUEST, "仅 1-3 星评价可触发改进任务");

        // 找最低分维度
        String worstDim = null;
        Integer worstScore = 5;
        for (Map.Entry<String, Integer> e : Map.of(
                "SERVICE", r.getServiceRating(),
                "SKILL", r.getSkillRating(),
                "ENVIRONMENT", r.getEnvironmentRating(),
                "PRICE", r.getPriceRating()).entrySet()) {
            Integer v = e.getValue();
            if (v != null && v < worstScore) {
                worstScore = v;
                worstDim = e.getKey();
            }
        }
        if (worstDim == null) worstDim = "OVERALL";

        Map<String, Object> body = new HashMap<>();
        body.put("title", "评价驱动的改进：" + worstDim + " 评分仅 " + worstScore + " 星");
        body.put("sourceType", "REVIEW");
        body.put("sourceId", reviewId);
        body.put("sourceNo", r.getSourceNo());
        body.put("dimensionCode", worstDim);
        body.put("currentScore", worstScore);
        body.put("targetScore", 4.5);
        body.put("problem", r.getContent());
        body.put("priority", r.getOverallRating() == 1 ? 4 : 3);
        body.put("operatorName", "system-auto");
        body.put("storeId", r.getStoreId());
        body.put("brandId", r.getBrandId());
        return createImprovement(body);
    }

    /** 3.5 改进任务统计 */
    @GetMapping("/improvement/stats")
    public Result<Map<String, Object>> impStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();
        for (int s = 1; s <= 5; s++) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_service_improvement WHERE deleted=0 AND tenant_id=? AND status=?",
                Long.class, tenantId, s);
            data.put("status_" + s, cnt);
        }
        // 平均 gap
        Double avgGap = jdbc.queryForObject(
            "SELECT COALESCE(AVG(gap_score), 0) FROM crm_service_improvement " +
            "WHERE deleted=0 AND tenant_id=? AND status IN (3,4)",
            Double.class, tenantId);
        data.put("avgGap", avgGap == null ? 0 : Math.round(avgGap * 100) / 100.0);
        // 改进后分数均值
        Double avgPost = jdbc.queryForObject(
            "SELECT COALESCE(AVG(post_score), 0) FROM crm_service_improvement " +
            "WHERE deleted=0 AND tenant_id=? AND status=4 AND post_score IS NOT NULL",
            Double.class, tenantId);
        data.put("avgPostScore", avgPost == null ? 0 : Math.round(avgPost * 100) / 100.0);
        return Result.success(data);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    /** 维度编码 → 评价表字段名 */
    private static String dimensionToColumn(String code) {
        return switch (code) {
            case "SERVICE" -> "service_rating";
            case "SKILL" -> "skill_rating";
            case "ENVIRONMENT" -> "environment_rating";
            case "PRICE" -> "price_rating";
            case "OVERALL" -> "overall_rating";
            default -> null;
        };
    }

    /** 状态机：1→2→3→4；1/2→5（取消）；3→5（取消） */
    private static boolean isValidTransit(Integer from, Integer to) {
        if (from == null) return to == 1;
        return switch (from) {
            case 1 -> to == 2 || to == 5;
            case 2 -> to == 3 || to == 5;
            case 3 -> to == 4 || to == 5;
            case 4 -> false;  // 已完成不可流转
            case 5 -> false;  // 已取消不可流转
            default -> false;
        };
    }

    private void writeLog(Long impId, Long tenantId, String action,
                          Integer from, Integer to, String content,
                          Long operatorId, String operatorName) {
        CrmServiceImprovementLog l = new CrmServiceImprovementLog();
        l.setTenantId(tenantId);
        l.setImprovementId(impId);
        l.setActionType(action);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setContent(content == null ? "" : (content.length() > 990 ? content.substring(0, 990) : content));
        l.setOperatorId(operatorId);
        l.setOperatorName(operatorName == null ? "system" : operatorName);
        impLogMapper.insert(l);
    }

    private static class TagStat {
        final String name;
        long count;
        TagStat(String name) { this.name = name; }
    }
}