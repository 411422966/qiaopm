package com.meiyetec.service;

import com.meiyetec.config.DcepConfigProperties;
import com.meiyetec.dcep.DcepChannelAdapter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-T12 · DCEP 启动校验测试
 *
 * <p>覆盖三类场景：
 * <ul>
 *   <li>VALIDATE-1: 非法 mode（"test"/空串/"xxx"）必须抛 {@link BeanInitializationException}</li>
 *   <li>VALIDATE-2: dev mode 启动校验通过 — {@link DcepService#isDcepRuntimeReady()}=true</li>
 *   <li>VALIDATE-3: prod mode + 全部 adapter 返回 NOT_IMPLEMENTED → log ERROR 且 runtime=false，<b>不抛异常</b>（方案 A：log + 标记，不阻断 CI）</li>
 * </ul>
 *
 * <p>按 P0-T12 决策 D2：测试不强制重新启动 Spring Context，
 * 通过反射注入 {@link DcepConfigProperties#setMode(String)} 后调用
 * {@link DcepService#validateRuntimeCapability()} 直接触发校验逻辑。</p>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepStartupValidateTest {

    @Autowired
    private DcepConfigProperties dcepConfig;

    @Autowired
    private DcepService dcepService;

    @Autowired(required = false)
    private List<DcepChannelAdapter> dcepChannelAdapters;

    @AfterEach
    void reset() {
        dcepConfig.setMode("dev");
    }

    // ========================================================================
    // VALIDATE-1: 非法 mode 必须抛 BeanInitializationException
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("VALIDATE-1a: 非法 mode 'test' 必须抛 BeanInitializationException")
    void validate_invalid_mode_test_throws() {
        DcepConfigProperties probe = new DcepConfigProperties();
        probe.setMode("test");
        BeanInitializationException ex = assertThrows(
                BeanInitializationException.class,
                probe::validate,
                "非法 mode 'test' 必须抛 BeanInitializationException");
        assertTrue(ex.getMessage().contains("test"),
                "异常消息应包含原始非法值 'test'");
        assertTrue(ex.getMessage().contains("dev") && ex.getMessage().contains("prod"),
                "异常消息应列出合法值");
    }

    @Test
    @Order(2)
    @DisplayName("VALIDATE-1b: 非法 mode 'xxx' 必须抛异常")
    void validate_invalid_mode_xxx_throws() {
        DcepConfigProperties probe = new DcepConfigProperties();
        probe.setMode("xxx");
        assertThrows(BeanInitializationException.class, probe::validate);
    }

    @Test
    @Order(3)
    @DisplayName("VALIDATE-1c: 空字符串必须抛异常")
    void validate_empty_mode_throws() {
        DcepConfigProperties probe = new DcepConfigProperties();
        probe.setMode("");
        assertThrows(BeanInitializationException.class, probe::validate);
    }

    @Test
    @Order(4)
    @DisplayName("VALIDATE-1d: null 必须抛异常")
    void validate_null_mode_throws() {
        DcepConfigProperties probe = new DcepConfigProperties();
        probe.setMode(null);
        assertThrows(BeanInitializationException.class, probe::validate);
    }

    @Test
    @Order(5)
    @DisplayName("VALIDATE-1e: 空白字符串必须抛异常")
    void validate_blank_mode_throws() {
        DcepConfigProperties probe = new DcepConfigProperties();
        probe.setMode("   ");
        assertThrows(BeanInitializationException.class, probe::validate);
    }

    // ========================================================================
    // VALIDATE-2: dev / sandbox 模式启动校验通过
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("VALIDATE-2a: dev 模式启动校验通过，runtime ready=true")
    void validate_dev_passes() {
        dcepConfig.setMode("dev");
        // 触发运行时校验（不是 @PostConstruct 阶段，因为 Spring Context 已启动）
        ReflectionTestUtils.invokeMethod(dcepService, "validateRuntimeCapability");
        assertTrue(dcepService.isDcepRuntimeReady(),
                "dev 模式 runtime 应 ready=true（不要求真实 SDK）");
    }

    @Test
    @Order(11)
    @DisplayName("VALIDATE-2b: sandbox 模式启动校验通过，runtime ready=true")
    void validate_sandbox_passes() {
        dcepConfig.setMode("sandbox");
        ReflectionTestUtils.invokeMethod(dcepService, "validateRuntimeCapability");
        assertTrue(dcepService.isDcepRuntimeReady(),
                "sandbox 模式 runtime 应 ready=true");
    }

    // ========================================================================
    // VALIDATE-3: prod 模式 + 全部 adapter NOT_IMPLEMENTED
    // ========================================================================

    @Test
    @Order(20)
    @DisplayName("VALIDATE-3: prod 模式 + 全部 adapter NOT_IMPLEMENTED → log ERROR + runtime=false + 不抛异常")
    void validate_prod_all_notimpl_logs_error_not_throws() {
        dcepConfig.setMode("prod");
        // 当前默认 3 个 adapter 都是占位 NOT_IMPLEMENTED（除非有真实 SDK 已对接）
        // 调用 validateRuntimeCapability 应不抛异常
        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(dcepService, "validateRuntimeCapability"),
                "P0-T12 方案 A：prod+全 NOT_IMPLEMENTED 必须不抛异常（仅 log ERROR）");

        // 当前 Spring 容器中 3 个占位 adapter 都返回 NOT_IMPLEMENTED，
        // 因此 dcepRuntimeReady 应被置为 false。
        // 注意：若未来 T13 完成真实 SDK 接入，本测试可能需要调整。
        assertFalse(dcepService.isDcepRuntimeReady(),
                "prod 模式 + 全部 NOT_IMPLEMENTED 应使 runtime=false");
    }
}