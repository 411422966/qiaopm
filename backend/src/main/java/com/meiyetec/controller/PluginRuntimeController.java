package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.PluginInstall;
import com.meiyetec.entity.PluginRuntimeState;
import com.meiyetec.exception.BizCode;
import com.meiyetec.plugin.PluginPersistenceService;
import com.meiyetec.plugin.PluginRuntimeInfo;
import com.meiyetec.plugin.PluginRuntimeService;
import com.meiyetec.plugin.PluginTenantService;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EXT-09 + EXT-23：插件运行时数据看板 + 启停端点
 *
 * <p>基础路径：{@code /plugin/runtime}
 * <ul>
 *   <li>GET    /list                              — 列出所有已注册插件（含状态）</li>
 *   <li>GET    /dashboard                         — 摘要统计</li>
 *   <li>POST   /{pluginCode}/load                 — 加载（body: {version?: "x.y.z"}）</li>
 *   <li>POST   /{pluginCode}/enable               — 启用（平台级）</li>
 *   <li>POST   /{pluginCode}/disable              — 停用（平台级）</li>
 *   <li>POST   /{pluginCode}/unload               — 卸载</li>
 * </ul>
 *
 * <p>EXT-23 租户级端点（叠加平台级之上）：
 * <ul>
 *   <li>GET    /tenant/{tenantId}/list            — 某租户的插件视图（含 plugin_install.enabled）</li>
 *   <li>POST   /tenant/{tenantId}/{pluginCode}/enable  — 租户级启用</li>
 *   <li>POST   /tenant/{tenantId}/{pluginCode}/disable — 租户级停用</li>
 *   <li>GET    /persisted                         — 持久化状态记录（跨重启可观测）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/plugin/runtime")
@RequiredArgsConstructor
public class PluginRuntimeController {

    private final PluginRuntimeService runtime;
    private final PluginTenantService tenantService;
    private final PluginPersistenceService persistenceService;

    /**
     * 跨租户访问校验（EXT-23 P1）：只允许当前租户用户或平台超管（dataScope=ALL）访问指定 tenantId。
     * <p>返回 {@code null} 表示校验通过；返回非空 {@link Result} 表示应立即作为 403 返回。
     */
    private Result<Void> assertTenantAccess(Long tenantId) {
        if (tenantId == null) return null;
        String dataScope = TenantContextHolder.get().getDataScope();
        if ("ALL".equals(dataScope)) return null;            // 平台超管放行
        Long current = TenantContextHolder.get().getTenantId();
        if (current != null && current.equals(tenantId)) return null;
        log.warn("[PLUGIN-RUNTIME] 跨租户访问被拒绝: pathTenantId={}, currentTenantId={}, dataScope={}",
                tenantId, current, dataScope);
        return Result.error(BizCode.FORBIDDEN, "跨租户访问被拒绝");
    }

    /** 数据看板列表 */
    @GetMapping("/list")
    @RequirePermission("plugin:runtime:list")
    public Result<List<PluginRuntimeInfo>> list() {
        return Result.success(runtime.listPlugins());
    }

    /** 摘要统计 */
    @GetMapping("/dashboard")
    @RequirePermission("plugin:runtime:list")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(runtime.dashboard());
    }

    /** 加载指定版本（不指定则取最大版本号目录） */
    @PostMapping("/{pluginCode}/load")
    @RequirePermission("plugin:runtime:edit")
    public Result<PluginRuntimeInfo> load(@PathVariable String pluginCode,
                                          @RequestBody(required = false) Map<String, Object> body) {
        String version = body == null ? null
                : (body.get("version") == null ? null : body.get("version").toString());
        PluginRuntimeInfo info = runtime.loadPlugin(pluginCode, version);
        if ("NOT_FOUND".equals(info.getState())) {
            return Result.error(BizCode.NOT_FOUND, "插件不存在: " + pluginCode);
        }
        persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
                info.getState(), info.getErrorMessage());
        return Result.success("已加载", info);
    }

    /** 启用 */
    @PostMapping("/{pluginCode}/enable")
    @RequirePermission("plugin:runtime:edit")
    public Result<PluginRuntimeInfo> enable(@PathVariable String pluginCode) {
        PluginRuntimeInfo info = runtime.enablePlugin(pluginCode);
        if ("NOT_FOUND".equals(info.getState())) {
            return Result.error(BizCode.NOT_FOUND, "插件未加载: " + pluginCode);
        }
        return Result.success("已启用", info);
    }

    /** 停用 */
    @PostMapping("/{pluginCode}/disable")
    @RequirePermission("plugin:runtime:edit")
    public Result<PluginRuntimeInfo> disable(@PathVariable String pluginCode) {
        PluginRuntimeInfo info = runtime.disablePlugin(pluginCode);
        if ("NOT_FOUND".equals(info.getState())) {
            return Result.error(BizCode.NOT_FOUND, "插件未加载: " + pluginCode);
        }
        return Result.success("已停用", info);
    }

    /** 卸载 */
    @PostMapping("/{pluginCode}/unload")
    @RequirePermission("plugin:runtime:edit")
    public Result<PluginRuntimeInfo> unload(@PathVariable String pluginCode) {
        PluginRuntimeInfo info = runtime.unloadPlugin(pluginCode);
        if ("NOT_FOUND".equals(info.getState())) {
            return Result.error(BizCode.NOT_FOUND, "插件未加载: " + pluginCode);
        }
        return Result.success("已卸载", info);
    }

    // ============================================================
    //  EXT-23：租户级端点
    // ============================================================

    /**
     * 列出某租户已注册的插件（含平台级 + 该租户专属 enabled 标记）。
     */
    @GetMapping("/tenant/{tenantId}/list")
    @RequirePermission("plugin:runtime:list")
    public Result<List<Map<String, Object>>> listTenant(@PathVariable Long tenantId) {
        Result<Void> deny = assertTenantAccess(tenantId);
        if (deny != null) return Result.error(deny.getCode(), deny.getMessage());
        List<PluginRuntimeInfo> platform = runtime.listPlugins();
        List<PluginInstall> tenantInstalls = tenantService.listByTenant(tenantId);
        Map<String, Integer> flagMap = new java.util.HashMap<>();
        for (PluginInstall pi : tenantInstalls) {
            flagMap.put(pi.getPluginCode(), pi.getEnabled());
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (PluginRuntimeInfo p : platform) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pluginCode", p.getPluginCode());
            row.put("pluginId", p.getPluginId());
            row.put("version", p.getVersion());
            row.put("platformState", p.getState());
            row.put("displayName", p.getDisplayName());
            row.put("tenantEnabled", flagMap.getOrDefault(p.getPluginCode(), 1));
            row.put("effectiveEnabled", runtime.isEnabledForTenant(p.getPluginCode(), tenantId));
            row.put("lastError", p.getErrorMessage());
            out.add(row);
        }
        return Result.success(out);
    }

    /** 租户级启用 */
    @PostMapping("/tenant/{tenantId}/{pluginCode}/enable")
    @RequirePermission("plugin:runtime:tenant:edit")
    public Result<Map<String, Object>> enableForTenant(@PathVariable Long tenantId,
                                                      @PathVariable String pluginCode) {
        Result<Void> deny = assertTenantAccess(tenantId);
        if (deny != null) return Result.error(deny.getCode(), deny.getMessage());
        boolean ok = tenantService.setTenantEnabled(pluginCode, tenantId, true);
        if (!ok) return Result.error(BizCode.SYSTEM_ERROR, "写入失败");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pluginCode", pluginCode);
        data.put("tenantId", tenantId);
        data.put("tenantEnabled", true);
        data.put("effectiveEnabled", runtime.isEnabledForTenant(pluginCode, tenantId));
        return Result.success("已启用", data);
    }

    /** 租户级停用 */
    @PostMapping("/tenant/{tenantId}/{pluginCode}/disable")
    @RequirePermission("plugin:runtime:tenant:edit")
    public Result<Map<String, Object>> disableForTenant(@PathVariable Long tenantId,
                                                       @PathVariable String pluginCode) {
        Result<Void> deny = assertTenantAccess(tenantId);
        if (deny != null) return Result.error(deny.getCode(), deny.getMessage());
        boolean ok = tenantService.setTenantEnabled(pluginCode, tenantId, false);
        if (!ok) return Result.error(BizCode.SYSTEM_ERROR, "写入失败");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pluginCode", pluginCode);
        data.put("tenantId", tenantId);
        data.put("tenantEnabled", false);
        data.put("effectiveEnabled", runtime.isEnabledForTenant(pluginCode, tenantId));
        return Result.success("已停用", data);
    }

    /** 持久化状态查询（跨重启可观测） */
    @GetMapping("/persisted")
    @RequirePermission("plugin:runtime:list")
    public Result<List<PluginRuntimeState>> persisted(
            @RequestParam(required = false) Long tenantId) {
        if (tenantId != null) {
            Result<Void> deny = assertTenantAccess(tenantId);
            if (deny != null) return Result.error(deny.getCode(), deny.getMessage());
            return Result.success(persistenceService.listByTenant(tenantId));
        }
        return Result.success(persistenceService.restoreStates());
    }
}