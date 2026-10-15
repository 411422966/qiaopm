package com.meiyetec.plugin;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * EXT-09：插件抽象基类（桥接 pf4j 生命周期 ↔ 业务 SPI）
 *
 * <p>所有具体插件只需继承本类并实现 {@link MeiyetecPlugin} 的业务方法：
 * <pre>{@code
 * public class HelloPlugin extends MeiyetecPluginAdapter {
 *     public String pluginCode()  { return "hello"; }
 *     public String version()     { return "1.0.0"; }
 *     public String displayName() { return "Hello 插件"; }
 *     public String description() { return "演示插件"; }
 *     public void onStart(PluginRuntimeContext ctx) {
 *         // 业务初始化
 *     }
 * }
 * }</pre>
 *
 * <p>pf4j 会在 META-INF/pf4j/plugin.properties 里声明 plugin.class=本类全限定名。
 */
public abstract class MeiyetecPluginAdapter extends Plugin implements MeiyetecPlugin {

    public MeiyetecPluginAdapter(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        PluginRuntimeContext ctx = new PluginRuntimeContext();
        ctx.setPluginCode(pluginCode());
        ctx.setVersion(version());
        onStart(ctx);
    }

    @Override
    public void stop() {
        PluginRuntimeContext ctx = new PluginRuntimeContext();
        ctx.setPluginCode(pluginCode());
        ctx.setVersion(version());
        onStop(ctx);
    }

    /**
     * pf4j 3.x：Plugin#getPluginId() 是 final，不能 override。
     * <p>pluginId 由 wrapper 提供（来自 META-INF/pf4j/plugin.properties 的 id 字段）。
     * 如需自定义 pluginId，请通过 plugin.properties 配置。
     */
}
