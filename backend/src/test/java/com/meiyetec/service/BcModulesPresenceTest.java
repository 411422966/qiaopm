package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 4.27 BC 区块链模块关键 controller / service / entity 完整、DB 表齐全、Stub 已移除。
 */
class BcModulesPresenceTest {

    @Test
    void bc_001_002_blockchain_controller_exposes_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.BlockchainController");
        // 6 类存证
        assertHasMethod(c, "recordProduct");
        assertHasMethod(c, "recordDrug");
        assertHasMethod(c, "recordDevice");
        assertHasMethod(c, "recordOrder");
        assertHasMethod(c, "recordConsent");
        assertHasMethod(c, "record");
        // 查询
        assertHasMethod(c, "getRecord");
        assertHasMethod(c, "recordPage");
        // 链维护
        assertHasMethod(c, "verifyChain");
        assertHasMethod(c, "chainInfo");
        assertHasMethod(c, "chainOverview");
        assertHasMethod(c, "trace");
        // 统计
        assertHasMethod(c, "chainStats");
        assertHasMethod(c, "recordTypeStats");
        assertHasMethod(c, "dailyTrend");
        assertHasMethod(c, "overview");
        // 工具
        assertHasMethod(c, "hash");
    }

    @Test
    void bc_003_smart_contract_controller_and_impl_expose_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.DcepSmartContractController");
        assertHasMethod(c, "list");
        assertHasMethod(c, "getById");
        assertHasMethod(c, "deploy");
        assertHasMethod(c, "trigger");
        assertHasMethod(c, "autoExecute");

        // 必须有真实 ServiceImpl（不是 Stub）
        Class<?> implCls = findClassIfExists("com.meiyetec.service.impl.DcepSmartContractServiceImpl");
        assertNotNull(implCls, "缺少真实 ServiceImpl: DcepSmartContractServiceImpl");
        // Stub 必须被删除
        assertNull(findClassIfExists("com.meiyetec.service.impl.DcepSmartContractServiceStub"),
                "DcepSmartContractServiceStub 必须被移除");

        // ServiceImpl 必须覆盖 5 个核心方法
        assertHasMethod(implCls, "deployContract");
        assertHasMethod(implCls, "triggerContract");
        assertHasMethod(implCls, "autoExecuteExpired");
        assertHasMethod(implCls, "refundPrepaid");
    }

    @Test
    void bc_004_offline_tx_controller_and_impl_expose_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.DcepOfflineTransactionController");
        assertHasMethod(c, "list");
        assertHasMethod(c, "getById");
        assertHasMethod(c, "create");
        assertHasMethod(c, "sync");
        assertHasMethod(c, "detectDoubleSpend");

        Class<?> impl = findClassIfExists("com.meiyetec.service.impl.DcepOfflineTransactionServiceImpl");
        assertNotNull(impl);
        assertHasMethod(impl, "createOfflineTransaction");
        assertHasMethod(impl, "findByOfflineTxNo");
    }

    @Test
    void bc_required_entities_and_mappers_present() {
        assertNotNull(findClassIfExists("com.meiyetec.entity.BlockchainRecord"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.BlockchainChainBlock"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepSmartContract"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepOfflineTransaction"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepHardwareWallet"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepAntiDoubleSpendProof"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepContractExecutionLog"));

        assertNotNull(findClassIfExists("com.meiyetec.mapper.BlockchainRecordMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.BlockchainChainBlockMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.DcepSmartContractMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.DcepOfflineTransactionMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.DcepHardwareWalletMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.DcepAntiDoubleSpendProofMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.DcepContractExecutionLogMapper"));
    }

    @Test
    void bc_required_tables_exist_in_db() {
        String[] required = {
            "blockchain_record", "blockchain_chain_block",
            "biz_dcep_smart_contract", "biz_dcep_contract_execution_log",
            "biz_dcep_hardware_wallet", "biz_dcep_offline_transaction",
            "biz_dcep_anti_double_spend_proof"
        };
        for (String t : required) {
            assertEquals(true, tableExists(t), "BC 表不存在: " + t);
        }
    }

    @Test
    void bc_smart_contract_service_impl_is_real_not_stub() throws Exception {
        Class<?> implCls = Class.forName("com.meiyetec.service.impl.DcepSmartContractServiceImpl");
        Method deploy = implCls.getMethod("deployContract", String.class, String.class,
                Long.class, Long.class, java.math.BigDecimal.class,
                String.class, java.time.LocalDateTime.class);
        // 返回类型必须是 DcepSmartContract 而非 null
        assertEquals("com.meiyetec.entity.DcepSmartContract", deploy.getReturnType().getName());

        Method trigger = implCls.getMethod("triggerContract", Long.class, String.class);
        assertEquals(void.class, trigger.getReturnType());
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'meiyetec' AND table_name = ?",
                Integer.class, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=utf-8",
                    "root", "root123456"));

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }

    private Class<?> findClassIfExists(String fqn) {
        try { return Class.forName(fqn); } catch (ClassNotFoundException e) { return null; }
    }
}