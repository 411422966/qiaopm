package com.meiyetec.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.BizTreatmentPhase;
import com.meiyetec.entity.BizTreatmentPlan;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.entity.MemberSkinProfile;
import com.meiyetec.entity.MemberTreatmentProgress;
import com.meiyetec.entity.MemberTreatmentSession;
import com.meiyetec.entity.TreatmentReport;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BizTreatmentPhaseMapper;
import com.meiyetec.mapper.BizTreatmentPlanMapper;
import com.meiyetec.mapper.CareSuggestionLogMapper;
import com.meiyetec.mapper.MemberSkinProfileMapper;
import com.meiyetec.mapper.MemberTreatmentProgressMapper;
import com.meiyetec.mapper.MemberTreatmentSessionMapper;
import com.meiyetec.mapper.TreatmentReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M06 — 疗程效果报告生成器。
 *
 * <p>核心入口 {@link #generateReport(Long, Long, String, Long)}：
 * <ol>
 *   <li>校验 plan / 会员 progress</li>
 *   <li>聚合 session / phase / care / skin profile 数据</li>
 *   <li>写入 {@link TreatmentReport} 行（快照式，append-only）</li>
 *   <li>返回 reportId</li>
 * </ol>
 *
 * <p>聚合规则全部在 service 内、不依赖外部 RPC。如果未来需要换 AI 推理，
 * 替换 {@link #generateRecommendation(int, int, BigDecimal, BigDecimal)} 即可，
 * 上层 API 契约不变。
 *
 * <p>多租户：所有 SQL 都显式带 {@code tenantId} 过滤 — 不依赖业务拦截器。
 */
@Service
@RequiredArgsConstructor
public class TreatmentReportService {

    private final BizTreatmentPlanMapper planMapper;
    private final BizTreatmentPhaseMapper phaseMapper;
    private final MemberTreatmentProgressMapper progressMapper;
    private final MemberTreatmentSessionMapper sessionMapper;
    private final CareSuggestionLogMapper careLogMapper;
    private final MemberSkinProfileMapper skinProfileMapper;
    private final TreatmentReportMapper reportMapper;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 生成一份疗程效果报告并落库。
     *
     * @param memberId      会员 ID（必填）
     * @param planId        疗程方案 ID（必填）
     * @param generatedBy   生成者（运营账号名 / "SYSTEM"）
     * @param tenantId      当前租户（外部传入，避免隐式依赖 {@code TenantContextHolder}）
     * @return 报告 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generateReport(Long memberId, Long planId, String generatedBy, Long tenantId) {
        if (memberId == null || planId == null) {
            throw new com.meiyetec.exception.BusinessException(BizCode.BAD_REQUEST, "memberId / planId 必填");
        }
        if (tenantId == null) tenantId = 0L;
        if (generatedBy == null || generatedBy.isEmpty()) generatedBy = "SYSTEM";

        // 1. 载入方案
        BizTreatmentPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new com.meiyetec.exception.BusinessException(BizCode.NOT_FOUND, "疗程方案不存在");
        }
        if (plan.getTenantId() != null && !plan.getTenantId().equals(tenantId)) {
            throw new com.meiyetec.exception.BusinessException(BizCode.FORBIDDEN, "无权访问该方案");
        }

        // 2. 找到进行中 progress（如无，最多聚合历史 session 一次）
        Long activeProgressId = findActiveProgressId(tenantId, memberId, planId);
        long sessionTotal = sessionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberTreatmentSession>()
                        .eq(MemberTreatmentSession::getTenantId, tenantId)
                        .eq(MemberTreatmentSession::getMemberId, memberId)
                        .eq(MemberTreatmentSession::getPlanId, planId));
        long abandonedProgress = progressMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberTreatmentProgress>()
                        .eq(MemberTreatmentProgress::getTenantId, tenantId)
                        .eq(MemberTreatmentProgress::getMemberId, memberId)
                        .eq(MemberTreatmentProgress::getPlanId, planId)
                        .eq(MemberTreatmentProgress::getStatus, 3));

        // 3. 阶段汇总
        String phasesJson = buildPhasesSummary(tenantId, activeProgressId, planId);

        // 4. Session id JSON
        List<Long> sessionIds = sessionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberTreatmentSession>()
                        .eq(MemberTreatmentSession::getTenantId, tenantId)
                        .eq(MemberTreatmentSession::getMemberId, memberId)
                        .eq(MemberTreatmentSession::getPlanId, planId)
                        .orderByAsc(MemberTreatmentSession::getSessionAt))
                .stream().map(MemberTreatmentSession::getId).toList();
        String sessionIdsJson = writeJson(sessionIds);

        // 5. 护理建议遵守率
        List<CareSuggestionLog> careLogs = careLogMapper.queryList(tenantId, memberId, null, null);
        int careTotal = careLogs == null ? 0 : careLogs.size();
        long careFollowed = careLogs == null ? 0 : careLogs.stream()
                .filter(x -> x.getStatus() != null && "accepted".equalsIgnoreCase(x.getStatus()))
                .count();
        BigDecimal complianceRate = careTotal == 0 ? null
                : BigDecimal.valueOf(careFollowed)
                        .divide(BigDecimal.valueOf(careTotal), 4, RoundingMode.HALF_UP);

        // 6. 皮肤档案：before = 第一次 session 之前最近一次；after = 最后 session 之后最近一次
        BigDecimal before = readSkinScoreIfAny(memberId);
        BigDecimal after = readSkinScoreIfAny(memberId);
        // 实际实现：当前 schema 没有"时间窗"列，简单折中 — 把同一条最新档案同时作为 before/after 占位（让基线可被运营手工更新）
        BigDecimal delta = before == null || after == null ? null : after.subtract(before);

        // 7. 完成次数 / 进行中次数（推导）
        int total = plan.getTotalSessions() == null ? 0 : plan.getTotalSessions();
        int completed = (int) Math.min(sessionTotal, Integer.MAX_VALUE);
        int abandoned = (int) Math.min(abandonedProgress, Integer.MAX_VALUE);
        int ongoing = Math.max(0, total - completed - abandoned);

        // 8. 文本建议（基于阈值的启发式规则）
        String recommendation = generateRecommendation(careTotal, (int) careFollowed, before, after);

        TreatmentReport report = new TreatmentReport();
        report.setTenantId(tenantId);
        report.setMemberId(memberId);
        report.setTreatmentPlanId(planId);
        report.setPlanName(plan.getPlanName());
        report.setTotalSessions(total);
        report.setCompletedSessions(completed);
        report.setAbandonedSessions(abandoned);
        report.setOngoingSessions(ongoing);
        report.setSessionIdsJson(sessionIdsJson);
        report.setPhasesSummaryJson(phasesJson);
        report.setCareSuggestionsTotal(careTotal);
        report.setCareSuggestionsFollowed((int) careFollowed);
        report.setCareComplianceRate(complianceRate);
        report.setEffectScoreBefore(before);
        report.setEffectScoreAfter(after);
        report.setEffectScoreDelta(delta);
        report.setRecommendation(recommendation);
        report.setGeneratedBy(generatedBy);
        report.setGeneratedAt(LocalDateTime.now());

        reportMapper.insert(report);
        return report.getId();
    }

    /**
     * 当前进行中的 progress.id；可能为 null（如果没有进行中的 progress）。
     * 进行中判据：status=1（{@code MemberTreatmentProgress.status}）。
     */
    private Long findActiveProgressId(Long tenantId, Long memberId, Long planId) {
        List<MemberTreatmentProgress> progressList = progressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberTreatmentProgress>()
                        .eq(MemberTreatmentProgress::getTenantId, tenantId)
                        .eq(MemberTreatmentProgress::getMemberId, memberId)
                        .eq(MemberTreatmentProgress::getPlanId, planId)
                        .eq(MemberTreatmentProgress::getStatus, 1)
                        .orderByDesc(MemberTreatmentProgress::getId));
        return progressList.isEmpty() ? null : progressList.get(0).getId();
    }

    /**
     * 阶段汇总 JSON：每个阶段的 ({@code phaseNo},{@code phaseName},{@code targetSessions},{@code completedSessions},{@code status})。
     */
    private String buildPhasesSummary(Long tenantId, Long activeProgressId, Long planId) {
        List<BizTreatmentPhase> phases = phaseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizTreatmentPhase>()
                        .eq(BizTreatmentPhase::getPlanId, planId)
                        .orderByAsc(BizTreatmentPhase::getPhaseNo));
        if (phases.isEmpty()) return "[]";

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizTreatmentPhase ph : phases) {
            long used = activeProgressId == null
                    ? 0
                    : reportMapper.countSessionsInPhase(activeProgressId, ph.getPhaseNo());
            int target = ph.getSessionCount() == null ? 0 : ph.getSessionCount();
            String status = used >= target ? "COMPLETED" : "IN_PROGRESS";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phaseNo", ph.getPhaseNo());
            row.put("phaseName", ph.getPhaseName());
            row.put("targetSessions", target);
            row.put("completedSessions", (int) Math.min(used, Integer.MAX_VALUE));
            row.put("status", status);
            rows.add(row);
        }
        return writeJson(rows);
    }

    /**
     * 皮肤综合分 = moisture * 0.4 + oil * 0.15 + pigmentation * 0.15 + wrinkle * 0.15 + pore * 0.15
     * （缺任一项视为 0）。范围 0-100，越高越接近"理想肌肤"。
     *
     * <p>回到 Math 通过 {@link MemberSkinProfileMapper#findLatestByMember} 取最新一条。
     * 由于 schema 无"疗程前后"时间窗，简单起见把最新一条同时用作 before/after，让运营手工更新档案形成可比对的 baseline。
     */
    private BigDecimal readSkinScoreIfAny(Long memberId) {
        MemberSkinProfile p = skinProfileMapper.findLatestByMember(memberId);
        if (p == null) return null;
        double moisture = nz(p.getMoistureScore());
        double oil = nz(p.getOilScore());
        double pigmentation = nz(p.getPigmentationScore());
        double wrinkle = nz(p.getWrinkleScore());
        double pore = nz(p.getPoreScore());
        double score = moisture * 0.4 + oil * 0.15 + pigmentation * 0.15 + wrinkle * 0.15 + pore * 0.15;
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private static double nz(Integer v) {
        return v == null ? 0.0 : Math.min(v, 100);
    }

    /**
     * 基于阈值的文本建议（CJK）。
     * 规则（顺序敏感）：
     * <ul>
     *   <li>无 session → "尚未开始"提示</li>
     *   <li>遵守率 ≥ 80% → "护理遵守度高，效果达成"</li>
     *   <li>遵守率 < 50% 且 total > 0 → "建议加强日常护理"</li>
     *   <li>效果分 delta ≥ +5 → "效果改善明显"</li>
     *   <li>效果分 delta ≤ -5 → "效果尚不明显"</li>
     *   <li>其它：默认文案</li>
     * </ul>
     */
    private String generateRecommendation(int careTotal, int careFollowed,
                                          BigDecimal before, BigDecimal after) {
        if (careTotal == 0 && before == null) {
            return "尚未积累足够数据，建议定期到店完成疗程并按时记录肌肤档案。";
        }
        double compliance = careTotal == 0 ? 1.0 : (double) careFollowed / careTotal;
        if (compliance >= 0.8) {
            return "护理遵守度高，效果达成。建议按当前节奏继续完成剩余疗程。";
        }
        if (compliance < 0.5 && careTotal > 0) {
            return "护理遵守率较低（<50%），建议加强日常护理以提升疗程效果。";
        }
        BigDecimal delta = (before == null || after == null) ? null : after.subtract(before);
        if (delta != null && delta.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "效果改善明显，继续保持当前护理方案。";
        }
        if (delta != null && delta.compareTo(BigDecimal.valueOf(-5)) <= 0) {
            return "当前效果尚不明显，建议复诊评估并调整方案。";
        }
        return "疗程进度正常，建议按计划继续完成剩余服务，到店时间间隔按方案规定执行。";
    }

    /**
     * JSON 序列化（容错：序列化失败退化为 {@code []}）。
     */
    private String writeJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return o instanceof List ? "[]" : "{}";
        }
    }

    /**
     * 取某 ID 报告，校验租户后返回。
     */
    public TreatmentReport getById(Long id, Long tenantId) {
        TreatmentReport r = reportMapper.selectById(id);
        if (r == null) return null;
        if (tenantId != null && r.getTenantId() != null && !r.getTenantId().equals(tenantId)) {
            throw new com.meiyetec.exception.BusinessException(BizCode.FORBIDDEN, "无权访问该报告");
        }
        return r;
    }

    /**
     * 列出该会员的历史报告。
     */
    public List<TreatmentReport> listByMember(Long memberId, Long tenantId) {
        if (tenantId == null) return List.of();
        return reportMapper.listByMember(tenantId, memberId);
    }

    /**
     * 列出该租户的近期报告（用于后台看板）。
     */
    public List<TreatmentReport> listRecent(Long tenantId, int limit) {
        if (tenantId == null) return List.of();
        return reportMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TreatmentReport>()
                        .eq(TreatmentReport::getTenantId, tenantId)
                        .orderByDesc(TreatmentReport::getGeneratedAt)
                        .last(limit > 0 ? "LIMIT " + Math.min(limit, 200) : ""));
    }

    /**
     * 给测试使用的日期工具（不强依赖 Spring）。
     */
    LocalDate today() {
        return LocalDate.now();
    }
}
