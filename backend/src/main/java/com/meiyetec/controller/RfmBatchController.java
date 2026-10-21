package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.RfmBatchExecutor;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RFM 批量 / 种子任务控制端点（异步 + 实时进度）。
 *
 * 端点：
 *   POST /intel/rfm/batch/run         启动批量计算（异步），返回 taskId
 *   POST /intel/rfm/seed/run          启动种子数据生成（异步），返回 taskId
 *   POST /intel/rfm/batch/cancel/{id} 取消任务
 *   GET  /intel/rfm/batch/progress/{id} 查询实时进度
 *
 * 进度刷新粒度由 RfmBatchExecutor.PROGRESS_FLUSH_EVERY = 50 控制（Q4）。
 */
@Slf4j
@RestController
@RequestMapping("/intel/rfm")
@RequiredArgsConstructor
public class RfmBatchController {

    private final RfmBatchExecutor executor;
    private final JdbcTemplate jdbc;

    // ----------------------------------------------------------------
    // 启动批量
    // ----------------------------------------------------------------
    @PostMapping("/batch/run")
    @RequirePermission("report:rfm:batch:run")
    public Result<Map<String, Object>> runBatch(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.BAD_REQUEST, "未识别租户");

        Long brandId = body == null || body.get("brandId") == null ? null
                : ((Number) body.get("brandId")).longValue();
        int limit = body == null || body.get("limit") == null ? 5000
                : ((Number) body.get("limit")).intValue();
        String operator = body == null ? null : (String) body.get("operatorName");

        long taskId = executor.createTask(tenantId, "BATCH", brandId, operator);
        executor.runBatch(taskId, tenantId, brandId, limit, operator);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "BATCH");
        data.put("limit", limit);
        return Result.success("批量任务已启动", data);
    }

    // ----------------------------------------------------------------
    // 启动种子数据生成（仅异步 RFM 重算；造会员/订单的同步 INSERT 由 /seed 端点处理，见 RfmSeedController）
    // ----------------------------------------------------------------
    @PostMapping("/seed/run")
    @RequirePermission("report:rfm:batch:run")
    public Result<Map<String, Object>> runSeed(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.BAD_REQUEST, "未识别租户");

        int limit = body == null || body.get("limit") == null ? 5000
                : ((Number) body.get("limit")).intValue();
        String operator = body == null ? null : (String) body.get("operatorName");

        long taskId = executor.createTask(tenantId, "SEED", null, operator);
        executor.runSeed(taskId, tenantId, limit, operator);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "SEED");
        data.put("limit", limit);
        return Result.success("种子重算任务已启动", data);
    }

    // ----------------------------------------------------------------
    // 取消
    // ----------------------------------------------------------------
    @PostMapping("/batch/cancel/{taskId}")
    @RequirePermission("report:rfm:batch:run")
    public Result<Map<String, Object>> cancel(@PathVariable("taskId") long taskId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.BAD_REQUEST, "未识别租户");

        // 校验归属 & 当前状态
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT status FROM rfm_batch_task WHERE id = ? AND tenant_id = ?",
            taskId, tenantId);
        if (rows.isEmpty()) return Result.error(BizCode.NOT_FOUND, "任务不存在或不属于当前租户");
        String cur = (String) rows.get(0).get("status");
        if (!"RUNNING".equals(cur)) {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("status", cur);
            return Result.error(BizCode.CONFLICT, "任务不在运行中，当前状态=" + cur, data);
        }

        int n = jdbc.update(
            "UPDATE rfm_batch_task SET status='CANCELLED', end_time=NOW(), remark='用户取消' " +
            "WHERE id=? AND tenant_id=? AND status='RUNNING'",
            taskId, tenantId);
        if (n == 0) {
            // 期间被别人取消或已完成
            return Result.error(BizCode.CONFLICT, "任务已被其他操作改变状态");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "CANCELLED");
        return Result.success("已请求取消", data);
    }

    // ----------------------------------------------------------------
    // 查进度（前端轮询，1~2 秒一次）
    // ----------------------------------------------------------------
    @GetMapping("/batch/progress/{taskId}")
    @RequirePermission("report:rfm:batch:run")
    public Result<Map<String, Object>> progress(@PathVariable("taskId") long taskId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.BAD_REQUEST, "未识别租户");

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, tenant_id, action, brand_id, operator_name, status, total, processed, " +
            "success, fail, start_time, end_time, remark " +
            "FROM rfm_batch_task WHERE id = ? AND tenant_id = ?",
            taskId, tenantId);
        if (rows.isEmpty()) return Result.error(BizCode.NOT_FOUND, "任务不存在或不属于当前租户");
        return Result.success(rows.get(0));
    }
}