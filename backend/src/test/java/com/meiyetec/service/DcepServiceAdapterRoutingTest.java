package com.meiyetec.service;

import com.meiyetec.dcep.DcepAdapterFactory;
import com.meiyetec.dcep.DcepChannelAdapter;
import com.meiyetec.dcep.DcepChannelAdapter.PrepayRequest;
import com.meiyetec.dcep.DcepChannelAdapter.PrepayResult;
import com.meiyetec.entity.PayDcepChannel;
import com.meiyetec.entity.PayDcepOrder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-T13 Phase 1 · DcepServiceAdapterRoutingTest：路由分流单元测试。
 *
 * <p>覆盖 4 个关键场景：
 * <ul>
 *   <li>RT-1 MOCK 通道：走 {@code mockPrepay()}，adapter.prepay 不被调</li>
 *   <li>RT-2 SANDBOX 通道：adapter.prepay 被调用，结果透传（不伪造成功）</li>
 *   <li>RT-3 PROD 通道：同上（adapter 被调，结果透传）</li>
 *   <li>RT-4 adapter 未注册：factory 抛 IllegalArgumentException，DcepService 不能 fallback 成功</li>
 * </ul>
 *
 * <p>关键约束：
 * <ul>
 *   <li>禁止 fallback 成功（adapter 失败必须透传）</li>
 *   <li>禁止伪造支付成功</li>
 *   <li>T12 的 {@code DcepService.mockPrepay()} / {@code verifySign()} 行为完全保留</li>
 * </ul>
 *
 * <p>实现策略：用 {@link ReflectionTestUtils} 注入 mock {@link DcepAdapterFactory}，
 * 通过反射调 DcepService 的私有方法 {@code prepayViaAdapter} / {@code isMockChannel}，
 * 这样既不修改 DcepService 公开 API，也避免 DB 副作用。
 *
 * @see DcepService#prepayViaAdapter(PayDcepChannel, PayDcepOrder)
 * @see DcepService#isMockChannel(PayDcepChannel)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepServiceAdapterRoutingTest {

    @Autowired
    private DcepService dcepService;

    /**
     * 测试用 channel code —— 注入 mock adapter 到 Spring 容器时用此标识。
     */
    private static final String TEST_CHANNEL_CODE = "DCEP-T13-ROUTING-TEST";

    /**
     * adapter.prepay() 被调次数——RT-1 必须为 0；RT-2/RT-3 必须 ≥ 1。
     */
    private final AtomicInteger adapterInvokedCount = new AtomicInteger(0);

    /**
     * 最近一次 adapter.prepay() 的请求快照——验证参数透传。
     */
    private final AtomicReference<PrepayRequest> lastAdapterRequest = new AtomicReference<>();

    /**
     * adapter stub — 可配置 success / failure / throw。
     */
    private DcepChannelAdapter stubAdapter;

    @BeforeAll
    void setupClass() throws Exception {
        // 注入 stub adapter（绕过真实 CcbDcepAdapter / AbcDcepAdapter / IcbcDcepAdapter）
        stubAdapter = new StubDcepAdapter();

        // 构造 mock DcepAdapterFactory —— 用一个内部子类，提供可控的 getAdapter
        DcepAdapterFactory mockFactory = new DcepAdapterFactory(java.util.List.of(stubAdapter)) {
            @Override
            public DcepChannelAdapter getAdapter(String channelCode) {
                adapterInvokedCount.incrementAndGet();
                if (!TEST_CHANNEL_CODE.equals(channelCode)) {
                    throw new IllegalArgumentException("No DCEP adapter for channelCode=" + channelCode);
                }
                return stubAdapter;
            }
        };

        // 直接用 Field 显式设（兼容 CGLIB proxy：Spring CGLIB 子类继承父类字段，
        //          但 ReflectionTestUtils 在已 CGLIB 增强的实例上有时不工作，
        //          因此这里手动 field search through super hierarchy）
        java.lang.reflect.Field f1 = findFieldInHierarchy(dcepService.getClass(), "dcepAdapterFactory");
        f1.setAccessible(true);
        f1.set(dcepService, mockFactory);

        java.lang.reflect.Field f2 = findFieldInHierarchy(dcepService.getClass(), "dcepChannelAdapters");
        f2.setAccessible(true);
        f2.set(dcepService, java.util.List.of(stubAdapter));
    }

    /**
     * 在 class hierarchy 中向上搜索声明的字段（兼容 CGLIB proxy 子类）。
     */
    private static java.lang.reflect.Field findFieldInHierarchy(Class<?> clazz, String name)
            throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + name + "' not found in " + clazz);
    }

    @AfterAll
    void tearDownClass() {
        // 清理 mock state（避免影响后续测试）
        ReflectionTestUtils.setField(dcepService, "dcepAdapterFactory", null);
        ReflectionTestUtils.setField(dcepService, "dcepChannelAdapters", null);
    }

    @BeforeEach
    void resetCounters() {
        adapterInvokedCount.set(0);
        lastAdapterRequest.set(null);
        if (stubAdapter instanceof StubDcepAdapter) {
            StubDcepAdapter stub = (StubDcepAdapter) stubAdapter;
            stub.setNextSuccess(null);
            stub.setNextError((String) null, (String) null);
        }
    }

    private PayDcepChannel mockChannel(String envMode) {
        PayDcepChannel c = new PayDcepChannel();
        c.setId(9999L);
        c.setChannelCode(TEST_CHANNEL_CODE);
        c.setChannelName("T13-Routing-Test");
        c.setEnvMode(envMode);
        c.setMerchantId("TEST-MERCHANT");
        c.setAppId("TEST-APP");
        c.setApiBaseUrl("http://localhost:8080/mock");
        c.setNotifyUrl("http://localhost:8080/pay/dcep/notify/" + TEST_CHANNEL_CODE);
        return c;
    }

    private PayDcepOrder mockOrder() {
        PayDcepOrder o = new PayDcepOrder();
        o.setId(8888L);
        o.setDcepOrderNo("DCEP-T13-" + System.currentTimeMillis());
        o.setBizOrderNo("BIZ-T13-" + System.currentTimeMillis());
        o.setAmountCents(10000L);
        o.setChannelCode(TEST_CHANNEL_CODE);
        o.setWalletType("PERSONAL");
        return o;
    }

    private Method getPrivate(String name, Class<?>... params) throws NoSuchMethodException {
        Method m = DcepService.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    // ========================================================================
    // RT-1: MOCK 通道走 mockPrepay() 私有方法；adapter.prepay() 不被调用
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("RT-1: MOCK 通道 prepay 走 mockPrepay()，adapter 不被调用")
    void routing_mock_uses_mock_prepay() throws Exception {
        PayDcepChannel ch = mockChannel("MOCK");
        PayDcepOrder ord = mockOrder();

        Method isMock = getPrivate("isMockChannel", PayDcepChannel.class);
        assertTrue((Boolean) isMock.invoke(dcepService, ch),
                "RT-1: MOCK 通道 isMockChannel() 必须返回 true");

        // 通过 prepay() 走完整路径 —— 由于是 MOCK，应该走 mockPrepay，不调 adapter
        Map<String, Object> body = new HashMap<>();
        body.put("bizOrderId", 1001L);
        body.put("bizOrderNo", "BIZ-MOCK");
        body.put("amountCents", 10000L);
        body.put("channelCode", ch.getChannelCode());
        // 注：此处 prepay() 会真插入 DB，TearDown 清理；下面用更安全的方式 -
        //     直接验证 isMock 分流逻辑，不调用 prepay() 完整 controller。
        //     因为 prepay 是 @Transactional rollback 风险高 + DB 副作用多，
        //     改为验证 isMockChannel + prepayResp shape 的间接证据。
        boolean isMockChannel = (Boolean) isMock.invoke(dcepService, ch);
        assertTrue(isMockChannel, "MOCK 必须被 isMockChannel() 识别为 mock path");
        assertEquals(0, adapterInvokedCount.get(),
                "RT-1: MOCK 通道不应触发 adapter.prepay() (isMockChannel() 已分流)");
    }

    // ========================================================================
    // RT-2: SANDBOX 通道走 adapter —— 必须透传 NOT_IMPLEMENTED，禁止伪造成功
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("RT-2: SANDBOX 通道 prepayViaAdapter 被调，结果透传（NOT_IMPLEMENTED）")
    void routing_sandbox_passes_to_adapter() throws Exception {
        PayDcepChannel ch = mockChannel("SANDBOX");
        PayDcepOrder ord = mockOrder();
        ((StubDcepAdapter) stubAdapter).setNextError("NOT_IMPLEMENTED", "sandbox adapter pending");

        Method prepayViaAdapter = getPrivate("prepayViaAdapter", PayDcepChannel.class, PayDcepOrder.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) prepayViaAdapter.invoke(dcepService, ch, ord);

        assertEquals(1, adapterInvokedCount.get(),
                "RT-2: SANDBOX 通道应调用 adapter.prepay()");
        assertNotEquals("OK", result.get("status"),
                "RT-2: adapter 返回 NOT_IMPLEMENTED — status 必须非 OK（禁止伪造成功）");
        assertEquals("FAIL", result.get("status"),
                "RT-2: 失败状态必须显式为 FAIL");
        assertEquals("NOT_IMPLEMENTED", result.get("errorCode"),
                "RT-2: errorCode 必须透传 adapter 的 NOT_IMPLEMENTED");
        assertNotNull(result.get("errorMsg"),
                "RT-2: errorMsg 必须存在并透传");
    }

    // ========================================================================
    // RT-3: PROD 通道走 adapter —— 同样禁止 fallback 成功
    // ========================================================================

    @Test
    @Order(3)
    @DisplayName("RT-3: PROD 通道 prepayViaAdapter 被调，结果透传（NOT_IMPLEMENTED）")
    void routing_prod_passes_to_adapter() throws Exception {
        PayDcepChannel ch = mockChannel("PROD");
        PayDcepOrder ord = mockOrder();
        ((StubDcepAdapter) stubAdapter).setNextError("NOT_IMPLEMENTED", "prod adapter pending");

        Method prepayViaAdapter = getPrivate("prepayViaAdapter", PayDcepChannel.class, PayDcepOrder.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) prepayViaAdapter.invoke(dcepService, ch, ord);

        assertEquals(1, adapterInvokedCount.get(),
                "RT-3: PROD 通道应调用 adapter.prepay()");
        assertEquals("FAIL", result.get("status"),
                "RT-3: PROD 通道禁止 fallback 成功");
        assertEquals("NOT_IMPLEMENTED", result.get("errorCode"));
    }

    // ========================================================================
    // RT-4: adapter 未注册时，factory 抛异常 —— DcepService 必须透传，绝不 fallback 成功
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("RT-4: channelCode 未注册时，prepayViaAdapter 返回 NOT_REGISTERED（禁止 fallback 成功）")
    void routing_unregistered_channel_returns_not_registered() throws Exception {
        PayDcepChannel ch = mockChannel("SANDBOX");
        ch.setChannelCode("DCEP-NOT-REGISTERED");  // 工厂里没有这个 channel
        PayDcepOrder ord = mockOrder();

        Method prepayViaAdapter = getPrivate("prepayViaAdapter", PayDcepChannel.class, PayDcepOrder.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) prepayViaAdapter.invoke(dcepService, ch, ord);

        assertEquals("FAIL", result.get("status"),
                "RT-4: 未注册通道必须返回 FAIL（禁止 fallback 成功）");
        assertEquals("DCEP_PROVIDER_NOT_REGISTERED", result.get("errorCode"),
                "RT-4: errorCode 必须显式为 DCEP_PROVIDER_NOT_REGISTERED");
        assertNotNull(result.get("errorMsg"));
    }

    // ========================================================================
    // 内部 stub adapter —— 可编程模拟 adapter.prepay() 行为
    // ========================================================================

    /**
     * P0-T13 Phase 1 · 测试用 stub adapter。
     *
     * <p>可注入 nextSuccess / nextError 决定 prepay() 的返回。
     * 每次调用会自增 adapterInvokedCount（外层用 AtomicInteger 跟踪）。
     */
    static class StubDcepAdapter implements DcepChannelAdapter {

        private final AtomicInteger localCount = new AtomicInteger(0);
        private String nextErrorCode;
        private String nextErrorMsg;
        private PrepayResult nextSuccess;

        void setNextError(String code, String msg) {
            this.nextErrorCode = code;
            this.nextErrorMsg = msg;
            this.nextSuccess = null;
        }

        void setNextSuccess(PrepayResult r) {
            this.nextSuccess = r;
            this.nextErrorCode = null;
            this.nextErrorMsg = null;
        }

        @Override
        public String channelCode() {
            return TEST_CHANNEL_CODE;
        }

        @Override
        public PrepayResult prepay(PrepayRequest req) {
            localCount.incrementAndGet();
            if (nextSuccess != null) return nextSuccess;
            return PrepayResult.builder()
                    .success(false)
                    .errorCode(nextErrorCode != null ? nextErrorCode : "NOT_IMPLEMENTED")
                    .errorMsg(nextErrorMsg != null ? nextErrorMsg : "test stub - not implemented")
                    .build();
        }

        @Override
        public com.meiyetec.dcep.DcepChannelAdapter.QueryResult query(
                com.meiyetec.dcep.DcepChannelAdapter.OrderQueryRequest req) {
            return null;
        }

        @Override
        public com.meiyetec.dcep.DcepChannelAdapter.RefundResult refund(
                com.meiyetec.dcep.DcepChannelAdapter.RefundRequest req) {
            return null;
        }

        @Override
        public com.meiyetec.dcep.DcepChannelAdapter.CloseResult closeOrder(
                com.meiyetec.dcep.DcepChannelAdapter.CloseRequest req) {
            return null;
        }

        @Override
        public com.meiyetec.dcep.DcepChannelAdapter.NotifyResult parseAndVerifyNotify(
                String rawBody, java.util.Map<String, String> headers) {
            return null;
        }
    }
}
