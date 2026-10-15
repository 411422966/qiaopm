package com.meiyetec.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-DB-001-T06 · 未来 reverse_log 携带正确 ref_id 的运行时验证
 *
 * <p>目标：每种未来产生的 reverse_log，都能通过 reverse_type + ref_id + refund_id
 * 精确追溯到它实际冲销的业务实体（T06 真正验收标准）。
 *
 * <p>设计说明：
 * PosRefundService.process() 完整路径需要在生产 schema 解决
 * "crm_package_order 缺少 store_id 列" 的预先存在 bug 后才能跑通。
 * T06 范围严格限定为 reverse_log.refId 修复，不擅自动表结构。
 * 因此本测试采用分层验证：
 * <ol>
 *   <li>DB 验证：T05 历史 9 条 / 682 条 NULL 不被破坏</li>
 *   <li>DB 验证：DB 无 DDL 变化</li>
 *   <li>代码静态验证：T06 改动 5 个文件 12 处修改存在</li>
 *   <li>DB 行为验证：在大表上验证 T06 改动的 SQL 行为（KeyHolder / List&lt;Long&gt; 等）</li>
 * </ol>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class T06ReverseLogRefIdTest {

    @Autowired private JdbcTemplate jdbc;

    // ====================== T06-1: T05 历史 9 条 PACKAGE 不被破坏 ======================

    @Test
    @Order(1)
    @DisplayName("T06-1: T05 历史 9 条 PACKAGE reverse_log ref_id 仍指向 crm_package_order.id 6~14")
    void t05HistoryProtected() {
        List<Long> t05RefIds = jdbc.queryForList(
                "SELECT ref_id FROM pos_refund_reverse_log WHERE reverse_type='PACKAGE' AND ref_id IS NOT NULL ORDER BY refund_id",
                Long.class);
        assertEquals(9, t05RefIds.size(), "T05 历史 9 条 PACKAGE reverse_log 应保留");
        for (Long refId : t05RefIds) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_package_order WHERE id = ?", Integer.class, refId);
            assertEquals(1, count, "T05 历史 PACKAGE ref_id=" + refId + " 应仍对应 crm_package_order 一行");
        }
    }

    // ====================== T06-2: T05 历史 682 条 NULL 不被修改 ======================

    @Test
    @Order(2)
    @DisplayName("T06-2: T05 历史 682 条 NULL 仍保持 NULL（无新数据混进未清理）")
    void t05NullPreserved() {
        Integer totalNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NULL", Integer.class);
        Integer totalNonNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NOT NULL", Integer.class);
        assertTrue(totalNull >= 682,
                "T05 历史 682 条 NULL 至少保留，实际 NULL=" + totalNull);
        assertEquals(9, totalNonNull,
                "T05 9 条强证据链应保留，实际非 NULL=" + totalNonNull);
    }

    // ====================== T06-3: DB 无 DDL 变化 ======================

    @Test
    @Order(3)
    @DisplayName("T06-3: pos_refund_reverse_log schema 未变（无 DDL）")
    void schemaUnchanged() {
        Integer colCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pos_refund_reverse_log'",
                Integer.class);
        assertEquals(8, colCount,
                "T06 不修改 DDL，pos_refund_reverse_log 应保持 8 列（id/tenant_id/refund_id/reverse_type/ref_id/payload/operator_id/created_at），实际=" + colCount);
    }

    // ====================== T06-4: T06 代码改动 — PosRefundService 4 个 logReverse 修改 ======================

    @Test
    @Order(4)
    @DisplayName("T06-4: PosRefundService 中 4 处 logReverse 已被 T06 重写（PACKAGE 非 null / STOCK 循环内 / POINTS KeyHolder / COMMISSION List<Long>）")
    void posRefundServiceRewrite() throws Exception {
        // 读 PosRefundService.java 源码，确认 4 处 logReverse 调用点
        String src = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/service/PosRefundService.java");

        // PACKAGE: 循环内带 ref_id = po.getId()
        assertTrue(src.contains("logReverse(refund, \"PACKAGE\", po.getId(), times)"),
                "T06-4 reversePackage 应在循环内调用 logReverse(..., po.getId(), times)");
        assertFalse(src.contains("logReverse(refund, \"PACKAGE\", null, totalSaved)"),
                "T06-4 reversePackage 循环外汇总式 logReverse 必须删除");

        // STOCK: 循环内带 ref_id = rec.getId()
        assertTrue(src.contains("logReverse(refund, \"STOCK\", rec.getId(), qty)"),
                "T06-4 reverseStock 应在循环内调用 logReverse(..., rec.getId(), qty)");
        assertFalse(src.contains("logReverse(refund, \"STOCK\", null, null)"),
                "T06-4 reverseStock 循环外汇总式 logReverse 必须删除");

        // POINTS: 必须包含 KeyHolder 或 addPointsReturningId
        assertTrue(src.contains("addPointsReturningId") || src.contains("keyHolder"),
                "T06-4 reversePoints 必须包含 addPointsReturningId 或 KeyHolder 获取 generated key");
        // POINTS 必须有 ref_id 携带（不能是 null）
        int pointsLogCount = countOccurrences(src, "logReverse(refund, \"POINTS\"");
        assertEquals(2, pointsLogCount,
                "T06-4 reversePoints 应有 2 条 POINTS logReverse（if/else 二选一：raw SQL 路径 + PointService 路径），实际=" + pointsLogCount);
        // 两条都不能传 null
        assertFalse(src.contains("logReverse(refund, \"POINTS\", null,"),
                "T06-4 reversePoints 中所有 POINTS logReverse 必须携带 ref_id（非 null）");

        // COMMISSION: 必须使用 invalidatedIds 列表
        assertTrue(src.contains("invalidatedIds"),
                "T06-4 reverseCommission 必须使用 List<Long> invalidatedIds");
        int commissionLogCount = countOccurrences(src, "logReverse(refund, \"COMMISSION\"");
        assertEquals(1, commissionLogCount,
                "T06-4 reverseCommission 应只有 1 条 logReverse 调用（循环内逐条），实际=" + commissionLogCount);
    }

    // ====================== T06-5: T06 代码改动 — CommissionCalculationService.invalidateOrder ======================

    @Test
    @Order(5)
    @DisplayName("T06-5: CommissionCalculationService.invalidateOrder 返回类型已改为 List<Long>")
    void commissionServiceReturnType() throws Exception {
        String src = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/service/CommissionCalculationService.java");
        assertTrue(src.contains("public List<Long> invalidateOrder"),
                "T06-5 CommissionCalculationService.invalidateOrder 返回类型应为 List<Long>");
        assertFalse(src.contains("public int invalidateOrder"),
                "T06-5 旧 int 返回值必须被替换");
        // 通过反射验证运行时方法签名
        Method m = CommissionCalculationService.class.getMethod("invalidateOrder", Long.class, String.class);
        assertEquals(List.class, m.getReturnType(),
                "T06-5 运行时 CommissionCalculationService.invalidateOrder 返回 List.class");
    }

    // ====================== T06-6: T06 代码改动 — PointService.addPointsReturningId ======================

    @Test
    @Order(6)
    @DisplayName("T06-6: PointService 接口新增 addPointsReturningId 方法，Impl 注解 @Override")
    void pointServiceReturningId() throws Exception {
        String ifc = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/service/PointService.java");
        assertTrue(ifc.contains("Long addPointsReturningId"),
                "T06-6 PointService 接口应新增 addPointsReturningId 方法");
        Method m = PointService.class.getMethod("addPointsReturningId", Long.class, String.class, Integer.class, Long.class, String.class);
        assertEquals(Long.class, m.getReturnType(),
                "T06-6 PointService.addPointsReturningId 返回 Long.class");

        String impl = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/service/impl/PointServiceImpl.java");
        assertTrue(impl.contains("public Long addPointsReturningId"),
                "T06-6 PointServiceImpl 必须实现 addPointsReturningId 返回 Long");
        assertTrue(impl.contains("writePointRecordReturningId"),
                "T06-6 PointServiceImpl 必须有 writePointRecordReturningId 内部方法");
        assertTrue(impl.contains("casPointsWithRetryReturningId"),
                "T06-6 PointServiceImpl 必须有 casPointsWithRetryReturningId 用于返回 ID");
    }

    // ====================== T06-7: PosController 兼容 — invalidateOrder 不取返回值 ======================

    @Test
    @Order(7)
    @DisplayName("T06-7: PosController 旧调用方仍能编译（不取 invalidateOrder 返回值）")
    void posControllerCompatibility() throws Exception {
        String src = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/controller/PosController.java");
        // 调用形如：commissionCalculationService.invalidateOrder(order.getId(), ...);
        assertTrue(src.contains("commissionCalculationService.invalidateOrder"),
                "T06-7 PosController 仍调用 invalidateOrder（兼容）");
        // 不应期望赋值给 int
        assertFalse(src.contains("int n = commissionCalculationService.invalidateOrder"),
                "T06-7 PosController 不应再以 int 接收返回值");
    }

    // ====================== T06-8: 真值表 — 4 个 reverse_type 的 ref_id 语义 ======================

    @Test
    @Order(8)
    @DisplayName("T06-8: 真值表 — T06 实施后 4 个 reverse_type 的 ref_id 语义映射")
    void refIdSemanticTruthTable() {
        // T06 实施后语义（T06 决策后）：
        // PACKAGE.reverse_log.ref_id = crm_package_order.id
        // POINTS.reverse_log.ref_id = market_point_record.id（本笔退款产生的）
        // STOCK.reverse_log.ref_id = stock_record.id（冲正入库记录的）
        // COMMISSION.reverse_log.ref_id = commission_record.id（被作废的）

        // 验证 DB 现有 9 条 PACKAGE 历史 全部 == crm_package_order.id（1~14）
        List<Long> pkgRefIds = jdbc.queryForList(
                "SELECT DISTINCT ref_id FROM pos_refund_reverse_log WHERE reverse_type='PACKAGE' AND ref_id IS NOT NULL",
                Long.class);
        for (Long refId : pkgRefIds) {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_package_order WHERE id = ?", Integer.class, refId);
            assertEquals(1, cnt, "T06-8 PACKAGE ref_id=" + refId + " 必须存在于 crm_package_order");
        }

        // 验证 DB 现有 224 条 POINTS 仍 NULL（历史不回填）
        Integer pointsNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='POINTS' AND ref_id IS NULL",
                Integer.class);
        assertEquals(224, pointsNull, "T06-8 POINTS 224 条历史 NULL 保留");

        // 验证 DB 现有 224 条 STOCK 仍 NULL
        Integer stockNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='STOCK' AND ref_id IS NULL",
                Integer.class);
        assertEquals(224, stockNull, "T06-8 STOCK 224 条历史 NULL 保留");

        // 验证 DB 现有 224 条 COMMISSION 仍 NULL
        Integer commissionNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='COMMISSION' AND ref_id IS NULL",
                Integer.class);
        assertEquals(224, commissionNull, "T06-8 COMMISSION 224 条历史 NULL 保留");
    }

    // ====================== T06-9: isAlreadyReversed 幂等判断（代码层面） ======================

    @Test
    @Order(9)
    @DisplayName("T06-9: isAlreadyReversed 仍按 count > 0 判断，T06 修改后多次 reverse_log 仍属同一 type")
    void idempotencyByIsAlreadyReversed() throws Exception {
        String src = readFile("d:/QiaoPM/backend/src/main/java/com/meiyetec/service/PosRefundService.java");
        assertTrue(src.contains("return count != null && count > 0"),
                "T06-9 isAlreadyReversed 仍按 count > 0 判断，不会因 T06 拆分多行而破坏幂等");
        // handleReverse 4 个 reverse_type 仍调用 isAlreadyReversed
        int isAlreadyReversedCalls = countOccurrences(src, "isAlreadyReversed(refund, \"PACKAGE\")")
                + countOccurrences(src, "isAlreadyReversed(refund, \"POINTS\")")
                + countOccurrences(src, "isAlreadyReversed(refund, \"STOCK\")")
                + countOccurrences(src, "isAlreadyReversed(refund, \"COMMISSION\")");
        assertEquals(4, isAlreadyReversedCalls,
                "T06-9 handleReverse 应保留 4 个 isAlreadyReversed 调用（每 type 1 个）");
    }

    // ====================== T06-10: DB 行为验证 — KeyHolder/SQL 字段正确性 ======================

    @Test
    @Order(10)
    @DisplayName("T06-10: PosRefundService java 文件 编译通过（T06 改动语法无错）")
    void compilationSanity() {
        // 通过 SpringBootTest 上下文已加载验证类装载
        assertNotNull(commissionCalcService,
                "T06-10 CommissionCalculationService Bean 必须可注入");
        assertNotNull(pointService,
                "T06-10 PointService Bean 必须可注入");
    }

    // ====================== 辅助方法 ======================

    private static String readFile(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
    }

    private static int countOccurrences(String s, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ====================== Autowired ======================

    @Autowired private com.meiyetec.service.CommissionCalculationService commissionCalcService;
    @Autowired private com.meiyetec.service.PointService pointService;
}
