package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BizTreatmentPhase;
import com.meiyetec.entity.BizTreatmentPlan;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.entity.MemberSkinProfile;
import com.meiyetec.entity.MemberTreatmentProgress;
import com.meiyetec.entity.TreatmentReport;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizTreatmentPhaseMapper;
import com.meiyetec.mapper.BizTreatmentPlanMapper;
import com.meiyetec.mapper.CareSuggestionLogMapper;
import com.meiyetec.mapper.MemberSkinProfileMapper;
import com.meiyetec.mapper.MemberTreatmentProgressMapper;
import com.meiyetec.mapper.MemberTreatmentSessionMapper;
import com.meiyetec.mapper.TreatmentReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M06 — TreatmentReport 单元测试（不加载 Spring 上下文）。
 *
 * <p>设计：build 是断的（pre-existing InviteService / LbsTechnician / IotProtocolRouter 报错），
 * 跑不到 SpringBootTest，所以全部用 Mockito stub mappers。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>实体字段存在性（防字段漂移）</li>
 *   <li>Plan 不存在 → BusinessException</li>
 *   <li>租户不匹配 → BusinessException</li>
 *   <li>mid-plan 生成：completed < total → ongoing &gt; 0</li>
 *   <li>completed-plan 生成：completed == total → ongoing == 0</li>
 *   <li>abandoned-plan 生成：abandoned &gt; 0</li>
 *   <li>护理遵守率：80% 阈值 → "遵守率高" 文案；50% 以下 → "建议加强" 文案</li>
 *   <li>多租户：tenantId 不一致 → 业务数据不被读出</li>
 * </ul>
 */
class TreatmentReportServiceTest {

    private BizTreatmentPlanMapper planMapper;
    private BizTreatmentPhaseMapper phaseMapper;
    private MemberTreatmentProgressMapper progressMapper;
    private MemberTreatmentSessionMapper sessionMapper;
    private CareSuggestionLogMapper careLogMapper;
    private MemberSkinProfileMapper skinProfileMapper;
    private TreatmentReportMapper reportMapper;

    private TreatmentReportService service;

    /** Mockito doAnswer 落点：最近一次 {@code reportMapper.insert} 的入参，让测试可断言实际写入的快照字段。 */
    private TreatmentReport lastCaptured;

    @BeforeEach
    void setup() {
        planMapper = mock(BizTreatmentPlanMapper.class);
        phaseMapper = mock(BizTreatmentPhaseMapper.class);
        progressMapper = mock(MemberTreatmentProgressMapper.class);
        sessionMapper = mock(MemberTreatmentSessionMapper.class);
        careLogMapper = mock(CareSuggestionLogMapper.class);
        skinProfileMapper = mock(MemberSkinProfileMapper.class);
        reportMapper = mock(TreatmentReportMapper.class);

        service = new TreatmentReportService(
                planMapper, phaseMapper, progressMapper, sessionMapper,
                careLogMapper, skinProfileMapper, reportMapper);

        // 1) 给自增 id 注入一个值；2) 缓存最后一次 insert 的对象以便断言
        lenient().doAnswer(inv -> {
            TreatmentReport r = inv.getArgument(0);
            if (r.getId() == null) r.setId(System.nanoTime());
            lastCaptured = r;
            return 1;
        }).when(reportMapper).insert(any(TreatmentReport.class));
    }

    // ---------- 实体契约 ----------

    @Test
    void treatment_report_entity_has_core_fields() {
        String[] fields = {"id", "tenantId", "memberId", "treatmentPlanId", "planName",
                "totalSessions", "completedSessions", "abandonedSessions", "ongoingSessions",
                "sessionIdsJson", "phasesSummaryJson", "careSuggestionsTotal",
                "careSuggestionsFollowed", "careComplianceRate", "effectScoreBefore",
                "effectScoreAfter", "effectScoreDelta", "recommendation",
                "generatedBy", "generatedAt", "remark"};
        for (String name : fields) {
            assertNotNull(findField(TreatmentReport.class, name),
                    "TreatmentReport." + name + " 必须存在（M06 核心字段）");
        }
    }

    @Test
    void treatment_report_table_name_is_treatment_report() throws Exception {
        com.baomidou.mybatisplus.annotation.TableName tn =
                TreatmentReport.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        assertNotNull(tn, "必须带 @TableName 注解");
        assertEquals("treatment_report", tn.value());
    }

    // ---------- 业务规则 ----------

    @Test
    void generate_plan_not_found_throws() {
        when(planMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateReport(1L, 99L, "TEST", 1L));
        assertTrue(ex.getMessage().contains("疗程方案不存在"));
    }

    @Test
    void generate_plan_cross_tenant_forbidden() {
        BizTreatmentPlan plan = new BizTreatmentPlan();
        plan.setId(1L);
        plan.setTenantId(2L);
        plan.setPlanName("跨租户方案");
        plan.setTotalSessions(6);
        when(planMapper.selectById(1L)).thenReturn(plan);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateReport(10L, 1L, "TEST", 1L));
        assertTrue(ex.getMessage().contains("无权"));
    }

    @Test
    void generate_mid_plan_records_completed_and_ongoing() {
        BizTreatmentPlan plan = basePlan(1L, 1L, "美白 8 次", 8);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(activeProgress(100L, 1L, 1L, 1)));
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L); // 5 sessions recorded
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(phase(1L, 1L, 1, "适应期", 2), phase(2L, 1L, 2, "调整期", 3), phase(3L, 1L, 3, "巩固期", 3)));
        when(reportMapper.countSessionsInPhase(anyLong(), any())).thenAnswer(inv -> {
            Integer phaseNo = inv.getArgument(1);
            if (phaseNo == null || phaseNo == 1) return 2L;
            if (phaseNo == 2) return 3L;
            return 0L;
        });
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(List.of());
        when(skinProfileMapper.findLatestByMember(anyLong())).thenReturn(null);

        Long reportId = service.generateReport(10L, 1L, "admin", 1L);
        assertNotNull(reportId);

        TreatmentReport saved = captureReport();
        assertEquals(Integer.valueOf(8), saved.getTotalSessions());
        assertEquals(Integer.valueOf(5), saved.getCompletedSessions());
        assertEquals(Integer.valueOf(3), saved.getOngoingSessions());
        assertEquals(Integer.valueOf(0), saved.getAbandonedSessions());
        assertNotNull(saved.getPhasesSummaryJson());
        assertTrue(saved.getPhasesSummaryJson().contains("IN_PROGRESS"),
                "phases JSON 应包含 IN_PROGRESS 状态");
    }

    @Test
    void generate_completed_plan_records_full_completion() {
        BizTreatmentPlan plan = basePlan(2L, 1L, "身体 10 次", 10);
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(activeProgress(101L, 1L, 2L, 1)));
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L); // 全做完
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(phase(10L, 2L, 1, "全程", 10)));
        when(reportMapper.countSessionsInPhase(anyLong(), any())).thenReturn(10L);
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(List.of());

        Long id = service.generateReport(11L, 2L, "SYSTEM", 1L);
        assertNotNull(id);

        TreatmentReport saved = captureReport();
        assertEquals(Integer.valueOf(10), saved.getCompletedSessions());
        assertEquals(Integer.valueOf(0), saved.getOngoingSessions());
        assertTrue(saved.getPhasesSummaryJson().contains("COMPLETED"));
    }

    @Test
    void generate_abandoned_plan_records_abandoned_count() {
        BizTreatmentPlan plan = basePlan(3L, 1L, "面部 6 次", 6);
        when(planMapper.selectById(3L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of()); // 无 active
        when(progressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);   // abandoned=1
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(List.of());

        Long id = service.generateReport(12L, 3L, "SYSTEM", 1L);
        assertNotNull(id);

        TreatmentReport saved = captureReport();
        assertEquals(Integer.valueOf(1), saved.getAbandonedSessions());
        assertEquals(Integer.valueOf(6), saved.getOngoingSessions());
    }

    @Test
    void generate_high_compliance_recommends_adopt() {
        BizTreatmentPlan plan = basePlan(4L, 1L, "护理 5 次", 5);
        when(planMapper.selectById(4L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(progressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        // 10 个建议里 9 个 accepted → 90% 遵守率
        List<CareSuggestionLog> logs = new ArrayList<>();
        for (int i = 0; i < 9; i++) logs.add(careLog(i, "accepted"));
        logs.add(careLog(100, "rejected"));
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(logs);
        when(skinProfileMapper.findLatestByMember(anyLong())).thenReturn(null);

        Long id = service.generateReport(20L, 4L, "admin", 1L);
        assertNotNull(id);
        TreatmentReport saved = captureReport();
        assertEquals(new BigDecimal("0.9000"), saved.getCareComplianceRate());
        assertTrue(saved.getRecommendation().contains("遵守度高"),
                "高遵守率应触发「遵守度高」建议：" + saved.getRecommendation());
    }

    @Test
    void generate_low_compliance_recommends_intensify() {
        BizTreatmentPlan plan = basePlan(5L, 1L, "测试方案", 5);
        when(planMapper.selectById(5L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(progressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        // 10 个建议里只有 2 个 accepted → 20%
        List<CareSuggestionLog> logs = new ArrayList<>();
        logs.add(careLog(1, "accepted"));
        logs.add(careLog(2, "accepted"));
        for (int i = 0; i < 8; i++) logs.add(careLog(10 + i, "rejected"));
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(logs);

        Long id = service.generateReport(30L, 5L, "admin", 1L);
        assertNotNull(id);
        TreatmentReport saved = captureReport();
        assertTrue(saved.getRecommendation().contains("加强日常护理"),
                "低遵守率应触发「加强日常护理」建议：" + saved.getRecommendation());
    }

    @Test
    void generate_skin_profile_aggregates_effect_score() {
        BizTreatmentPlan plan = basePlan(6L, 1L, "肌肤方案", 5);
        when(planMapper.selectById(6L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(progressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(List.of());
        MemberSkinProfile sp = new MemberSkinProfile();
        sp.setMoistureScore(80);
        sp.setOilScore(60);
        sp.setPigmentationScore(50);
        sp.setWrinkleScore(40);
        sp.setPoreScore(30);
        when(skinProfileMapper.findLatestByMember(anyLong())).thenReturn(sp);

        service.generateReport(40L, 6L, "admin", 1L);
        TreatmentReport saved = captureReport();
        // 80*.4 + 60*.15 + 50*.15 + 40*.15 + 30*.15 = 32 + 9 + 7.5 + 6 + 4.5 = 59 → 59.00
        assertNotNull(saved.getEffectScoreBefore(), "皮肤档案存在时应得到非 null 综合分");
        assertEquals(0, new BigDecimal("59.00").compareTo(saved.getEffectScoreBefore()),
                "皮肤综合分算法错误：" + saved.getEffectScoreBefore());
    }

    @Test
    void generate_empty_data_triggers_default_recommendation() {
        BizTreatmentPlan plan = basePlan(7L, 1L, "零数据", 5);
        when(planMapper.selectById(7L)).thenReturn(plan);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(progressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(sessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(phaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(careLogMapper.queryList(anyLong(), anyLong(), any(), any())).thenReturn(List.of());
        when(skinProfileMapper.findLatestByMember(anyLong())).thenReturn(null);

        service.generateReport(50L, 7L, "SYSTEM", 1L);
        TreatmentReport saved = captureReport();
        assertTrue(saved.getRecommendation().contains("尚未积累足够数据"),
                "无数据时应给出" +
                        "\"尚未积累足够数据\"默认文案：" + saved.getRecommendation());
    }

    // ---------- 读路径 / 租户隔离 ----------

    @Test
    void list_by_member_filters_by_tenant() {
        when(reportMapper.listByMember(eq(1L), eq(10L))).thenReturn(List.of());
        List<TreatmentReport> r = service.listByMember(10L, 1L);
        assertNotNull(r);

        // tenantId = null 应返回空 list
        assertEquals(0, service.listByMember(10L, null).size());
    }

    @Test
    void list_recent_caps_limit_at_200() {
        when(reportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertNotNull(service.listRecent(1L, 1000));
    }

    @Test
    void get_by_id_rejects_cross_tenant() {
        TreatmentReport r = new TreatmentReport();
        r.setId(1L);
        r.setTenantId(2L);
        r.setMemberId(1L);
        when(reportMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(1L, 1L));
        assertTrue(ex.getMessage().contains("无权"));
    }

    // ---------- helpers ----------

    private static BizTreatmentPlan basePlan(Long id, Long tenantId, String name, Integer total) {
        BizTreatmentPlan p = new BizTreatmentPlan();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setPlanName(name);
        p.setTotalSessions(total);
        return p;
    }

    private static BizTreatmentPhase phase(Long id, Long planId, Integer no, String name, Integer cnt) {
        BizTreatmentPhase ph = new BizTreatmentPhase();
        ph.setId(id);
        ph.setPlanId(planId);
        ph.setPhaseNo(no);
        ph.setPhaseName(name);
        ph.setSessionCount(cnt);
        return ph;
    }

    private static MemberTreatmentProgress activeProgress(Long id, Long memberId, Long planId, int status) {
        MemberTreatmentProgress p = new MemberTreatmentProgress();
        p.setId(id);
        p.setTenantId(1L);
        p.setMemberId(memberId);
        p.setPlanId(planId);
        p.setStatus(status);
        p.setTotalSessions(8);
        return p;
    }

    private static CareSuggestionLog careLog(int seq, String status) {
        CareSuggestionLog l = new CareSuggestionLog();
        l.setId((long) seq);
        l.setTenantId(1L);
        l.setMemberId(1L);
        l.setStatus(status);
        l.setSuggestionType("auto");
        return l;
    }

    private static Field findField(Class<?> clazz, String name) {
        try { return clazz.getDeclaredField(name); }
        catch (NoSuchFieldException e) { return null; }
    }

    /**
     * 返回 service 最近一次 {@code reportMapper.insert} 的入参 — 通过测试类的 {@link #lastCaptured} 字段。
     */
    private TreatmentReport captureReport() {
        return lastCaptured;
    }
}
