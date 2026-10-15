package com.meiyetec.plugin;

/**
 * EXT-09：美业插件 SPI（所有插件必须实现）
 *
 * <p>本接口是"业务侧 SPI"，与 pf4j 的生命周期解耦。
 * 实际插件类继承 {@link MeiyetecPluginAdapter} 即可同时拿到 pf4j 生命周期钩子 + 本接口。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code pluginCode}：与 {@code plugin_market.plugin_code} 对齐，是平台侧唯一标识</li>
 *   <li>{@code version}：语义化版本号，启动时按 version 区分 classloader</li>
 *   <li>{@code displayName / description}：用于前端展示</li>
 * </ul>
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link #onStart(PluginRuntimeContext)} — load → enable 后调用，业务初始化</li>
 *   <li>{@link #onStop(PluginRuntimeContext)} — disable → unload 前调用，资源释放</li>
 * </ol>
 */
public interface MeiyetecPlugin {

    /** 平台侧唯一编码（与 plugin_market.plugin_code 对齐） */
    String pluginCode();

    /** 语义化版本号（启动时按 version 隔离 classloader） */
    String version();

    /** 前端展示名 */
    String displayName();

    /** 简介（≤500 字） */
    String description();

    /** 作者 / 提供方 */
    default String vendor() {
        return "unknown";
    }

    /** 插件被启用时回调（load → enable 后） */
    default void onStart(PluginRuntimeContext ctx) {}

    /** 插件被停用时回调（disable → unload 前） */
    default void onStop(PluginRuntimeContext ctx) {}
}
