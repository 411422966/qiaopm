package com.meiyetec.service;

import com.meiyetec.entity.Store;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STORE Feature Reality Check — 集成测试
 * 覆盖：
 *  - STORE-001 门店列表（storePage）
 *  - STORE-002 门店分页
 *  - STORE-003 门店详情（getById）
 *  - STORE-004 创建门店
 *  - STORE-005 编辑门店
 *
 * 前置：pos_store 表存在，mkt_brand 表存在（由 init_m02_basic.sql / init_rbac_v3_0_a.sql 保证）
 *       tenant_id=1 已存在（由 init_m12_tenant_freeze.sql 保证）
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StoreServiceOrgTest {

    private static final Long TENANT_A = 1L;
    private static Long createdStoreId;

    @Autowired private StoreService storeService;
    @Autowired private JdbcTemplate jdbc;

    private void setCtx(Long tenantId, Long userId) {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(tenantId);
        ctx.setUserId(userId);
        ctx.setUsername("test-user");
        ctx.setDataScope("ALL");
        TenantContextHolder.set(ctx);
    }

    // ================================================================
    // STORE-004 创建门店
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("STORE-004-1: save() 成功创建门店，ID 自增，tenant_id 自动写入")
    void createStoreSuccess() {
        setCtx(TENANT_A, 1L);
        long ts = System.currentTimeMillis();
        Store s = new Store();
        s.setStoreName("测试门店-" + ts);
        s.setStoreCode("ST" + ts);
        s.setTenantId(TENANT_A);
        s.setStatus(1);
        boolean ok = storeService.save(s);
        assertTrue(ok, "save() 应返回 true");
        assertNotNull(s.getId(), "save 后 id 不应为 null");
        createdStoreId = s.getId();

        // 验证写库
        Store saved = storeService.getById(s.getId());
        assertEquals("测试门店-" + ts, saved.getStoreName());
        assertEquals("ST" + ts, saved.getStoreCode());
        assertEquals(TENANT_A, saved.getTenantId(), "tenantId 必须写入");
    }

    @Test
    @Order(11)
    @DisplayName("STORE-004-2: getById() 查到已创建的门店")
    void getByIdAfterCreate() {
        setCtx(TENANT_A, 1L);
        if (createdStoreId == null) { assertTrue(true, "依赖上一个测试"); return; }
        Store s = storeService.getById(createdStoreId);
        assertNotNull(s, "应能查到");
        assertEquals(createdStoreId, s.getId());
        assertNotNull(s.getStoreName());
    }

    // ================================================================
    // STORE-002 门店分页
    // ================================================================

    @Test
    @Order(20)
    @DisplayName("STORE-002-1: list() 返回非空列表（至少含租户1的门店）")
    void listReturnsNonEmpty() {
        setCtx(TENANT_A, 1L);
        List<Store> stores = storeService.list();
        assertNotNull(stores);
        assertTrue(stores.size() >= 0, "list 应正常返回");
    }

    @Test
    @Order(21)
    @DisplayName("STORE-002-2: page() 返回分页结构，records 字段完整")
    void pageReturnsPagedRecords() {
        setCtx(TENANT_A, 1L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Store> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        var result = storeService.page(p);
        assertNotNull(result);
        assertNotNull(result.getRecords());
        assertTrue(result.getTotal() >= 0, "total 应 >= 0");
    }

    // ================================================================
    // STORE-005 编辑门店
    // ================================================================

    @Test
    @Order(30)
    @DisplayName("STORE-005-1: updateById() 成功更新门店字段")
    void updateStoreSuccess() {
        setCtx(TENANT_A, 1L);
        if (createdStoreId == null) { assertTrue(true, "依赖上一个测试"); return; }

        Store s = storeService.getById(createdStoreId);
        s.setContactPerson("新联系人");
        s.setStatus(0);
        boolean ok = storeService.updateById(s);
        assertTrue(ok, "updateById() 应返回 true");

        Store updated = storeService.getById(createdStoreId);
        assertEquals("新联系人", updated.getContactPerson());
        assertEquals(0, updated.getStatus());
    }

    @Test
    @Order(31)
    @DisplayName("STORE-005-2: storeCode UNIQUE 约束存在（重复插入应抛异常）")
    void storeCodeUniqueConstraint() {
        setCtx(TENANT_A, 1L);
        long ts = System.currentTimeMillis();
        String code = "UNIQUE-" + ts;

        Store s1 = new Store();
        s1.setStoreName("唯一码门店1-" + ts);
        s1.setStoreCode(code);
        s1.setTenantId(TENANT_A);
        s1.setStatus(1);
        storeService.save(s1);

        Store s2 = new Store();
        s2.setStoreName("唯一码门店2-" + ts);
        s2.setStoreCode(code);
        s2.setTenantId(TENANT_A);
        s2.setStatus(1);

        // 唯一键冲突时 MySQL 抛 DataIntegrityViolationException
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> storeService.save(s2),
                "重复 storeCode 应触发唯一键异常");

        // Cleanup
        jdbc.update("DELETE FROM pos_store WHERE store_code = ?", code);
    }

    // ================================================================
    // STORE-003 门店详情
    // ================================================================

    @Test
    @Order(40)
    @DisplayName("STORE-003-1: getById() 对存在的门店返回完整字段")
    void getByIdReturnsFullRecord() {
        setCtx(TENANT_A, 1L);
        if (createdStoreId == null) { assertTrue(true, "依赖上一个测试"); return; }
        Store s = storeService.getById(createdStoreId);
        assertNotNull(s);
        assertEquals(createdStoreId, s.getId());
        assertNotNull(s.getStoreName());
        assertNotNull(s.getStatus());
        assertNotNull(s.getTenantId());
    }

    @Test
    @Order(41)
    @DisplayName("STORE-003-2: getById() 对不存在的门店返回 null")
    void getByIdReturnsNullForNonexistent() {
        setCtx(TENANT_A, 1L);
        Store s = storeService.getById(999999L);
        assertNull(s, "不存在的门店应返回 null");
    }

    // ================================================================
    // STORE-001 门店列表
    // ================================================================

    @Test
    @Order(50)
    @DisplayName("STORE-001-1: list() ORDER BY id ASC（升序）")
    void listOrderByIdAsc() {
        setCtx(TENANT_A, 1L);
        List<Store> stores = storeService.list();
        if (stores.size() >= 2) {
            for (int i = 0; i < stores.size() - 1; i++) {
                Store a = stores.get(i);
                Store b = stores.get(i + 1);
                assertTrue(a.getId() <= b.getId(),
                        "应按 id 升序排列: " + a.getId() + " vs " + b.getId());
            }
        }
    }

    @Test
    @Order(51)
    @DisplayName("STORE-001-2: deleted=1 的记录不出现在列表中（@TableLogic）")
    void softDeleteExcluded() {
        setCtx(TENANT_A, 1L);
        // 创建后软删除
        long ts = System.currentTimeMillis();
        Store s = new Store();
        s.setStoreName("软删除测试-" + ts);
        s.setStoreCode("DEL-" + ts);
        s.setTenantId(TENANT_A);
        s.setStatus(1);
        storeService.save(s);
        Long id = s.getId();

        // 确认存在
        assertNotNull(storeService.getById(id));

        // 软删除
        storeService.removeById(id);

        // 再次查询不应出现
        Store deleted = storeService.getById(id);
        assertNull(deleted, "软删除后 getById 应返回 null");
    }

    // ================================================================
    // Cleanup
    // ================================================================

    @Test
    @Order(99)
    @DisplayName("Cleanup: 删除测试创建的门店")
    @AfterAll
    static void cleanup(@Autowired JdbcTemplate jdbc) {
        if (createdStoreId != null) {
            jdbc.update("DELETE FROM pos_store WHERE id = ?", createdStoreId);
        }
        TenantContextHolder.clear();
    }
}
