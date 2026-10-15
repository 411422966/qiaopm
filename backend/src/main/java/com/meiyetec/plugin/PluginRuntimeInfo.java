package com.meiyetec.plugin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pf4j.PluginState;

/**
 * EXT-09：插件运行时信息（数据看板 DTO）
 *
 * <p>字段对齐 pf4j PluginWrapper + MeiyetecPlugin SPI；
 * 状态字段来自 pf4j PluginState：CREATED / STARTED / STOPPED / FAILED / DISABLED。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PluginRuntimeInfo {

    /** pf4j 内部 pluginId（默认 = pluginCode@version） */
    private String pluginId;

    /** 业务侧编码（与 plugin_market.plugin_code 对齐） */
    private String pluginCode;

    /** 当前版本 */
    private String version;

    /** 显示名 */
    private String displayName;

    /** 简介 */
    private String description;

    /** 供应商 */
    private String vendor;

    /** 状态：CREATED / STARTED / STOPPED / FAILED / DISABLED */
    private String state;

    /** 插件主类 FQN */
    private String className;

    /** 插件 jar / 解压根路径（plugins/{pluginCode}/{version}/） */
    private String pluginPath;

    /** 最近一次错误信息（FAILED 时填） */
    private String errorMessage;

    /** 是否被本服务自动加载（启动时） */
    private Boolean autoLoaded;

    public static PluginRuntimeInfo notFound(String pluginCode) {
        PluginRuntimeInfo info = new PluginRuntimeInfo();
        info.setPluginCode(pluginCode);
        info.setState("NOT_FOUND");
        info.setErrorMessage("plugin not registered");
        return info;
    }

    public static PluginRuntimeInfo disabled(String reason) {
        PluginRuntimeInfo info = new PluginRuntimeInfo();
        info.setState("RUNTIME_DISABLED");
        info.setErrorMessage(reason);
        return info;
    }

    public PluginState toPluginStateOrNull() {
        if (state == null) return null;
        try { return PluginState.valueOf(state); } catch (Exception e) { return null; }
    }
}
