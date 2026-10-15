package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BizTreatmentPhase;
import com.meiyetec.entity.BizTreatmentPhaseStep;
import com.meiyetec.entity.MemberTreatmentStepLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizTreatmentPhaseMapper;
import com.meiyetec.mapper.BizTreatmentPhaseStepMapper;
import com.meiyetec.mapper.MemberTreatmentStepLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * R3.0-H P1.4：服务 SOP 步骤化（补强疗程阶段粒度）
 *
 * <p>阶段（phase）粒度太粗，本服务把一个 phase 内的步骤拆解：
 *  - 步骤（step）：一次操作的最小单元（如：清洁、热敷、按摩、面膜）
 *  - 步骤分组（step_group）：同 group 内步骤并行执行（如：左脸按摩 + 右脸面膜同时）
 *  - 不同 group 之间串行（G1 全部完成 → G2 → G3...）
 *
 * <p>典型案例（面部护理 6 次 - 1 次服务）：
 * <pre>
 * G1(并行清洁):  卸妆 + 洁面
 * G2(并行热敷):  热喷机 + 化妆棉热敷
 * G3(并行导入):  超声波左脸 + 超声波右脸
 * G4(并行面膜):  左脸面膜 + 右脸面膜
 * G5(收尾):      头肩按摩
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/biz/treatment/step")
@RequiredArgsConstructor
public class BizTreatmentStepService {

    private final BizTreatmentPhaseStepMapper stepMapper;
    private final MemberTreatmentStepLogMapper stepLogMapper;
    private final BizTreatmentPhaseMapper phaseMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 步骤 CRUD
    // ====================================================================

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<BizTreatmentPhaseStep> createStep(@RequestBody BizTreatmentPhaseStep s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getPhaseId() == null) return Result.error(BizCode.BAD_REQUEST, "phaseId 必填");
        if (s.getStepName() == null || s.getStepName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "stepName 必填");

        // 校验 phase 存在 + 拿 planId
        BizTreatmentPhase phase = phaseMapper.selectById(s.getPhaseId());
        if (phase == null) return Result.error(BizCode.NOT_FOUND, "阶段不存在");
        s.setPlanId(phase.getPlanId());

        if (s.getStepNo() == null) {
            // 自动递增：selectCount 返回 Long
            Long cnt = stepMapper.selectCount(new LambdaQueryWrapper<BizTreatmentPhaseStep>()
                    .eq(BizTreatmentPhaseStep::getPhaseId, s.getPhaseId()));
            s.setStepNo(cnt == null ? 1 : cnt.intValue() + 1);
        }
        if (s.getStepGroup() == null || s.getStepGroup().isEmpty()) s.setStepGroup("G1");
        if (s.getStepType() == null) s.setStepType("SERIAL");
        if (s.getDurationMinutes() == null) s.setDurationMinutes(10);
        if (s.getTenantId() == null) s.setTenantId(tenantId);
        if (s.getSortOrder() == null) s.setSortOrder(s.getStepNo());

        stepMapper.insert(s);
        return Result.success("已创建", s);
    }

    @PutMapping("/{id}")
    public Result<BizTreatmentPhaseStep> updateStep(@PathVariable Long id,
                                                    @RequestBody BizTreatmentPhaseStep s) {
        BizTreatmentPhaseStep old = stepMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "步骤不存在");
        s.setId(id);
        // 不允许换 phase
        if (s.getPhaseId() != null && !s.getPhaseId().equals(old.getPhaseId())) {
            return Result.error(BizCode.CONFLICT, "步骤不能换阶段");
        }
        stepMapper.updateById(s);
        return Result.success("已更新", stepMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteStep(@PathVariable Long id) {
        BizTreatmentPhaseStep old = stepMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "步骤不存在");
        stepMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    @GetMapping("/phase/{phaseId}")
    public Result<List<BizTreatmentPhaseStep>> listByPhase(@PathVariable Long phaseId) {
        return Result.success(stepMapper.selectList(
                new LambdaQueryWrapper<BizTreatmentPhaseStep>()
                        .eq(BizTreatmentPhaseStep::getPhaseId, phaseId)
                        .orderByAsc(BizTreatmentPhaseStep::getSortOrder)));
    }

    // ====================================================================
    // 2. SOP 编排（按 group 分组）
    // ====================================================================

    /**
     * 返回按 group 分组的 SOP 编排（前端直接渲染）
     */
    @GetMapping("/phase/{phaseId}/arrangement")
    public Result<List<Map<String, Object>>> phaseArrangement(@PathVariable Long phaseId) {
        List<BizTreatmentPhaseStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<BizTreatmentPhaseStep>()
                        .eq(BizTreatmentPhaseStep::getPhaseId, phaseId)
                        .orderByAsc(BizTreatmentPhaseStep::getSortOrder));
        Map<String, List<BizTreatmentPhaseStep>> grouped = steps.stream()
                .collect(Collectors.groupingBy(BizTreatmentPhaseStep::getStepGroup,
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<BizTreatmentPhaseStep>> e : grouped.entrySet()) {
            List<BizTreatmentPhaseStep> g = e.getValue();
            Map<String, Object> node = new HashMap<>();
            node.put("group", e.getKey());
            // 并行判据：组内步骤数 > 1 即并行
            node.put("type", g.size() > 1 ? "PARALLEL" : "SERIAL");
            node.put("stepCount", g.size());
            node.put("maxDurationMinutes", g.stream()
                    .mapToInt(BizTreatmentPhaseStep::getDurationMinutes)
                    .max().orElse(0));
            node.put("steps", g);
            result.add(node);
        }
        return Result.success(result);
    }

    // ====================================================================
    // 3. 步骤执行日志
    // ====================================================================

    /**
     * 会员某次到店：完成某步骤
     */
    @PostMapping("/log")
    @Transactional(rollbackFor = Exception.class)
    public Result<MemberTreatmentStepLog> logStep(@RequestBody Map<String, Object> body) {
        Long progressId = body.get("progressId") == null ? null
                : ((Number) body.get("progressId")).longValue();
        Long stepId = body.get("stepId") == null ? null
                : ((Number) body.get("stepId")).longValue();
        if (progressId == null) return Result.error(BizCode.BAD_REQUEST, "progressId 必填");
        if (stepId == null) return Result.error(BizCode.BAD_REQUEST, "stepId 必填");

        BizTreatmentPhaseStep step = stepMapper.selectById(stepId);
        if (step == null) return Result.error(BizCode.NOT_FOUND, "步骤不存在");

        // 同 group 内多步可并发执行：校验不应有未完成的步骤
        Long executingCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_treatment_step_log " +
            "WHERE deleted=0 AND progress_id=? AND step_group=? AND status=1",
            Long.class, progressId, step.getStepGroup());
        if (executingCount != null && executingCount > 0 && !"PARALLEL".equals(step.getStepType())) {
            return Result.error(BizCode.CONFLICT,
                    "同组步骤「" + step.getStepGroup() + "」还有 " + executingCount + " 个未完成");
        }

        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        Long sessionId = body.get("sessionId") == null ? null
                : ((Number) body.get("sessionId")).longValue();

        MemberTreatmentStepLog log = new MemberTreatmentStepLog();
        log.setTenantId(tenantId);
        log.setProgressId(progressId);
        log.setSessionId(sessionId);
        log.setStepId(stepId);
        log.setPhaseId(step.getPhaseId());
        log.setPlanId(step.getPlanId());
        log.setMemberId(memberId);
        log.setStepName(step.getStepName());
        log.setStepGroup(step.getStepGroup());
        log.setDurationMinutes(body.get("durationMinutes") == null
                ? step.getDurationMinutes()
                : ((Number) body.get("durationMinutes")).intValue());
        log.setProductId(step.getProductId());
        log.setProductQty(step.getProductQty());
        log.setOperatorId(body.get("operatorId") == null ? null
                : ((Number) body.get("operatorId")).longValue());
        log.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        log.setRemark((String) body.get("remark"));
        log.setStartedAt(LocalDateTime.now());

        // 状态：若传 finishedAt=true 表示已完成
        boolean finished = Boolean.TRUE.equals(body.get("finished"));
        if (finished) {
            log.setStatus(2);
            log.setFinishedAt(LocalDateTime.now());
        } else {
            log.setStatus(1);
        }
        stepLogMapper.insert(log);
        return Result.success(finished ? "已完成" : "已开始", log);
    }

    @GetMapping("/log/progress/{progressId}")
    public Result<List<MemberTreatmentStepLog>> logsByProgress(@PathVariable Long progressId) {
        return Result.success(stepLogMapper.selectList(
                new LambdaQueryWrapper<MemberTreatmentStepLog>()
                        .eq(MemberTreatmentStepLog::getProgressId, progressId)
                        .orderByDesc(MemberTreatmentStepLog::getStartedAt)));
    }

    /**
     * 完成一组并行步骤中的某个步骤
     */
    @PostMapping("/log/{id}/finish")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> finishLog(@PathVariable Long id) {
        MemberTreatmentStepLog l = stepLogMapper.selectById(id);
        if (l == null) return Result.error(BizCode.NOT_FOUND, "日志不存在");
        if (l.getStatus() != null && l.getStatus() == 2)
            return Result.success("已完成", null);
        jdbc.update(
            "UPDATE member_treatment_step_log SET status=2, finished_at=NOW() WHERE id=?",
            id);
        return Result.success("已完成", null);
    }
}