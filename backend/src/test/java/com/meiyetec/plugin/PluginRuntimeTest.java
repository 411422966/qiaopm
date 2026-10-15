package com.meiyetec.plugin;

import com.meiyetec.plugin.PluginRuntimeService.PluginRuntimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginState;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-09：插件运行时单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>MeiyetecPlugin SPI 接口契约：必要方法齐全</li>
 *   <li>PluginRuntimeInfo DTO 工厂方法 + PluginState 映射</li>
 *   <li>PluginRuntimeService 禁用模式：list / dashboard / load / enable / disable / unload 全部安全返回</li>
 *   <li>PluginRuntimeContext：attr 链式读写</li>
 *   <li>PluginRuntimeProperties 配置默认值</li>
 * </ul>
 */
@DisplayName("EXT-09 Plugin Runtime")
class PluginRuntimeTest {

    private PluginRuntimeProperties props;

    @BeforeEach
    void setUp() {
        props = new PluginRuntimeProperties();
    }

    // ==================== SPI 接口契约 ====================

    @Test
    @DisplayName("MeiyetecPlugin 接口：必要方法齐全")
    void spiContract() {
        Class<?> c = MeiyetecPlugin.class;
        assertHasMethod(c, "pluginCode");
        assertHasMethod(c, "version");
        assertHasMethod(c, "displayName");
        assertHasMethod(c, "description");
        assertHasMethod(c, "onStart");
        assertHasMethod(c, "onStop");
    }

    // ==================== DTO 工厂方法 ====================

    @Test
    @DisplayName("PluginRuntimeInfo.notFound() / disabled() 工厂")
    void dtoFactories() {
        PluginRuntimeInfo nf = PluginRuntimeInfo.notFound("missing-plugin");
        assertEquals("missing-plugin", nf.getPluginCode());
        assertEquals("NOT_FOUND", nf.getState());
        assertNotNull(nf.getErrorMessage());

        PluginRuntimeInfo dis = PluginRuntimeInfo.disabled("config off");
        assertEquals("RUNTIME_DISABLED", dis.getState());
        assertEquals("config off", dis.getErrorMessage());
    }

    @Test
    @DisplayName("PluginRuntimeInfo.toPluginStateOrNull() 映射合法 PluginState")
    void pluginStateMapping() {
        PluginRuntimeInfo info = new PluginRuntimeInfo();
        info.setState("STARTED");
        assertEquals(PluginState.STARTED, info.toPluginStateOrNull());

        info.setState("DISABLED");
        assertEquals(PluginState.DISABLED, info.toPluginStateOrNull());

        info.setState("NOT_FOUND");
        assertNull(info.toPluginStateOrNull(), "非法状态返回 null");

        info.setState(null);
        assertNull(info.toPluginStateOrNull(), "null 状态返回 null");
    }

    // ==================== 服务禁用模式 ====================

    @Test
    @DisplayName("禁用模式：listPlugins 返回 RUNTIME_DISABLED 标记")
    void listPluginsWhenDisabled() {
        props.setEnabled(false);
        PluginRuntimeService svc = new PluginRuntimeService(props);
        // 注意：不要调用 init()，避免触发 pf4j 启动
        List<PluginRuntimeInfo> result = svc.listPlugins();
        assertEquals(1, result.size());
        assertEquals("RUNTIME_DISABLED", result.get(0).getState());
    }

    @Test
    @DisplayName("禁用模式：dashboard 返回 total=0 / started=0")
    void dashboardWhenDisabled() {
        props.setEnabled(false);
        PluginRuntimeService svc = new PluginRuntimeService(props);
        Map<String, Object> data = svc.dashboard();
        assertEquals(false, data.get("runtimeEnabled"));
        assertEquals(0, ((Number) data.get("total")).intValue());
        assertEquals(0, ((Number) data.get("started")).intValue());
        assertEquals(0, ((Number) data.get("failed")).intValue());
    }

    @Test
    @DisplayName("禁用模式：load / enable / disable / unload 都安全返回 disabled 标记")
    void lifecycleWhenDisabled() {
        props.setEnabled(false);
        PluginRuntimeService svc = new PluginRuntimeService(props);

        PluginRuntimeInfo load = svc.loadPlugin("any", "1.0.0");
        assertEquals("RUNTIME_DISABLED", load.getState());

        PluginRuntimeInfo en = svc.enablePlugin("any");
        assertEquals("RUNTIME_DISABLED", en.getState());

        PluginRuntimeInfo dis = svc.disablePlugin("any");
        assertEquals("RUNTIME_DISABLED", dis.getState());

        PluginRuntimeInfo un = svc.unloadPlugin("any");
        assertEquals("RUNTIME_DISABLED", un.getState());
    }

    @Test
    @DisplayName("loadPlugin(null code)：抛 IllegalArgumentException")
    void loadPluginNullCode() {
        props.setEnabled(true);
        PluginRuntimeService svc = new PluginRuntimeService(props);
        // 不调 init()，仅触发校验：ensureManager 才会被调用
        assertThrows(IllegalArgumentException.class, () -> svc.loadPlugin(null, "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> svc.loadPlugin("", "1.0.0"));
    }

    // ==================== PluginRuntimeContext ====================

    @Test
    @DisplayName("PluginRuntimeContext：attr 链式读写")
    void contextAttr() {
        PluginRuntimeContext ctx = new PluginRuntimeContext();
        ctx.setTenantId(7L);
        ctx.setPluginCode("hello");
        ctx.setVersion("1.0.0");
        ctx.attr("dbHandle", "jdbc:mysql://x").attr("weight", 100);

        assertEquals(7L, ctx.getTenantId());
        assertEquals("hello", ctx.getPluginCode());
        assertEquals("1.0.0", ctx.getVersion());
        assertEquals("jdbc:mysql://x", ctx.<String>attr("dbHandle"));
        assertEquals(100, ctx.<Integer>attr("weight"));
        assertNull(ctx.attr("nope"));
    }

    // ==================== 配置默认值 ====================

    @Test
    @DisplayName("PluginRuntimeProperties 默认值：enabled=true / autoLoad=true / pluginsRoot=./plugins")
    void propertiesDefaults() {
        PluginRuntimeProperties p = new PluginRuntimeProperties();
        assertTrue(p.isEnabled());
        assertTrue(p.isAutoLoadOnStartup());
        assertEquals("./plugins", p.getPluginsRoot());
    }

    // ==================== helpers ====================

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }
}
