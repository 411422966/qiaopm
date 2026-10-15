package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-03/04 阶段 B：DcepOperatorRegistry 单元测试
 */
@DisplayName("DCEP-03/04 阶段 B DcepOperatorRegistry")
class DcepOperatorRegistryTest {

    @Test
    @DisplayName("channelCode 'DCEP-ICBC' → operatorKey 'ICBC'")
    void operatorKey_uppercase() {
        assertEquals("ICBC", DcepOperatorRegistry.operatorKey("DCEP-ICBC"));
        assertEquals("CCB", DcepOperatorRegistry.operatorKey("DCEP-CCB"));
        assertEquals("ABC", DcepOperatorRegistry.operatorKey("DCEP-ABC"));
    }

    @Test
    @DisplayName("channelCode 无 'DCEP-' 前缀时也大写后返回")
    void operatorKey_noPrefix() {
        assertEquals("ICBC", DcepOperatorRegistry.operatorKey("ICBC"));
        assertEquals("ICBC", DcepOperatorRegistry.operatorKey("icbc"));
        assertEquals("ICBC", DcepOperatorRegistry.operatorKey("Icbc"));
    }

    @Test
    @DisplayName("channelCode 为 null 时返回 null")
    void operatorKey_null() {
        assertNull(DcepOperatorRegistry.operatorKey(null));
    }

    @Test
    @DisplayName("isEnabled / shouldUseSandbox / lookup")
    void basicLookup() {
        DcepOperatorProperties props = new DcepOperatorProperties();
        Map<String, Operator> map = new HashMap<>();
        Operator icbc = new Operator();
        icbc.setEnabled(true);
        icbc.setEnvMode("SANDBOX");
        icbc.setProtocol("REST");
        icbc.setBaseUrl("https://open.icbc.com.cn");
        icbc.setMerchantId("123");
        map.put("ICBC", icbc);

        Operator ccb = new Operator();
        ccb.setEnabled(false);
        ccb.setMerchantId("456");
        map.put("CCB", ccb);

        props.setOperators(map);

        DcepOperatorRegistry reg = new DcepOperatorRegistry(props);

        assertTrue(reg.isEnabled("DCEP-ICBC"));
        assertFalse(reg.isEnabled("DCEP-CCB"));
        assertFalse(reg.isEnabled("DCEP-UNKNOWN"));

        assertFalse(reg.shouldUseSandbox("DCEP-ICBC"));
        assertTrue(reg.shouldUseSandbox("DCEP-CCB"));
        assertTrue(reg.shouldUseSandbox("DCEP-UNKNOWN"));

        assertNotNull(reg.lookup("DCEP-ICBC"));
        assertNotNull(reg.lookup("DCEP-CCB"));
        assertNull(reg.lookup("DCEP-UNKNOWN"));
    }

    @Test
    @DisplayName("listAllOperators / listEnabledOperators")
    void listOperators() {
        DcepOperatorProperties props = new DcepOperatorProperties();
        Map<String, Operator> map = new HashMap<>();
        for (String k : new String[]{"ICBC", "CCB", "ABC"}) {
            Operator op = new Operator();
            op.setEnabled("ICBC".equals(k));
            map.put(k, op);
        }
        props.setOperators(map);

        DcepOperatorRegistry reg = new DcepOperatorRegistry(props);
        assertEquals(3, reg.listAllOperators().size());
        assertEquals(1, reg.listEnabledOperators().size());
        assertTrue(reg.listEnabledOperators().contains("ICBC"));
    }

    @Test
    @DisplayName("healthCheckReport 输出包含机构标识")
    void healthReport() {
        DcepOperatorProperties props = new DcepOperatorProperties();
        Map<String, Operator> map = new HashMap<>();
        Operator op = new Operator();
        op.setEnabled(true);
        op.setEnvMode("PROD");
        op.setProtocol("REST");
        op.setBaseUrl("https://api.example.com");
        op.setMerchantId("M100");
        map.put("ICBC", op);
        props.setOperators(map);

        DcepOperatorRegistry reg = new DcepOperatorRegistry(props);
        String report = reg.healthCheckReport();
        assertTrue(report.contains("ICBC"));
        assertTrue(report.contains("enabled=true"));
        assertTrue(report.contains("env=PROD"));
        assertTrue(report.contains("protocol=REST"));
    }
}
