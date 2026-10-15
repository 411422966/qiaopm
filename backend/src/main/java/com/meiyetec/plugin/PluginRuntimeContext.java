package com.meiyetec.plugin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * EXT-09：插件运行时上下文
 *
 * <p>由 {@link PluginRuntimeService} 在每次生命周期回调时构造，携带：
 * <ul>
 *   <li>租户隔离所需的最小信息（pluginCode + version）</li>
 *   <li>插件配置 JSON（来自 {@code plugin_install.config_json} 或启动参数）</li>
 *   <li>运行时通用 KV（plugin → host 通信，例如数据库连接句柄）</li>
 * </ul>
 *
 * <p>注意：context 是不可变的快照，插件不应缓存。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PluginRuntimeContext {

    /** 当前租户 ID（系统级插件传 0） */
    private Long tenantId;

    /** 插件编码（与 plugin_market.plugin_code 对齐） */
    private String pluginCode;

    /** 当前启用的版本 */
    private String version;

    /** 插件配置 JSON（来自 plugin_install.config_json） */
    private String configJson;

    /** 主机侧共享 KV（DB / Redis / 业务 Service 引用，由 PluginRuntimeService 注入） */
    private Map<String, Object> attributes = new HashMap<>();

    /** 写入一个属性（链式） */
    public PluginRuntimeContext attr(String key, Object value) {
        if (attributes == null) attributes = new HashMap<>();
        attributes.put(key, value);
        return this;
    }

    /** 读取一个属性（无值返回 null） */
    @SuppressWarnings("unchecked")
    public <T> T attr(String key) {
        return attributes == null ? null : (T) attributes.get(key);
    }
}
