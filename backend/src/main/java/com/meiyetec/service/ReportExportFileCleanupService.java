package com.meiyetec.service;

import com.meiyetec.mapper.ReportExportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T10 临时文件清理
 *
 * <p>ReportV2Service 在 {@code java.io.tmpdir/meiyetec-reports/} 下生成 CSV 导出文件，
 * 用户下载后文件不再被引用，长期积累会撑满 /tmp 分区。
 *
 * <p>清理策略：
 * <ul>
 *   <li>每天凌晨 3:00（低峰期）执行一次全量扫描清理</li>
 *   <li>启动时立即执行一次（清理上次异常退出遗留的文件）</li>
 *   <li>仅删除符合导出文件命名规则的文件（{@code T{timestamp}_{code}_{date}.csv}）</li>
 *   <li>只删除超过 retentionDays 天且任务已结束（status=3 成功/4 失败/5 取消）的文件</li>
 *   <li>物理文件删除后，将 DB 中对应任务的 file_path 置 NULL（保留任务记录供审计）</li>
 * </ul>
 *
 * <p>配置（application.yml）：
 * <pre>
 * app.report.export.retention-days: 7   # 默认 7 天后清理
 * app.report.export.cleanup-enabled: true
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportExportFileCleanupService {

    private final ReportExportTaskMapper taskMapper;
    private final JdbcTemplate jdbc;

    /** 导出目录（与 ReportV2Service.EXPORT_DIR 保持一致） */
    private static final String EXPORT_DIR_NAME = "meiyetec-reports";

    /** 文件名正则：T{timestamp}_{reportCode}_{date}.csv */
    private static final java.util.regex.Pattern FILE_NAME_PATTERN =
            java.util.regex.Pattern.compile("^T\\d+_[A-Z]+_\\d{4}-\\d{2}-\\d{2}\\.csv$");

    @Value("${app.report.export.retention-days:7}")
    private int retentionDays;

    @Value("${app.report.export.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    private Path exportDir;

    @PostConstruct
    void init() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        this.exportDir = Paths.get(tmpDir, EXPORT_DIR_NAME).toAbsolutePath().normalize();
        if (cleanupEnabled) {
            log.info("[CLEANUP] 启动清理探查: exportDir={}, retentionDays={} 天", exportDir, retentionDays);
            doCleanup("启动清理");
        }
    }

    /**
     * 每天凌晨 3:00 执行（低峰期，低频，不影响业务）
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            log.debug("[CLEANUP] 已禁用 (cleanup-enabled=false)");
            return;
        }
        doCleanup("定时清理");
    }

    /**
     * 清理逻辑：
     * <ol>
     *   <li>遍历 exportDir 下所有符合命名规则的文件</li>
     *   <li>文件修改时间 < now - retentionDays → 进入候选</li>
     *   <li>查询 DB：对应 task 的 file_path 是否仍指向此文件（已被下载覆盖的已清理）</li>
     *   <li>若 DB 已无此路径（或 task 已结束）→ 物理删除</li>
     *   <li>若物理删除成功 → 将 DB file_path 置 NULL（审计留记录）</li>
     * </ol>
     */
    public void doCleanup(String trigger) {
        if (!Files.exists(exportDir)) {
            log.info("[CLEANUP] {}: 目录不存在, 跳过", trigger);
            return;
        }

        long cutoffEpochMs = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        AtomicInteger deleted = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        try (var stream = Files.list(exportDir)) {
            var files = stream.filter(p -> {
                String n = p.getFileName().toString();
                return FILE_NAME_PATTERN.matcher(n).matches();
            }).toList();

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                long mtimeMs;
                try {
                    mtimeMs = Files.getLastModifiedTime(file).toMillis();
                } catch (IOException ex) {
                    failed.incrementAndGet();
                    log.warn("[CLEANUP] 读 mtime 失败: {} — {}", fileName, ex.getMessage());
                    continue;
                }

                if (mtimeMs > cutoffEpochMs) {
                    skipped.incrementAndGet();
                    continue;
                }

                String filePath = file.toAbsolutePath().toString();
                try {
                    Integer refCount = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM report_export_task " +
                                    "WHERE file_path = ? AND status IN (1,2)",
                            Integer.class, filePath);

                    if (refCount != null && refCount > 0) {
                        log.debug("[CLEANUP] 跳过（任务进行中）: {}", fileName);
                        skipped.incrementAndGet();
                        continue;
                    }

                    Files.deleteIfExists(file);
                    deleted.incrementAndGet();

                    jdbc.update(
                            "UPDATE report_export_task SET file_path = NULL " +
                                    "WHERE file_path = ? AND status NOT IN (1,2)",
                            filePath);

                    log.info("[CLEANUP] 已删除（{}）: {}", trigger, fileName);

                } catch (Exception ex) {
                    failed.incrementAndGet();
                    log.warn("[CLEANUP] 处理失败: {} — {}", fileName, ex.getMessage());
                }
            }

            log.info("[CLEANUP] {} 完成: 删除 {} 个文件, 跳过 {} 个, 失败 {} 个",
                    trigger, deleted.get(), skipped.get(), failed.get());

        } catch (IOException e) {
            log.error("[CLEANUP] {} 异常: {}", trigger, e.getMessage(), e);
        }
    }

    /**
     * 手动触发清理（供运维调用）
     */
    public Map<String, Object> manualCleanup() {
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        try {
            var count = Files.list(exportDir).filter(p -> {
                String n = p.getFileName().toString();
                return FILE_NAME_PATTERN.matcher(n).matches();
            }).count();
            before.set((int) count);
        } catch (IOException e) {
            before.set(-1);
        }

        doCleanup("手动触发");

        try {
            var count = Files.list(exportDir).filter(p -> {
                String n = p.getFileName().toString();
                return FILE_NAME_PATTERN.matcher(n).matches();
            }).count();
            after.set((int) count);
        } catch (IOException e) {
            after.set(-1);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("retentionDays", retentionDays);
        r.put("exportDir", exportDir.toString());
        r.put("beforeCount", before.get());
        r.put("afterCount", after.get());
        r.put("deleted", before.get() - after.get());
        r.put("timestamp", LocalDateTime.now());
        return r;
    }
}
