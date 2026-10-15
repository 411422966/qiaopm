package com.meiyetec.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-T12 · DCEP pay_dcep_channel.env_mode 物理约束测试
 *
 * <p>覆盖 4 类场景：
 * <ul>
 *   <li>CHK-1: env_mode='MOCK' 合法</li>
 *   <li>CHK-2: env_mode='SANDBOX' 合法</li>
 *   <li>CHK-3: env_mode='PROD' 合法</li>
 *   <li>CHK-4: env_mode='dev' 必须被 CHECK 约束拒绝（INSERT/UPDATE 抛异常）</li>
 * </ul>
 *
 * <p>依赖约束：{@code ck_pay_dcep_channel_env_mode}（由
 * {@code backend/src/main/resources/db/sql/migrate_pay_dcep_channel_env_mode_check.sql} 创建）。
 * 测试不会破坏历史数据 — 使用测试 channel_code + cleanup 清理。</p>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepChannelEnvModeTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static final String TEST_CHANNEL_PREFIX = "T12-ENV-CHK-";

    @BeforeAll
    void setupClass() {
        // 清理任何残留
        jdbc.update("UPDATE pay_dcep_channel SET deleted = 1 WHERE channel_code LIKE ?",
                TEST_CHANNEL_PREFIX + "%");
    }

    @AfterAll
    void tearDownClass() {
        // 软删除所有测试通道
        jdbc.update("UPDATE pay_dcep_channel SET deleted = 1 WHERE channel_code LIKE ?",
                TEST_CHANNEL_PREFIX + "%");
    }

    private String uniqCode() {
        return TEST_CHANNEL_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    private void softDelete(String code) {
        jdbc.update("UPDATE pay_dcep_channel SET deleted = 1 WHERE channel_code = ?", code);
    }

    /**
     * 尝试 INSERT 一行 env_mode = x 的测试通道；返回 true 表示成功，false 表示被 CHECK 拒绝。
     */
    private boolean tryInsert(String code, String envMode) {
        try {
            jdbc.update(
                "INSERT INTO pay_dcep_channel " +
                "(tenant_id, channel_code, channel_name, env_mode, merchant_id, api_base_url, status, create_time, deleted) " +
                "VALUES (1, ?, ?, ?, ?, ?, 1, NOW(), 0)",
                code, "T12-Test-" + code, envMode, "TEST-MERCHANT-" + code,
                "http://localhost:8080/mock");
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        } catch (Exception e) {
            // CHECK 约束失败在 MySQL 上也可能报 CheckViolation 或 SQLException
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("check") || msg.contains("constraint") || msg.contains("ck_")) {
                return false;
            }
            throw e;
        }
    }

    // ========================================================================
    // CHK-1/2/3: 合法 env_mode 值
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("CHK-1: env_mode='MOCK' 合法 — INSERT 成功")
    void chk_mock_allowed() {
        String code = uniqCode();
        boolean ok = tryInsert(code, "MOCK");
        assertTrue(ok, "env_mode='MOCK' 应被 CHECK 接受");
        softDelete(code);
    }

    @Test
    @Order(2)
    @DisplayName("CHK-2: env_mode='SANDBOX' 合法 — INSERT 成功")
    void chk_sandbox_allowed() {
        String code = uniqCode();
        boolean ok = tryInsert(code, "SANDBOX");
        assertTrue(ok, "env_mode='SANDBOX' 应被 CHECK 接受");
        softDelete(code);
    }

    @Test
    @Order(3)
    @DisplayName("CHK-3: env_mode='PROD' 合法 — INSERT 成功")
    void chk_prod_allowed() {
        String code = uniqCode();
        boolean ok = tryInsert(code, "PROD");
        assertTrue(ok, "env_mode='PROD' 应被 CHECK 接受");
        softDelete(code);
    }

    // ========================================================================
    // CHK-4: 非法 env_mode='dev' 必须被拒绝
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("CHK-4: env_mode='dev' 被 CHECK 约束拒绝（INSERT 失败）")
    void chk_dev_rejected() {
        String code = uniqCode();
        boolean ok = tryInsert(code, "dev");
        assertFalse(ok, "P0-T12 CHECK 约束必须拒绝 env_mode='dev'");
        softDelete(code);  // 安全清理（虽然上面已经没插进去）
    }

    // ========================================================================
    // 约束存在性检查（验证迁移已应用）
    // ========================================================================

    @Test
    @Order(20)
    @DisplayName("CHK-5: ck_pay_dcep_channel_env_mode 约束已注册到 information_schema")
    void chk_constraint_exists() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS " +
            "WHERE CONSTRAINT_NAME = 'ck_pay_dcep_channel_env_mode' AND CONSTRAINT_SCHEMA = 'meiyetec'");
        assertEquals(1, rows.size(),
                "P0-T12 约束 ck_pay_dcep_channel_env_mode 应在 information_schema 中");
        String clause = (String) rows.get(0).get("CHECK_CLAUSE");
        assertNotNull(clause);
        assertTrue(clause.contains("MOCK") && clause.contains("SANDBOX") && clause.contains("PROD"),
                "CHECK 子句应包含 MOCK/SANDBOX/PROD");
    }

    // ========================================================================
    // 现状数据库 0 行 env_mode='dev'
    // ========================================================================

    @Test
    @Order(21)
    @DisplayName("CHK-6: pay_dcep_channel 中 0 行 env_mode='dev'（历史脏数据已修复为 MOCK）")
    void chk_no_dev_in_db() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pay_dcep_channel WHERE env_mode = 'dev'",
            Integer.class);
        assertEquals(0, count != null ? count : 0,
                "P0-T12 历史 dev 模式通道应已修复为 MOCK；0 行 dev mode");
    }
}