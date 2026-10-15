package com.meiyetec.service;

import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RFM 批量任务执行器（异步）。
 * <p>
 *  - 主线程 INSERT rfm_batch_task (RUNNING) → 立即返回 taskId
 *  - 异步线程遍历会员并调用 RfmService.runComputeForMember
 *  - 每 50 条 UPDATE 一次进度（Q4 确认）
 *  - 取消时设置 cancelled 标记，下一轮循环检测到后 break
 *  - 完成后：
 *      * UPDATE rfm_batch_task SET status = 'DONE'/'FAILED'/'CANCELLED'
 *      * INSERT rfm_batch_log 一行（保留审计连续性）
 */
@Component
public class RfmBatchExecutor {

    private static final Logger log = LoggerFactory.getLogger(RfmBatchExecutor.class);

    /** 进度写入粒度（Q4 确认 = 50） */
    private static final int PROGRESS_FLUSH_EVERY = 50;

    @Autowired
    private JdbcTemplate jdbc;

    /** 通过 @Lazy 打破循环依赖：RfmService -> Executor -> RfmService */
    @Autowired
    @Lazy
    private RfmService rfmService;

    /**
     * 主线程调用：插入 RUNNING 任务行，返回 taskId。
     */
    public long createTask(Long tenantId, String action, Long brandId, String operatorName) {
        jdbc.update("INSERT INTO rfm_batch_task " +
                    "(tenant_id, action, brand_id, operator_name, status) " +
                    "VALUES (?,?,?,?, 'RUNNING')",
                    tenantId, action, brandId, operatorName);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // ============================================================
    // 批量计算（/batch/compute）
    // ============================================================

    @Async
    public void runBatch(long taskId, Long tenantId, Long brandId, int limit, String operatorName) {
        runInternal(taskId, tenantId, brandId, limit, "BATCH", operatorName);
    }

    // ============================================================
    // 种子数据（/seed）
    // ============================================================

    @Async
    public void runSeed(long taskId, Long tenantId, int limit, String operatorName) {
        runInternal(taskId, tenantId, null, limit, "SEED", operatorName);
    }

    // ============================================================
    // 通用执行
    // ============================================================

    /**
     * 异步执行批量/种子任务。BATCH 与 SEED 共用主体（都是为每个会员跑一次 RFM 计算）。
     * action 仅用于审计区分。
     */
    private void runInternal(long taskId, Long tenantId, Long brandId, int limit,
                              String action, String operatorName) {
        long t0 = System.currentTimeMillis();

        // 设置租户上下文（异步线程不会自动继承 RequestContext）
        TenantContext ctx = TenantContext.empty();
        ctx.setTenantId(tenantId);
        TenantContextHolder.set(ctx);

        // 拉会员
        StringBuilder sql = new StringBuilder(
            "SELECT id FROM crm_member WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (brandId != null) { sql.append("AND brand_id = ? "); args.add(brandId); }
        sql.append("LIMIT ?");
        args.add(limit);

        List<Map<String, Object>> members;
        try {
            members = jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.error("[RfmBatchExecutor] 拉会员失败 taskId={}", taskId, e);
            markFailed(taskId, "查询会员失败: " + e.getMessage());
            writeLog(tenantId, action, brandId, 0, 0, 0,
                     (int)(System.currentTimeMillis()-t0), operatorName,
                     "查询会员失败: " + e.getMessage());
            return;
        }

        int total = members.size();
        // 把 total 写回
        jdbc.update("UPDATE rfm_batch_task SET total = ? WHERE id = ?", total, taskId);

        int processed = 0, success = 0, fail = 0;
        String lastFailMsg = null;
        boolean cancelled = false;

        for (Map<String, Object> m : members) {
            // 检查取消（每条检查 — 取消响应延迟 < 50ms × 1 = 50ms，肉眼可感）
            String status = getStatus(taskId);
            if ("CANCELLED".equals(status)) { cancelled = true; break; }

            Long memberId = ((Number) m.get("id")).longValue();
            try {
                rfmService.computeForMember(memberId);
                success++;
            } catch (Exception e) {
                fail++;
                if (lastFailMsg == null) lastFailMsg = e.getMessage();
                log.warn("[RfmBatchExecutor] {} 失败 memberId={} err={}", action, memberId, e.getMessage());
            }
            processed++;

            // Q4：每 50 条写一次进度
            if (processed % PROGRESS_FLUSH_EVERY == 0) {
                flushProgress(taskId, processed, success, fail);
            }
        }

        // 最后再写一次进度（处理 total < 50 的小批次）
        flushProgress(taskId, processed, success, fail);

        long durationMs = System.currentTimeMillis() - t0;
        String finalStatus = cancelled ? "CANCELLED" : (fail == total && total > 0 ? "FAILED" : "DONE");
        jdbc.update("UPDATE rfm_batch_task SET status = ?, end_time = NOW(), remark = ? WHERE id = ?",
                    finalStatus, lastFailMsg, taskId);

        writeLog(tenantId, action, brandId, total, success, fail,
                 (int)durationMs, operatorName,
                 cancelled ? "用户取消"
                            : (lastFailMsg != null ? "失败样例: " + lastFailMsg : null));
        log.info("[RfmBatchExecutor] taskId={} {} done. total={} success={} fail={} cancelled={} duration={}ms",
                 taskId, action, total, success, fail, cancelled, durationMs);
    }

    private void flushProgress(long taskId, int processed, int success, int fail) {
        try {
            jdbc.update("UPDATE rfm_batch_task SET processed=?, success=?, fail=? WHERE id=?",
                        processed, success, fail, taskId);
        } catch (Exception e) {
            log.warn("[RfmBatchExecutor] flushProgress 失败 taskId={}", taskId, e);
        }
    }

    private void markFailed(long taskId, String msg) {
        jdbc.update("UPDATE rfm_batch_task SET status='FAILED', end_time=NOW(), remark=? WHERE id=?",
                    msg, taskId);
    }

    private String getStatus(long taskId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status FROM rfm_batch_task WHERE id = ?", taskId);
            if (rows.isEmpty()) return "MISSING";
            return (String) rows.get(0).get("status");
        } catch (Exception e) {
            return "ERR";
        }
    }

    private void writeLog(Long tenantId, String action, Long brandId,
                           int total, int success, int fail, int durationMs,
                           String operatorName, String remark) {
        try {
            jdbc.update("INSERT INTO rfm_batch_log " +
                        "(tenant_id, action, brand_id, total, success, fail, duration_ms, operator_name, remark) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                        tenantId, action, brandId, total, success, fail, durationMs, operatorName, remark);
        } catch (Exception e) {
            log.warn("[RfmBatchExecutor] writeLog 失败 action={}", action, e);
        }
    }
}
