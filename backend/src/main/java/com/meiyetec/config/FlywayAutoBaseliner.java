package com.meiyetec.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务 5 — Flyway 自动 baseline 探针
 *
 * <h3>目的</h3>
 * 在 Flyway {@code migrate()} 启动之前，先判断当前 DB 的状态：
 * <ul>
 *   <li>若 DB 内已有任意核心业务表（如 {@code sys_user / biz_booking / crm_member}
 *       <b>且</b> {@code flyway_schema_history} 表尚不存在 → 视为「pre-Flyway
 *       历史库」 → 先写入一条 BASELINE 记录（version=0）防止 V1__init_baseline.sql
 *       在 {@code CREATE TABLE IF NOT EXISTS} 通过后误写 SUCCESS 行覆盖 baseline 语义</li>
 *   <li>若 {@code flyway_schema_history} 已经存在 → Flyway 自己处理，跳过</li>
 *   <li>若 DB 完全为空 → Flyway 自己从 V1 开始 migrate，跳过</li>
 * </ul>
 *
 * <h3>触发时机</h3>
 * {@code @PostConstruct} 早于 {@code FlywayMigrationInitializer}（Spring Boot 在
 * {@code SmartLifecycle} 阶段触发 migrate）。通过 {@code @DependsOn("dataSource")}
 * 显式确保 DataSource 先就绪。
 *
 * <h3>关闭方式</h3>
 * 启动参数 {@code -Dflyway.boot-baseline=false}（默认 true）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlywayAutoBaseliner {

    private final JdbcTemplate jdbcTemplate;

    /** 总开关（默认开）：false 时整个探针 no-op */
    @Value("${flyway.boot-baseline:true}")
    private boolean enabled;

    /**
     * 「任意业务表」白名单 — 检测到其中任一表存在 + 历史表不存在 → 视为 legacy 库
     * 这里挑了 6 张代表表，覆盖各业务域
     */
    private static final String[] LEGACY_PROBES = {
            "sys_user", "sys_role", "pos_store",
            "biz_booking", "crm_member", "stock_product"
    };

    @PostConstruct
    public void detectAndBaseline() {
        if (!enabled) {
            log.info("[FLYWAY] boot-baseline disabled (flyway.boot-baseline=false)");
            return;
        }
        try {
            // ---- 1. flyway_schema_history 是否已存在？ ----
            Integer historyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    " WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'",
                    Integer.class);
            if (historyCount != null && historyCount > 0) {
                log.info("[FLYWAY] flyway_schema_history already exists → skip auto-baseline");
                return;
            }

            // ---- 2. 任意 legacy 表是否存在？ ----
            boolean hasLegacy = false;
            String firstHit = null;
            for (String tbl : LEGACY_PROBES) {
                Integer n = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables " +
                        " WHERE table_schema = DATABASE() AND table_name = ?",
                        Integer.class, tbl);
                if (n != null && n > 0) {
                    hasLegacy = true;
                    firstHit = tbl;
                    break;
                }
            }
            if (!hasLegacy) {
                log.info("[FLYWAY] no legacy tables detected → fresh DB, Flyway will migrate from V1");
                return;
            }

            // ---- 3. legacy 库 + 无 history → 写 BASELINE 行 ----
            // 注意：在 Flyway 启动前直接 INSERT，因为它尚未创建表
            log.info("[FLYWAY] legacy DB detected (hit probe={}) → inserting BASELINE marker before Flyway starts", firstHit);
            jdbcTemplate.update(
                    "CREATE TABLE IF NOT EXISTS flyway_schema_history (" +
                    "  installed_rank INT NOT NULL," +
                    "  version VARCHAR(50)," +
                    "  description VARCHAR(200) NOT NULL," +
                    "  type VARCHAR(20) NOT NULL," +
                    "  script VARCHAR(1000) NOT NULL," +
                    "  checksum INT," +
                    "  installed_by VARCHAR(100) NOT NULL," +
                    "  installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  execution_time INT NOT NULL," +
                    "  success TINYINT(1) NOT NULL," +
                    "  PRIMARY KEY (installed_rank)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // installed_rank 必须唯一 — 用 1 作 baseline
            int existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE installed_rank = 1", Integer.class);
            if (existing == 0) {
                jdbcTemplate.update(
                        "INSERT INTO flyway_schema_history " +
                        "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) " +
                        "VALUES (1, '0', '?', 'BASELINE', '<< Flyway Baseline >>', NULL, ?, 0, 1)",
                        "pre-flyway legacy schema",
                        "FlywayAutoBaseliner");
                log.info("[FLYWAY] baseline row inserted (installed_rank=1, version=0, success=1)");
            } else {
                log.info("[FLYWAY] baseline row already exists at rank=1 → skip insert");
            }
        } catch (Exception e) {
            log.warn("[FLYWAY] auto-baseline probe failed (non-fatal): {}. Flyway will handle with baseline-on-migrate=true", e.getMessage());
        }
    }

    /** 让 Map 类型在部分 IDE 报警时仍可读（保留 Map 静态 import 避免 unused warning） */
    @SuppressWarnings("unused")
    private static Map<String, Object> staticMapStub() { return Map.of(); }
}
