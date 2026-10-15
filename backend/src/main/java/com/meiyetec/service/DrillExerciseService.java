package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.DrDrillExecution;
import com.meiyetec.entity.DrDrillPlan;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.DrDrillExecutionMapper;
import com.meiyetec.mapper.DrDrillPlanMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.7：灾备演练
 *
 * <p>提供：
 * <ul>
 *   <li>演练计划（4 类：TABLETOP / SIMULATION / PARTIAL_FAILOVER / FULL_FAILOVER）</li>
 *   <li>演练执行：开始 → 完成 → 评分</li>
 *   <li>RTO/RPO 实测</li>
 *   <li>经验沉淀（Lessons Learned）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/dr/drill")
@RequiredArgsConstructor
public class DrillExerciseService {

    private final DrDrillPlanMapper planMapper;
    private final DrDrillExecutionMapper execMapper;

    // ====================================================================
    // 1. 演练计划
    // ====================================================================

    @PostMapping("/plan")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrDrillPlan> createPlan(@RequestBody DrDrillPlan plan) {
        if (plan.getPlanCode() == null) return Result.error(BizCode.BAD_REQUEST, "planCode 必填");
        DrDrillPlan exist = planMapper.selectOne(
                new LambdaQueryWrapper<DrDrillPlan>().eq(DrDrillPlan::getPlanCode, plan.getPlanCode()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "planCode 已存在");
        if (plan.getStatus() == null) plan.setStatus("SCHEDULED");
        planMapper.insert(plan);
        return Result.success("已创建", plan);
    }

    @PostMapping("/plan/{code}/start")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrDrillPlan> startPlan(@PathVariable String code) {
        DrDrillPlan p = planMapper.selectOne(
                new LambdaQueryWrapper<DrDrillPlan>().eq(DrDrillPlan::getPlanCode, code));
        if (p == null) return Result.error(BizCode.NOT_FOUND, "演练计划不存在");
        if (!"SCHEDULED".equals(p.getStatus()))
            return Result.error(BizCode.CONFLICT, "演练不在 SCHEDULED 状态");
        p.setStatus("RUNNING");
        planMapper.updateById(p);

        // 自动创建 execution 记录
        DrDrillExecution ex = new DrDrillExecution();
        ex.setPlanId(p.getId());
        ex.setPlanCode(p.getPlanCode());
        ex.setStartedAt(LocalDateTime.now());
        execMapper.insert(ex);
        return Result.success("已启动", p);
    }

    @PostMapping("/plan/{code}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelPlan(@PathVariable String code) {
        DrDrillPlan p = planMapper.selectOne(
                new LambdaQueryWrapper<DrDrillPlan>().eq(DrDrillPlan::getPlanCode, code));
        if (p == null) return Result.error(BizCode.NOT_FOUND, "演练计划不存在");
        p.setStatus("CANCELLED");
        planMapper.updateById(p);
        return Result.success("已取消", null);
    }

    @GetMapping("/plan/list")
    public Result<List<DrDrillPlan>> listPlans(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<DrDrillPlan> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(DrDrillPlan::getStatus, status);
        qw.orderByDesc(DrDrillPlan::getScheduledAt);
        return Result.success(planMapper.selectList(qw));
    }

    // ====================================================================
    // 2. 演练执行
    // ====================================================================

    @PostMapping("/execution/{id}/complete")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrDrillExecution> completeExecution(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        DrDrillExecution e = execMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "演练执行不存在");
        e.setCompletedAt(LocalDateTime.now());
        e.setSuccess(body.get("success") == null ? 1 : ((Number) body.get("success")).intValue());
        e.setRtoActualSec(body.get("rtoActualSec") == null ? null : ((Number) body.get("rtoActualSec")).intValue());
        e.setRpoActualSec(body.get("rpoActualSec") == null ? null : ((Number) body.get("rpoActualSec")).intValue());
        e.setIssuesFound((String) body.get("issuesFound"));
        e.setLessonsLearned((String) body.get("lessonsLearned"));
        e.setScore(body.get("score") == null ? null : ((Number) body.get("score")).intValue());
        e.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        execMapper.updateById(e);

        // 更新 plan 状态
        DrDrillPlan p = planMapper.selectById(e.getPlanId());
        if (p != null) {
            p.setStatus("COMPLETED");
            planMapper.updateById(p);
        }
        return Result.success("已完成", e);
    }

    @GetMapping("/execution/list")
    public Result<List<DrDrillExecution>> listExecutions(@RequestParam(required = false) String planCode) {
        LambdaQueryWrapper<DrDrillExecution> qw = new LambdaQueryWrapper<>();
        if (planCode != null) qw.eq(DrDrillExecution::getPlanCode, planCode);
        qw.orderByDesc(DrDrillExecution::getId).last("LIMIT 50");
        return Result.success(execMapper.selectList(qw));
    }

    @GetMapping("/execution/stats")
    public Result<Map<String, Object>> executionStats() {
        List<DrDrillExecution> all = execMapper.selectList(null);
        int total = all.size(), success = 0, failed = 0, ongoing = 0;
        double avgScore = 0, avgRto = 0, avgRpo = 0;
        int scoreCount = 0, rtoCount = 0, rpoCount = 0;
        for (DrDrillExecution e : all) {
            if (e.getSuccess() == null) ongoing++;
            else if (e.getSuccess() == 1) success++;
            else failed++;
            if (e.getScore() != null) { avgScore += e.getScore(); scoreCount++; }
            if (e.getRtoActualSec() != null) { avgRto += e.getRtoActualSec(); rtoCount++; }
            if (e.getRpoActualSec() != null) { avgRpo += e.getRpoActualSec(); rpoCount++; }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("success", success);
        data.put("failed", failed);
        data.put("ongoing", ongoing);
        data.put("avgScore", scoreCount > 0 ? avgScore / scoreCount : 0);
        data.put("avgRtoSec", rtoCount > 0 ? avgRto / rtoCount : 0);
        data.put("avgRpoSec", rpoCount > 0 ? avgRpo / rpoCount : 0);
        return Result.success(data);
    }

    // ====================================================================
    // 工具：演练评分
    // ====================================================================

    /**
     * 自动评分 0-100：
     * - 成功 +30
     * - RTO ≤ 300s +20
     * - RPO ≤ 60s +20
     * - 评分时距开始 ≤ 计划时长 +10
     * - 有 Lessons Learned +20
     */
    public static int autoScore(DrDrillExecution e, DrDrillPlan plan) {
        int score = 0;
        if (e.getSuccess() != null && e.getSuccess() == 1) score += 30;
        if (e.getRtoActualSec() != null && e.getRtoActualSec() <= 300) score += 20;
        if (e.getRpoActualSec() != null && e.getRpoActualSec() <= 60) score += 20;
        if (e.getCompletedAt() != null && e.getStartedAt() != null && plan != null && plan.getEstimatedHours() != null) {
            long planSec = (long) (plan.getEstimatedHours().doubleValue() * 3600);
            long elapsed = Duration.between(e.getStartedAt(), e.getCompletedAt()).getSeconds();
            if (elapsed <= planSec) score += 10;
        }
        if (e.getLessonsLearned() != null && !e.getLessonsLearned().isEmpty()) score += 20;
        return Math.min(100, score);
    }
}