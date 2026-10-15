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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P3 · T-P03：视觉档案与 AI 对比（含合规）
 *  - 视觉档案采集（SHA-256 哈希 + 默认脱敏）
 *  - AI 标签提取（Demo：基于 hash 模拟特征）
 *  - 前后对比（Demo：基于时间差模拟差异分）
 *  - 人工复核机制
 *  - 图像授权（电子签名）
 *  - 撤回机制（合规必须）
 */
@RestController
@RequestMapping("/mh/visual")
@RequiredArgsConstructor
public class VisualArchiveService {

    private final MhVisualArchiveMapper archiveMapper;
    private final MhVisualCompareMapper compareMapper;
    private final MhImageConsentMapper consentMapper;
    private final MhImageWithdrawMapper withdrawMapper;
    private final VisualAiService visualAi;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 视觉档案
    // ====================================================================

    @PostMapping("/archive")
    @Transactional(rollbackFor = Exception.class)
    public Result<MhVisualArchive> createArchive(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String archiveType = (String) body.get("archiveType");
        String imageUrl = (String) body.get("imageUrl");
        if (memberId == null || archiveType == null || imageUrl == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId / archiveType / imageUrl 必填");

        // 合规检查：必须有 AI_ANALYSIS 授权（如要 AI 分析）
        if (Boolean.TRUE.equals(body.get("requireAi"))) {
            Long consent = consentMapper.selectCount(new LambdaQueryWrapper<MhImageConsent>()
                    .eq(MhImageConsent::getMemberId, memberId)
                    .eq(MhImageConsent::getConsentType, "AI_ANALYSIS")
                    .eq(MhImageConsent::getRevoked, 0));
            if (consent == 0) return Result.error(BizCode.FORBIDDEN, "未获得 AI 分析授权");
        }

        // 计算 SHA-256（生产：上传文件后计算）
        String imageHash = (String) body.getOrDefault("imageHash",
                "sha256-" + Integer.toHexString(imageUrl.hashCode()) + "-" + System.currentTimeMillis());

        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        Long brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);

        MhVisualArchive a = new MhVisualArchive();
        a.setTenantId(tenantId);
        a.setBrandId(brandId);
        a.setStoreId(storeId);
        a.setMemberId(memberId);
        a.setArchiveCode("VA" + System.currentTimeMillis());
        a.setArchiveType(archiveType);
        a.setBodyPart((String) body.get("bodyPart"));
        a.setProjectId(body.get("projectId") == null ? null : ((Number) body.get("projectId")).longValue());
        a.setProjectName((String) body.get("projectName"));
        a.setImageUrl(imageUrl);
        a.setThumbnailUrl((String) body.get("thumbnailUrl"));
        a.setImageHash(imageHash);
        a.setFaceMasked(body.get("faceMasked") == null ? 1 : ((Number) body.get("faceMasked")).intValue());
        a.setFaceMaskType((String) body.getOrDefault("faceMaskType", "BLUR"));
        a.setDevice((String) body.get("device"));
        a.setEnvInfo((String) body.get("envInfo"));
        a.setRelatedOrderId(body.get("relatedOrderId") == null ? null : ((Number) body.get("relatedOrderId")).longValue());
        a.setRelatedOrderNo((String) body.get("relatedOrderNo"));
        a.setPhotographerId(body.get("photographerId") == null ? null : ((Number) body.get("photographerId")).longValue());
        a.setPhotographerName((String) body.get("photographerName"));
        a.setRemark((String) body.get("remark"));
        a.setOperatorName((String) body.getOrDefault("operatorName", "admin"));

        // AI 自动分析（AI-006：优先真实模型，降级确定性算法）
        if (Boolean.TRUE.equals(body.get("analyzeNow"))) {
            // imageUrl 可选：真实模型需要，hash 降级用 imageHash
            Map<String, Object> aiResult = visualAi.analyzeWithImageUrl(archiveType, imageUrl, imageHash);
            a.setAiAnalyzed(1);
            a.setAiFeaturesJson(aiResult.get("features").toString());
            a.setAiTags((String) aiResult.get("tags"));
            a.setAiScore((BigDecimal) aiResult.get("score"));
        }

        archiveMapper.insert(a);
        return Result.success("已创建", a);
    }

    @GetMapping("/archive/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> archivePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String archiveType,
            @RequestParam(required = false) Integer aiAnalyzed,
            @RequestParam(required = false) Integer revoked) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, archive_code, archive_type, body_part, project_name, member_id, " +
            "       image_url, thumbnail_url, image_hash, face_masked, face_mask_type, " +
            "       ai_analyzed, ai_tags, ai_score, related_order_id, related_order_no, " +
            "       photographer_name, device, env_info, revoked, revoked_at, " +
            "       operator_name, create_time " +
            "FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND member_id = ? "); args.add(memberId); }
        if (archiveType != null && !archiveType.isEmpty()) { sql.append("AND archive_type = ? "); args.add(archiveType); }
        if (aiAnalyzed != null) { sql.append("AND ai_analyzed = ? "); args.add(aiAnalyzed); }
        if (revoked != null) { sql.append("AND revoked = ? "); args.add(revoked); }
        sql.append("ORDER BY create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (memberId != null) { csql.append("AND member_id = ? "); cargs.add(memberId); }
        if (archiveType != null && !archiveType.isEmpty()) { csql.append("AND archive_type = ? "); cargs.add(archiveType); }
        if (aiAnalyzed != null) { csql.append("AND ai_analyzed = ? "); cargs.add(aiAnalyzed); }
        if (revoked != null) { csql.append("AND revoked = ? "); cargs.add(revoked); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/archive/{id}/revoke")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revokeArchive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhVisualArchive a = archiveMapper.selectById(id);
        if (a == null) return Result.error(BizCode.NOT_FOUND, "档案不存在");
        if (a.getRevoked() != null && a.getRevoked() == 1) return Result.error(BizCode.CONFLICT, "已撤回");
        a.setRevoked(1);
        a.setRevokedAt(LocalDateTime.now());
        a.setRevokedReason((String) body.get("reason"));
        archiveMapper.updateById(a);
        return Result.success("已撤回", null);
    }

    @GetMapping("/archive/stats")
    public Result<Map<String, Object>> archiveStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> stats = new HashMap<>();

        // 总档案数
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ?",
            Long.class, tenantId);
        stats.put("total", total);

        // 近30天新增
        Long new30d = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)",
            Long.class, tenantId);
        stats.put("new30d", new30d);

        // 今日新增（最近24小时）
        Long todayNew = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
            Long.class, tenantId);
        stats.put("todayNew", todayNew);

        // AI已分析
        Long aiAnalyzed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND ai_analyzed = 1",
            Long.class, tenantId);
        stats.put("aiAnalyzed", aiAnalyzed);

        // AI分析比例
        double aiRatio = total > 0 ? (aiAnalyzed * 100.0 / total) : 0;
        stats.put("aiRatio", String.format("%.1f", aiRatio));

        // 已撤回
        Long revoked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND revoked = 1",
            Long.class, tenantId);
        stats.put("revoked", revoked);

        // 待处理撤回申请
        Long pendingWithdraw = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_image_withdraw WHERE tenant_id = ? AND status = 1",
            Long.class, tenantId);
        stats.put("pendingWithdraw", pendingWithdraw);

        // === 优化1: 部位分布聚合（用于数据洞察 Top3） ===
        List<Map<String, Object>> bodyPartRows = jdbc.queryForList(
            "SELECT COALESCE(NULLIF(TRIM(body_part),''), '未填') AS bp, COUNT(*) AS cnt " +
            "FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND revoked = 0 " +
            "GROUP BY bp ORDER BY cnt DESC LIMIT 20",
            tenantId);
        Map<String, Object> bodyParts = new LinkedHashMap<>();
        for (Map<String, Object> r : bodyPartRows) bodyParts.put(String.valueOf(r.get("bp")), r.get("cnt"));
        stats.put("bodyParts", bodyParts);

        // === 优化2: 近7天趋势（用于趋势图 sparkline7d） ===
        List<Map<String, Object>> trendRows = jdbc.queryForList(
            "SELECT DATE_FORMAT(create_time, '%m-%d') AS dt, COUNT(*) AS cnt " +
            "FROM mh_visual_archive WHERE deleted = 0 AND tenant_id = ? AND revoked = 0 " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY dt ORDER BY dt ASC",
            tenantId);
        // 补齐近7天（即使当天为0也显示）
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trend7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            String key = d.format(df);
            long val = trendRows.stream()
                .filter(r -> key.equals(String.valueOf(r.get("dt"))))
                .findFirst().map(r -> ((Number) r.get("cnt")).longValue()).orElse(0L);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", key);
            entry.put("value", val);
            trend7d.add(entry);
        }
        stats.put("trend7d", trend7d);

        return Result.success(stats);
    }

    @PostMapping("/archive/{id}/reanalyze")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reanalyzeArchive(@PathVariable Long id) {
        MhVisualArchive a = archiveMapper.selectById(id);
        if (a == null) return Result.error(BizCode.NOT_FOUND, "档案不存在");
        
        // AI-006：重新进行AI分析（传递 imageUrl 以启用真实模型）
        Map<String, Object> aiResult = visualAi.analyzeWithImageUrl(
                a.getArchiveType(), a.getImageUrl(), a.getImageHash());
        a.setAiAnalyzed(1);
        a.setAiFeaturesJson(aiResult.get("features").toString());
        a.setAiTags((String) aiResult.get("tags"));
        a.setAiScore((BigDecimal) aiResult.get("score"));
        archiveMapper.updateById(a);
        
        return Result.success("已重新分析", null);
    }

    @PostMapping("/archive/batch-analyze")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchAnalyzeArchives(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "ids 不能为空");
        }
        
        for (Long id : ids) {
            MhVisualArchive a = archiveMapper.selectById(id);
            if (a != null && (a.getAiAnalyzed() == null || a.getAiAnalyzed() == 0)) {
                // AI-006：批量重新分析（支持真实模型）
                Map<String, Object> aiResult = visualAi.analyzeWithImageUrl(
                        a.getArchiveType(), a.getImageUrl(), a.getImageHash());
                a.setAiAnalyzed(1);
                a.setAiFeaturesJson(aiResult.get("features").toString());
                a.setAiTags((String) aiResult.get("tags"));
                a.setAiScore((BigDecimal) aiResult.get("score"));
                archiveMapper.updateById(a);
            }
        }
        
        return Result.success("已发起批量分析", null);
    }

    @PostMapping("/archive/batch-revoke")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchRevokeArchives(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        String reason = (String) body.get("reason");
        
        if (ids == null || ids.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "ids 不能为空");
        }
        
        for (Long id : ids) {
            MhVisualArchive a = archiveMapper.selectById(id);
            if (a != null && (a.getRevoked() == null || a.getRevoked() == 0)) {
                a.setRevoked(1);
                a.setRevokedAt(LocalDateTime.now());
                a.setRevokedReason(reason);
                archiveMapper.updateById(a);
            }
        }
        
        return Result.success("已批量撤回", null);
    }

    // ====================================================================
    // AI 对比
    // ====================================================================

    @PostMapping("/compare")
    @Transactional(rollbackFor = Exception.class)
    public Result<MhVisualCompare> createCompare(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long beforeId = body.get("beforeId") == null ? null : ((Number) body.get("beforeId")).longValue();
        Long afterId = body.get("afterId") == null ? null : ((Number) body.get("afterId")).longValue();
        if (memberId == null || beforeId == null || afterId == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId / beforeId / afterId 必填");

        MhVisualArchive before = archiveMapper.selectById(beforeId);
        MhVisualArchive after = archiveMapper.selectById(afterId);
        if (before == null || after == null) return Result.error(BizCode.NOT_FOUND, "档案不存在");

        // 计算差异（真实算法替换 Mock）
        Map<String, Object> diffResult = visualAi.compareArchives(before, after);

        MhVisualCompare c = new MhVisualCompare();
        c.setTenantId(TenantContextHolder.get().getTenantId());
        c.setBrandId(before.getBrandId());
        c.setMemberId(memberId);
        c.setBeforeId(beforeId);
        c.setAfterId(afterId);
        c.setCompareType((String) body.getOrDefault("compareType", "CROSS_TREATMENT"));
        c.setSimilarity((BigDecimal) diffResult.get("similarity"));
        c.setDiffScore((BigDecimal) diffResult.get("diffScore"));
        c.setSkinDiff(diffResult.get("skinDiff").toString());
        c.setAiSummary((String) diffResult.get("summary"));
        c.setImprovementAreas((String) diffResult.get("improvement"));
        c.setRegressionAreas((String) diffResult.get("regression"));
        c.setModelVersion("AI-VISION-V1-DEMO");
        c.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        compareMapper.insert(c);
        return Result.success("已对比", c);
    }

    @GetMapping("/compare/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> comparePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Integer manualReviewed) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.member_id, c.before_id, c.after_id, c.compare_type, " +
            "       c.similarity, c.diff_score, c.ai_summary, c.improvement_areas, c.regression_areas, " +
            "       c.manual_reviewed, c.reviewer_name, c.reviewed_at, c.model_version, c.create_time, " +
            "       m.name AS member_name, b.archive_code AS before_code, a.archive_code AS after_code " +
            "FROM mh_visual_compare c " +
            "JOIN crm_member m ON m.id = c.member_id " +
            "JOIN mh_visual_archive b ON b.id = c.before_id " +
            "JOIN mh_visual_archive a ON a.id = c.after_id " +
            "WHERE c.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND c.member_id = ? "); args.add(memberId); }
        if (manualReviewed != null) { sql.append("AND c.manual_reviewed = ? "); args.add(manualReviewed); }
        sql.append("ORDER BY c.create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM mh_visual_compare c WHERE c.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (memberId != null) { csql.append("AND c.member_id = ? "); cargs.add(memberId); }
        if (manualReviewed != null) { csql.append("AND c.manual_reviewed = ? "); cargs.add(manualReviewed); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    /**
     * 人工复核
     */
    @PostMapping("/compare/{id}/review")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reviewCompare(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhVisualCompare c = compareMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "对比不存在");
        c.setManualReviewed(1);
        c.setReviewerId(body.get("reviewerId") == null ? null : ((Number) body.get("reviewerId")).longValue());
        c.setReviewerName((String) body.get("reviewerName"));
        c.setReviewNote((String) body.get("reviewNote"));
        c.setReviewedAt(LocalDateTime.now());
        compareMapper.updateById(c);
        return Result.success("已复核", null);
    }

    // ====================================================================
    // 图像授权（合规核心）
    // ====================================================================

    @PostMapping("/consent")
    @Transactional(rollbackFor = Exception.class)
    public Result<MhImageConsent> signConsent(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String consentType = (String) body.get("consentType");
        String signMethod = (String) body.getOrDefault("signMethod", "ELECTRONIC");
        String signerName = (String) body.get("signerName");
        if (memberId == null || consentType == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId / consentType 必填");

        // 幂等：同人同类型已存在有效授权则不再签
        Long dup = consentMapper.selectCount(new LambdaQueryWrapper<MhImageConsent>()
                .eq(MhImageConsent::getMemberId, memberId)
                .eq(MhImageConsent::getConsentType, consentType)
                .eq(MhImageConsent::getRevoked, 0));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "已有有效授权");

        MhImageConsent c = new MhImageConsent();
        c.setTenantId(TenantContextHolder.get().getTenantId());
        c.setMemberId(memberId);
        c.setConsentType(consentType);
        c.setScope((String) body.get("scope"));
        c.setSignMethod(signMethod);
        c.setSignatureHash(hashSignature(memberId, consentType, signerName));
        c.setSignerName(signerName);
        c.setSignerIdNo((String) body.get("signerIdNo"));
        c.setSignerRelation((String) body.getOrDefault("signerRelation", "SELF"));
        c.setEffectiveAt(LocalDateTime.now());
        c.setConsentDocUrl((String) body.get("consentDocUrl"));
        c.setRevoked(0);
        c.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        consentMapper.insert(c);
        return Result.success("已签署", c);
    }

    @GetMapping("/consent/member/{memberId}")
    public Result<List<MhImageConsent>> memberConsents(@PathVariable Long memberId) {
        return Result.success(consentMapper.selectList(new LambdaQueryWrapper<MhImageConsent>()
                .eq(MhImageConsent::getMemberId, memberId)
                .eq(MhImageConsent::getRevoked, 0)));
    }

    @PostMapping("/consent/{id}/revoke")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revokeConsent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhImageConsent c = consentMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "授权不存在");
        if (c.getRevoked() != null && c.getRevoked() == 1) return Result.error(BizCode.CONFLICT, "已撤回");
        c.setRevoked(1);
        c.setRevokedAt(LocalDateTime.now());
        c.setRevokedReason((String) body.get("reason"));
        consentMapper.updateById(c);
        return Result.success("已撤回授权", null);
    }

    @GetMapping("/consent/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> consentPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String consentType,
            @RequestParam(required = false) Integer revoked) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.member_id, m.name AS member_name, c.consent_type, c.scope, " +
            "       c.sign_method, c.signer_name, c.signer_relation, c.effective_at, c.expire_at, " +
            "       c.revoked, c.revoked_at, c.revoked_reason, c.consent_doc_url, c.operator_name, c.create_time " +
            "FROM mh_image_consent c " +
            "LEFT JOIN crm_member m ON m.id = c.member_id " +
            "WHERE c.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND c.member_id = ? "); args.add(memberId); }
        if (consentType != null && !consentType.isEmpty()) { sql.append("AND c.consent_type = ? "); args.add(consentType); }
        if (revoked != null) { sql.append("AND c.revoked = ? "); args.add(revoked); }
        sql.append("ORDER BY c.create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mh_image_consent c WHERE c.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (memberId != null) { csql.append("AND c.member_id = ? "); cargs.add(memberId); }
        if (consentType != null && !consentType.isEmpty()) { csql.append("AND c.consent_type = ? "); cargs.add(consentType); }
        if (revoked != null) { csql.append("AND c.revoked = ? "); cargs.add(revoked); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/consent/expiring")
    public Result<Map<String, Object>> expiringConsents() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> result = new HashMap<>();
        
        // 统计即将过期的授权（30天内过期）
        Long expiringSoon = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_image_consent " +
            "WHERE tenant_id = ? AND revoked = 0 " +
            "AND expire_at IS NOT NULL AND expire_at <= DATE_ADD(NOW(), INTERVAL 30 DAY)", 
            Long.class, tenantId);
        result.put("expiringSoon", expiringSoon);
        
        return Result.success(result);
    }

    // ====================================================================
    // 撤回申请（合规必须）
    // ====================================================================

    @PostMapping("/withdraw")
    @Transactional(rollbackFor = Exception.class)
    public Result<MhImageWithdraw> applyWithdraw(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String withdrawScope = (String) body.get("withdrawScope");
        if (memberId == null || withdrawScope == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId / withdrawScope 必填");

        MhImageWithdraw w = new MhImageWithdraw();
        w.setTenantId(TenantContextHolder.get().getTenantId());
        w.setMemberId(memberId);
        w.setWithdrawScope(withdrawScope);
        w.setTargetType((String) body.get("targetType"));
        w.setTargetIds((String) body.get("targetIds"));
        w.setReason((String) body.get("reason"));
        w.setStatus(1);
        w.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        withdrawMapper.insert(w);
        return Result.success("已提交撤回申请", w);
    }

    @PostMapping("/withdraw/{id}/handle")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> handleWithdraw(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhImageWithdraw w = withdrawMapper.selectById(id);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "申请不存在");
        if (w.getStatus() != 1) return Result.error(BizCode.CONFLICT, "申请已处理");
        Integer newStatus = body.get("status") == null ? 2 : ((Number) body.get("status")).intValue();

        w.setStatus(newStatus);
        w.setHandlerId(body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue());
        w.setHandlerName((String) body.get("handlerName"));
        w.setHandleRemark((String) body.get("handleRemark"));
        w.setHandledAt(LocalDateTime.now());
        withdrawMapper.updateById(w);

        // 批准撤回：执行撤回动作
        if (newStatus == 2) {
            if ("ALL".equals(w.getWithdrawScope()) || "STAFF_VIEW".equals(w.getWithdrawScope())) {
                jdbc.update("UPDATE mh_visual_archive SET revoked = 1, revoked_at = NOW(), revoked_reason = '会员撤回' " +
                        "WHERE member_id = ? AND deleted = 0", w.getMemberId());
            }
            if ("ALL".equals(w.getWithdrawScope())) {
                jdbc.update("UPDATE mh_image_consent SET revoked = 1, revoked_at = NOW(), revoked_reason = '会员撤回' " +
                        "WHERE member_id = ? AND deleted = 0", w.getMemberId());
            }
        }
        return Result.success("已处理", null);
    }

    /** 撤回申请统计：近7天每日撤回数量 + 今日新申请数 */
    @GetMapping("/withdraw/stats")
    public Result<Map<String, Object>> withdrawStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> result = new HashMap<>();

        // 今日新申请数
        Long todayApply = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_image_withdraw " +
            "WHERE tenant_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
            Long.class, tenantId);
        result.put("todayApply", todayApply);

        // 近7天每日撤回申请趋势（按申请时间）
        List<Map<String, Object>> trendRows = jdbc.queryForList(
            "SELECT DATE_FORMAT(create_time, '%m-%d') AS dt, COUNT(*) AS cnt " +
            "FROM mh_image_withdraw WHERE tenant_id = ? " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY dt ORDER BY dt ASC",
            tenantId);

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trend7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            String key = d.format(df);
            long val = trendRows.stream()
                .filter(r -> key.equals(String.valueOf(r.get("dt"))))
                .findFirst().map(r -> ((Number) r.get("cnt")).longValue()).orElse(0L);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", key);
            entry.put("value", val);
            trend7d.add(entry);
        }
        result.put("trend7d", trend7d);
        return Result.success(result);
    }

    @GetMapping("/withdraw/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> withdrawPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT w.id, w.member_id, m.name AS member_name, w.withdraw_scope, w.target_type, " +
            "       w.reason, w.status, w.handler_name, w.handle_remark, w.handled_at, " +
            "       w.operator_name, w.create_time " +
            "FROM mh_image_withdraw w " +
            "LEFT JOIN crm_member m ON m.id = w.member_id " +
            "WHERE w.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        
        // 为 COUNT 查询单独准备参数（不包含 LIMIT/OFFSET）
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(tenantId);
        
        if (status != null) { 
            sql.append("AND w.status = ? "); 
            args.add(status); 
            countArgs.add(status);
        }
        sql.append("ORDER BY w.create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mh_image_withdraw w WHERE w.tenant_id = ? ");
        if (status != null) { csql.append("AND w.status = ? "); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, countArgs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    // ====================================================================
    // 辅助
    // ====================================================================

    private String hashSignature(Long memberId, String type, String signerName) {
        try {
            String src = memberId + ":" + type + ":" + signerName + ":" + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "fallback-" + System.currentTimeMillis();
        }
    }

    /** 模拟 AI 特征分析（生产替换为真实模型调用） */
    private Map<String, Object> mockAiAnalyze(String type, String hash) {
        Random rnd = new Random(hash.hashCode());
        Map<String, Object> res = new HashMap<>();
        String[] skinTags = {"色斑", "毛孔", "细纹", "痤疮", "光泽", "弹性", "水润", "暗沉"};
        String[] faceTags = {"轮廓", "对称", "眼袋", "法令纹", "苹果肌", "嘴角"};
        String[] bodyTags = {"均匀", "疤痕", "色差", "松弛", "紧致"};
        String pool = type.contains("SKIN") ? String.join(",", skinTags)
                : type.contains("FACE") ? String.join(",", faceTags)
                : String.join(",", bodyTags);
        String[] tagArr = pool.split(",");
        StringBuilder tags = new StringBuilder();
        int tagCount = 3 + rnd.nextInt(3);
        for (int i = 0; i < tagCount; i++) {
            if (i > 0) tags.append(",");
            tags.append(tagArr[rnd.nextInt(tagArr.length)]);
        }
        BigDecimal score = BigDecimal.valueOf(50 + rnd.nextInt(50)).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> features = new HashMap<>();
        features.put("brightness", rnd.nextDouble());
        features.put("saturation", rnd.nextDouble());
        features.put("symmetry", rnd.nextDouble());
        features.put("texture_score", rnd.nextDouble());

        res.put("features", features);
        res.put("tags", tags.toString());
        res.put("score", score);
        return res;
    }

    /** 模拟 AI 对比（生产替换为特征向量比对） */
    private Map<String, Object> mockAiCompare(MhVisualArchive before, MhVisualArchive after) {
        Random rnd = new Random((before.getImageHash() + after.getImageHash()).hashCode());
        BigDecimal similarity = BigDecimal.valueOf(0.5 + rnd.nextDouble() * 0.4).setScale(4, RoundingMode.HALF_UP);
        BigDecimal diff = BigDecimal.valueOf(10 + rnd.nextDouble() * 30).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("spot", 1.5 + rnd.nextDouble() * 5);
        skin.put("pore", 1.5 + rnd.nextDouble() * 5);
        skin.put("wrinkle", 1.5 + rnd.nextDouble() * 5);
        skin.put("brightness", rnd.nextDouble() * 5);

        Map<String, Object> res = new HashMap<>();
        res.put("similarity", similarity);
        res.put("diffScore", diff);
        res.put("skinDiff", skin.toString());
        res.put("improvement", "毛孔细化、色斑淡化、皮肤光泽度提升");
        res.put("regression", "暂无明显退步项");
        res.put("summary", String.format("整体改善 %.1f 分，相似度 %.2f，建议继续当前疗程", diff, similarity));
        return res;
    }
}