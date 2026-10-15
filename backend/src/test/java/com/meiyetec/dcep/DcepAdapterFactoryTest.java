package com.meiyetec.dcep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-001 后续 · 适配器工厂测试
 *
 * <p>验证：
 * <ul>
 *   <li>3 个银行适配器都被 Spring 扫描并注入到工厂</li>
 *   <li>工厂能根据 channelCode 路由到正确实现</li>
 *   <li>未知 channelCode 抛出 IllegalArgumentException</li>
 * </ul>
 */
@SpringBootTest
class DcepAdapterFactoryTest {

    @Autowired
    private DcepAdapterFactory factory;

    @Test
    @DisplayName("FACTORY-1: 工厂能找到 3 个银行适配器")
    void factory_has_three_adapters() {
        assertEquals(3, factory.listChannelCodes().size());
        assertTrue(factory.listChannelCodes().contains("DCEP-CCB"));
        assertTrue(factory.listChannelCodes().contains("DCEP-ICBC"));
        assertTrue(factory.listChannelCodes().contains("DCEP-ABC"));
    }

    @Test
    @DisplayName("FACTORY-2: 按 channelCode 路由正确")
    void factory_routes_correctly() {
        DcepChannelAdapter ccb = factory.getAdapter("DCEP-CCB");
        assertEquals("DCEP-CCB", ccb.channelCode());
        assertTrue(ccb instanceof com.meiyetec.dcep.impl.CcbDcepAdapter);

        DcepChannelAdapter icbc = factory.getAdapter("DCEP-ICBC");
        assertEquals("DCEP-ICBC", icbc.channelCode());
        assertTrue(icbc instanceof com.meiyetec.dcep.impl.IcbcDcepAdapter);

        DcepChannelAdapter abc = factory.getAdapter("DCEP-ABC");
        assertEquals("DCEP-ABC", abc.channelCode());
        assertTrue(abc instanceof com.meiyetec.dcep.impl.AbcDcepAdapter);
    }

    @Test
    @DisplayName("FACTORY-3: 未知 channelCode 抛出 IllegalArgumentException")
    void factory_unknown_channel_throws() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.getAdapter("DCEP-UNKNOWN-BANK")
        );
        assertTrue(ex.getMessage().contains("DCEP-UNKNOWN-BANK"));
        assertTrue(ex.getMessage().contains("available"));
    }

    @Test
    @DisplayName("FACTORY-4: 路由后的适配器调用 prepay 返回 NOT_IMPLEMENTED（骨架版本）")
    void routed_adapter_returns_not_implemented() {
        DcepChannelAdapter.PrepayResult result = factory.getAdapter("DCEP-CCB").prepay(
            DcepChannelAdapter.PrepayRequest.builder()
                .channelCode("DCEP-CCB")
                .dcepOrderNo("DCEP-FACTORY-001")
                .amountCents(10000L)
                .envMode("PROD")
                .build()
        );
        assertFalse(result.isSuccess());
        assertEquals("NOT_IMPLEMENTED", result.getErrorCode());
    }
}
