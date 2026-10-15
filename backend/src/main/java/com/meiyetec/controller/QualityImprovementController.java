package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmServiceImprovement;
import com.meiyetec.entity.CrmServiceImprovementLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CrmServiceImprovementLogMapper;
import com.meiyetec.mapper.CrmServiceImprovementMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M9 质检管理 — 服务改进任务 CRUD（/quality/improvement/*）
 *
 * <p>来源：评价驱动的服务改进任务闭环
 * <ul>
 *   <li>REVIEW — 差评自动触发</li>
 *   <li>COMPLAINT — 投诉工单转化</li>
 *   <li>VOICE_OF_CUSTOMER — 客户声音</li>
 *   <li>INTERNAL — 内部审计</li>
 * </ul>
 */
@RestController
@RequestMapping("/quality/improvement")
@RequiredArgsConstructor
@SysOpLog(module = "质检管理")
public class QualityImprovementController {

    private final CrmServiceImprovementMapper improvementMapper;
    private final CrmServiceImprovementLogMapper logMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    // ====================================================================
    // 1. 改进任务 CRUD
    // ====================================================================

    @GetMapping("/list")
    public Result<IPage<CrmServiceImprovement>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String dimensionCode) {
        LambdaQueryWrapper<CrmServiceImprovement> q = new LambdaQueryWrapper<>();
        q.eq(CrmServiceImprovement::getTenantId, tid());
        if (storeId != null) q.eq(CrmServiceImprovement::getStoreId, storeId);
        if (status != null) q.eq(CrmServiceImprovement::getStatus, status);
        if (priority != null) q.eq(CrmServiceImprovement::getPriority, priority);
        if (sourceType != null) q.eq(CrmServiceImprovement::getSourceType, sourceType);
        if (dimensionCode != null) q.eq(CrmServiceImprovement::getDimensionCode, dimensionCode);
        q.orderByDesc(CrmServiceImprovement::getId);
        Page<CrmServiceImprovement> page = new Page<>(pageNum, pageSize);
        IPage<CrmServiceImprovement> result = improvementMapper.selectPage(page, q);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<CrmServiceImprovement> get(@PathVariable Long id) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        return Result.success(item);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> create(@RequestBody CrmServiceImprovement item) {
        item.setId(null);
        item.setTenantId(tid());
        if (item.getStatus() == null) item.setStatus(1);
        if (item.getImprovementNo() == null) {
            item.setImprovementNo("QI-" + System.currentTimeMillis());
        }
        if (item.getOperatorName() == null) {
            item.setOperatorName("system");
        }
        improvementMapper.insert(item);
        logAction(item.getId(), null, 1, "创建改进任务", null);
        return Result.success("已创建", item);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> update(@PathVariable Long id, @RequestBody CrmServiceImprovement patch) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        Integer oldStatus = item.getStatus();

        if (patch.getTitle() != null) item.setTitle(patch.getTitle());
        if (patch.getProblem() != null) item.setProblem(patch.getProblem());
        if (patch.getRootCause() != null) item.setRootCause(patch.getRootCause());
        if (patch.getActionPlan() != null) item.setActionPlan(patch.getActionPlan());
        if (patch.getExpectedEffect() != null) item.setExpectedEffect(patch.getExpectedEffect());
        if (patch.getOwnerId() != null) item.setOwnerId(patch.getOwnerId());
        if (patch.getOwnerName() != null) item.setOwnerName(patch.getOwnerName());
        if (patch.getPriority() != null) item.setPriority(patch.getPriority());
        if (patch.getDeadline() != null) item.setDeadline(patch.getDeadline());
        if (patch.getTargetScore() != null) item.setTargetScore(patch.getTargetScore());
        if (patch.getStatus() != null) {
            item.setStatus(patch.getStatus());
            if (patch.getStatus() == 4) { // 已完成
                item.setCompletedAt(LocalDateTime.now());
            }
        }
        improvementMapper.updateById(item);
        if (!oldStatus.equals(item.getStatus())) {
            logAction(id, oldStatus, item.getStatus(), "状态变更", null);
        }
        return Result.success("已更新", item);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        improvementMapper.deleteById(id);
        logMapper.delete(new LambdaQueryWrapper<CrmServiceImprovementLog>()
                .eq(CrmServiceImprovementLog::getImprovementId, id));
        return Result.success("已删除", null);
    }

    // ====================================================================
    // 2. 状态流转
    // ====================================================================

    @PostMapping("/{id}/start")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> start(@PathVariable Long id) {
        return changeStatus(id, 1, 3, "开始执行");
    }

    @PostMapping("/{id}/complete")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> complete(
            @PathVariable Long id,
            @RequestBody(required = false) CrmServiceImprovement body) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        if (body != null) {
            if (body.getPostScore() != null) item.setPostScore(body.getPostScore());
            if (body.getEffectNote() != null) item.setEffectNote(body.getEffectNote());
        }
        item.setStatus(4);
        item.setCompletedAt(LocalDateTime.now());
        improvementMapper.updateById(item);
        logAction(id, 3, 4, "任务完成", null);
        return Result.success("已完成", item);
    }

    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmServiceImprovement> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) String reason) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        Integer old = item.getStatus();
        item.setStatus(5);
        improvementMapper.updateById(item);
        logAction(id, old, 5, "取消任务: " + (reason != null ? reason : ""), null);
        return Result.success("已取消", item);
    }

    // ====================================================================
    // 3. 改进日志
    // ====================================================================

    @GetMapping("/{id}/log")
    public Result<List<CrmServiceImprovementLog>> logs(@PathVariable Long id) {
        return Result.success(logMapper.selectList(
                new LambdaQueryWrapper<CrmServiceImprovementLog>()
                        .eq(CrmServiceImprovementLog::getImprovementId, id)
                        .orderByAsc(CrmServiceImprovementLog::getCreateTime)));
    }

    // ====================================================================
    // 4. 仪表盘
    // ====================================================================

    @GetMapping("/dashboard")
    public Result<Object> dashboard() {
        Long t = tid();
        long total = improvementMapper.selectCount(
                new LambdaQueryWrapper<CrmServiceImprovement>().eq(CrmServiceImprovement::getTenantId, t));
        long pending = improvementMapper.selectCount(
                new LambdaQueryWrapper<CrmServiceImprovement>()
                        .eq(CrmServiceImprovement::getTenantId, t)
                        .in(CrmServiceImprovement::getStatus, 1, 2));
        long inProgress = improvementMapper.selectCount(
                new LambdaQueryWrapper<CrmServiceImprovement>()
                        .eq(CrmServiceImprovement::getTenantId, t)
                        .eq(CrmServiceImprovement::getStatus, 3));
        long completed = improvementMapper.selectCount(
                new LambdaQueryWrapper<CrmServiceImprovement>()
                        .eq(CrmServiceImprovement::getTenantId, t)
                        .eq(CrmServiceImprovement::getStatus, 4));
        return Result.success(java.util.Map.of(
                "total", total,
                "pending", pending,
                "inProgress", inProgress,
                "completed", completed,
                "completionRate", total > 0 ? Math.round(completed * 100.0 / total) : 0
        ));
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private Result<CrmServiceImprovement> changeStatus(Long id, int from, int to, String reason) {
        CrmServiceImprovement item = improvementMapper.selectById(id);
        if (item == null || !tid().equals(item.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "改进任务不存在");
        }
        if (item.getStatus() != from) {
            return Result.error(BizCode.BAD_REQUEST, "当前状态不允许此操作");
        }
        item.setStatus(to);
        if (to == 3) item.setStartedAt(LocalDateTime.now());
        if (to == 4) item.setCompletedAt(LocalDateTime.now());
        improvementMapper.updateById(item);
        logAction(id, from, to, reason, null);
        return Result.success("状态已更新", item);
    }

    private void logAction(Long improvementId, Integer fromStatus, Integer toStatus, String content, Long operatorId) {
        CrmServiceImprovementLog log = new CrmServiceImprovementLog();
        log.setTenantId(tid());
        log.setImprovementId(improvementId);
        log.setActionType(toStatus == 1 ? "CREATE" : toStatus == 4 ? "COMPLETE" : "TRANSIT");
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setContent(content);
        log.setOperatorId(operatorId);
        log.setOperatorName("system");
        logMapper.insert(log);
    }
}
