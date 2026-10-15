package com.meiyetec.plugin;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EXT-09：插件运行时服务（架构级）
 *
 * <p>职责：
 * <ol>
 *   <li>启动时初始化 pf4j {@link DefaultPluginManager}，按 {@code plugins/{pluginCode}/{version}/} 扫描</li>
 *   <li>对每个插件暴露 load / enable / disable / unload 生命周期</li>
 *   <li>提供数据看板 DTO（PluginRuntimeInfo）供 REST 返回</li>
 * </ol>
 *
 * <p>插件 ID 约定：{@code {pluginCode}@{version}}（pf4j 内部用，外部用 pluginCode）
 *
 * <p>配置（application.yml）：
 * <pre>{@code
 * plugin:
 *   runtime:
 *     enabled: true                    # 总开关
 *     plugins-root: ./plugins          # 插件 jar 根目录
 *     auto-load-on-startup: true       # 启动时自动 load + enable
 * }</pre>
 */
@Slf4j
@Service
public class PluginRuntimeService implements ApplicationRunner {

    private final PluginRuntimeProperties props;
    private final PluginPersistenceService persistenceService;
    private final PluginTenantService tenantService;

    /** pf4j 插件管理器（懒加载，启动期 + 第一次 list 时初始化） */
    private volatile PluginManager pluginManager;

    public PluginRuntimeService(PluginRuntimeProperties props,
                                PluginPersistenceService persistenceService,
                                PluginTenantService tenantService) {
        this.props = props;
        this.persistenceService = persistenceService;
        this.tenantService = tenantService;
    }

    // ============================================================
    //  初始化
    // ============================================================

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("[PLUGIN] 插件运行时已禁用（plugin.runtime.enabled=false），仅暴露 REST 元数据");
            return;
        }
        ensureManager();
        File root = new File(props.getPluginsRoot());
        if (!root.exists()) {
            // 自动创建目录，方便首次部署
            boolean ok = root.mkdirs();
            if (ok) log.info("[PLUGIN] 自动创建插件根目录：{}", root.getAbsolutePath());
            else log.warn("[PLUGIN] 插件根目录不存在且创建失败：{}", root.getAbsolutePath());
        }
        log.info("[PLUGIN] 插件运行时已就绪，根目录：{}", root.getAbsolutePath());
    }

    /** 应用启动后自动加载（auto-load-on-startup=true 时） */
    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled() || !props.isAutoLoadOnStartup()) return;
        try {
            int loaded = loadAllFromDisk();
            log.info("[PLUGIN] 启动期自动加载完成，发现 {} 个插件", loaded);
            // 启动期把已加载插件写入持久化（覆盖 FAILED / UNLOADED 残留）
            for (PluginRuntimeInfo info : listPlugins()) {
                persistenceService.recordState(0L, info.getPluginCode(), info.getVersion(),
                        info.getPluginId(), info.getState(), info.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("[PLUGIN] 启动期自动加载失败：{}", e.getMessage());
        }
    }

    /** 懒加载 + 线程安全单例 */
    private PluginManager ensureManager() {
        PluginManager pm = pluginManager;
        if (pm == null) {
            synchronized (this) {
                if (pluginManager == null) {
                    File root = new File(props.getPluginsRoot());
                    pluginManager = new DefaultPluginManager(root.toPath());
                }
                pm = pluginManager;
            }
        }
        return pm;
    }

    /** 把 plugins-root 下的所有 {pluginCode}/{version}/*.jar 全部 load + start */
    public int loadAllFromDisk() {
        PluginManager pm = ensureManager();
        File root = new File(props.getPluginsRoot());
        if (!root.isDirectory()) return 0;
        File[] codeDirs = root.listFiles(File::isDirectory);
        if (codeDirs == null) return 0;
        int total = 0;
        for (File codeDir : codeDirs) {
            File[] versionDirs = codeDir.listFiles(File::isDirectory);
            if (versionDirs == null) continue;
            for (File vDir : versionDirs) {
                try {
                    PluginRuntimeInfo info = loadPluginFromPath(codeDir.getName(), vDir.getName(), vDir.toPath());
                    if (info != null) {
                        // load 之后立刻 enable
                        enablePluginInternal(info.getPluginCode());
                        total++;
                    }
                } catch (Exception e) {
                    log.warn("[PLUGIN] 自动加载 {}/{} 失败：{}", codeDir.getName(), vDir.getName(), e.getMessage());
                }
            }
        }
        return total;
    }

    // ============================================================
    //  对外 API
    // ============================================================

    /**
     * 数据看板：列出所有已注册的插件（即使未 enable 也列出，状态 = CREATED）
     */
    public List<PluginRuntimeInfo> listPlugins() {
        if (!props.isEnabled()) {
            return List.of(PluginRuntimeInfo.disabled("plugin.runtime.enabled=false"));
        }
        PluginManager pm = ensureManager();
        List<PluginWrapper> wrappers = pm.getPlugins();
        List<PluginRuntimeInfo> out = new ArrayList<>(wrappers.size());
        for (PluginWrapper w : wrappers) {
            out.add(toInfo(w));
        }
        return out;
    }

    /**
     * 加载指定插件（按 pluginCode + version），返回加载后的运行时信息
     *
     * @param pluginCode 业务编码
     * @param version    语义化版本号，传 null 表示"自动选最新版本"
     */
    public PluginRuntimeInfo loadPlugin(String pluginCode, String version) {
        if (!props.isEnabled()) return PluginRuntimeInfo.disabled("plugin.runtime.enabled=false");
        if (pluginCode == null || pluginCode.isEmpty()) {
            throw new IllegalArgumentException("pluginCode 必填");
        }
        File dir = resolvePluginDir(pluginCode, version);
        if (!dir.isDirectory()) {
            return PluginRuntimeInfo.notFound(pluginCode);
        }
        return loadPluginFromPath(pluginCode, dir.getName(), dir.toPath());
    }

    /** 启用已加载的插件 */
    public PluginRuntimeInfo enablePlugin(String pluginCode) {
        if (!props.isEnabled()) return PluginRuntimeInfo.disabled("plugin.runtime.enabled=false");
        PluginRuntimeInfo info = enablePluginInternal(pluginCode);
        persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
                info.getState(), info.getErrorMessage());
        return info;
    }

    /** 停用插件（不卸载 classloader） */
    public PluginRuntimeInfo disablePlugin(String pluginCode) {
        if (!props.isEnabled()) return PluginRuntimeInfo.disabled("plugin.runtime.enabled=false");
        PluginManager pm = ensureManager();
        String pluginId = resolvePluginId(pluginCode);
        PluginWrapper w = pm.getPlugin(pluginId);
        if (w == null) return PluginRuntimeInfo.notFound(pluginCode);
        try {
            // pf4j 3.x: disablePlugin() returns boolean
            boolean ok = pm.disablePlugin(pluginId);
            log.info("[PLUGIN] 禁用完成：pluginCode={} ok={}", pluginCode, ok);
        } catch (Exception e) {
            log.warn("[PLUGIN] 禁用失败：pluginCode={} err={}", pluginCode, e.getMessage());
        }
        PluginRuntimeInfo info = toInfo(pm.getPlugin(pluginId));
        // ⭐ I022 fix：disable 时若旧 lastError 非空，保留旧错误（避免覆盖 FAILED 错误的审计线索）
        // 仅在新 state=DISABLED 时生效；其他状态保持 recordState 默认语义
        String preservedError = info.getErrorMessage();
        if ("DISABLED".equals(info.getState())) {
            String oldError = persistenceService.getLastError(0L, pluginCode);
            if (oldError != null && !oldError.isEmpty()) {
                preservedError = oldError;
                log.info("[PLUGIN] disablePlugin 保留旧 lastError（I022 审计保留）：pluginCode={} oldError={}",
                        pluginCode, oldError);
            }
        }
        persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
                info.getState(), preservedError);
        return info;
    }

    /** 卸载插件（释放 classloader） */
    public PluginRuntimeInfo unloadPlugin(String pluginCode) {
        if (!props.isEnabled()) return PluginRuntimeInfo.disabled("plugin.runtime.enabled=false");
        PluginManager pm = ensureManager();
        String pluginId = resolvePluginId(pluginCode);
        PluginWrapper w = pm.getPlugin(pluginId);
        if (w == null) return PluginRuntimeInfo.notFound(pluginCode);
        try {
            pm.unloadPlugin(pluginId);
            log.info("[PLUGIN] 卸载完成：pluginCode={}", pluginCode);
            // 卸载 = 持久化状态清掉（保留 last_error 便于审计）
            persistenceService.recordState(0L, pluginCode, null, pluginId, "UNLOADED", null);
            return new PluginRuntimeInfo();
        } catch (Exception e) {
            log.warn("[PLUGIN] 卸载失败：pluginCode={} err={}", pluginCode, e.getMessage());
            PluginRuntimeInfo info = toInfo(pm.getPlugin(pluginId));
            persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
                    info.getState(), info.getErrorMessage());
            return info;
        }
    }

    /**
     * 数据看板：摘要（总数 / 已启用 / 已禁用 / 失败 / 已卸载）
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runtimeEnabled", props.isEnabled());
        data.put("pluginsRoot", new File(props.getPluginsRoot()).getAbsolutePath());
        if (!props.isEnabled()) {
            data.put("total", 0);
            data.put("started", 0);
            data.put("disabled", 0);
            data.put("failed", 0);
            return data;
        }
        List<PluginRuntimeInfo> all = listPlugins();
        data.put("total", all.size());
        data.put("started", all.stream().filter(i -> "STARTED".equals(i.getState())).count());
        data.put("created", all.stream().filter(i -> "CREATED".equals(i.getState())).count());
        data.put("disabled", all.stream().filter(i -> "DISABLED".equals(i.getState())).count());
        data.put("failed", all.stream().filter(i -> "FAILED".equals(i.getState())).count());
        data.put("stopped", all.stream().filter(i -> "STOPPED".equals(i.getState())).count());
        // EXT-23：叠加持久化摘要
        try {
            List<com.meiyetec.entity.PluginRuntimeState> persisted = persistenceService.restoreStates();
            data.put("persistedRecords", persisted.size());
            long failed = persisted.stream().filter(p -> "FAILED".equals(p.getState())).count();
            data.put("lastKnownFailed", failed);
        } catch (Exception ignored) {}
        return data;
    }

    /**
     * EXT-23：查询某插件在 pf4j 内存里的当前状态（用于租户级判断 + 调试）。
     */
    public PluginRuntimeInfo getByCode(String pluginCode) {
        if (!props.isEnabled()) return PluginRuntimeInfo.disabled("plugin.runtime.enabled=false");
        PluginManager pm = ensureManager();
        String pluginId = resolvePluginId(pluginCode);
        PluginWrapper w = pm.getPlugin(pluginId);
        return w == null ? PluginRuntimeInfo.notFound(pluginCode) : toInfo(w);
    }

    /**
     * EXT-23：判断某插件对某租户是否真正启用（叠加 pf4j 状态 + tenant flag）。
     * <p>业务代码调用插件入口前应使用本方法。
     */
    public boolean isEnabledForTenant(String pluginCode, Long tenantId) {
        if (pluginCode == null) return false;
        PluginRuntimeInfo info = getByCode(pluginCode);
        return tenantService.isEnabledForTenant(pluginCode, tenantId, info.getState());
    }

    /** 提供给 controller / 其它 Service 访问租户 service */
    public PluginTenantService tenantService() {
        return tenantService;
    }

    /** 提供给 controller / 其它 Service 访问持久化 service */
    public PluginPersistenceService persistenceService() {
        return persistenceService;
    }

    // ============================================================
    //  内部实现
    // ============================================================

    /** 通过 pluginCode 解析出 pf4j pluginId：pluginCode@version */
    private String resolvePluginId(String pluginCode) {
        if (pluginCode == null) return null;
        if (pluginCode.contains("@")) return pluginCode;   // 已是 pluginId
        // 找到已注册 wrapper 中匹配 pluginCode 的，取其 pluginId
        PluginManager pm = ensureManager();
        for (PluginWrapper w : pm.getPlugins()) {
            if (pluginCode.equals(extractPluginCode(w.getPluginId()))) {
                return w.getPluginId();
            }
        }
        return pluginCode + "@unknown";
    }

    private static String extractPluginCode(String pluginId) {
        if (pluginId == null) return null;
        int at = pluginId.lastIndexOf('@');
        return at >= 0 ? pluginId.substring(0, at) : pluginId;
    }

    /** 把 plugins-root 下 {pluginCode}/{version}/ 解析为 File，未指定 version 时挑最大版本号目录 */
    private File resolvePluginDir(String pluginCode, String version) {
        File codeDir = new File(props.getPluginsRoot(), pluginCode);
        if (!codeDir.isDirectory()) return new File(props.getPluginsRoot(), "_missing_");
        if (version != null && !version.isEmpty()) {
            return new File(codeDir, version);
        }
        File[] versions = codeDir.listFiles(File::isDirectory);
        if (versions == null || versions.length == 0) return new File(props.getPluginsRoot(), "_missing_");
        // 按目录名字典序倒序排（依赖 semver 风格 "x.y.z"），挑第一个
        Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
        return versions[0];
    }

    /** 从指定目录加载插件（调用 pf4j.loadPlugin + 自定义 pluginId 映射） */
    private PluginRuntimeInfo loadPluginFromPath(String pluginCode, String version, Path path) {
        PluginManager pm = ensureManager();
        try {
            String pluginId = pluginCode + "@" + version;
            // pf4j 会扫描目录下所有 jar 并按 META-INF/pf4j/plugin.properties 解析 pluginId
            String resolvedId = pm.loadPlugin(path);
            // 如果 pf4j 解析出来的 pluginId 不是约定的 pluginCode@version，重命名为约定值
            if (!pluginId.equals(resolvedId)) {
                log.info("[PLUGIN] 加载 {} 时 pluginId={}，将作为别名使用", pluginCode, resolvedId);
            }
            return toInfo(pm.getPlugin(resolvedId));
        } catch (Exception e) {
            log.warn("[PLUGIN] 加载失败：pluginCode={} version={} err={}", pluginCode, version, e.getMessage());
            PluginRuntimeInfo info = PluginRuntimeInfo.notFound(pluginCode);
            info.setVersion(version);
            info.setErrorMessage(e.getMessage());
            return info;
        }
    }

    private PluginRuntimeInfo enablePluginInternal(String pluginCode) {
        PluginManager pm = ensureManager();
        String pluginId = resolvePluginId(pluginCode);
        PluginWrapper w = pm.getPlugin(pluginId);
        if (w == null) return PluginRuntimeInfo.notFound(pluginCode);
        try {
            // pf4j 3.x: enablePlugin() returns boolean
            boolean ok = pm.enablePlugin(pluginId);
            log.info("[PLUGIN] 启用完成：pluginCode={} ok={}", pluginCode, ok);
        } catch (Exception e) {
            log.warn("[PLUGIN] 启用失败：pluginCode={} err={}", pluginCode, e.getMessage());
        }
        return toInfo(pm.getPlugin(pluginId));
    }

    /** 从 PluginWrapper 转 DTO（注入 SPI 字段） */
    private PluginRuntimeInfo toInfo(PluginWrapper w) {
        if (w == null) return null;
        PluginRuntimeInfo info = new PluginRuntimeInfo();
        info.setPluginId(w.getPluginId());
        info.setPluginCode(extractPluginCode(w.getPluginId()));
        info.setVersion(w.getDescriptor() != null ? w.getDescriptor().getVersion() : "unknown");
        info.setState(w.getPluginState() == null ? "UNKNOWN" : w.getPluginState().name());
        // pf4j 3.x: PluginWrapper#getPluginClassName() 不存在；用 getDescriptor().getPluginClass()
        info.setClassName(w.getDescriptor() != null ? w.getDescriptor().getPluginClass() : null);
        info.setPluginPath(w.getPluginPath() != null ? w.getPluginPath().toString() : null);
        info.setErrorMessage(w.getFailedException() != null ? w.getFailedException().getMessage() : null);

        // 如果插件实现了 MeiyetecPlugin，注入展示字段
        try {
            org.pf4j.Plugin plugin = w.getPlugin();
            if (plugin instanceof MeiyetecPlugin mp) {
                info.setDisplayName(mp.displayName());
                info.setDescription(mp.description());
                info.setVendor(mp.vendor());
            }
        } catch (Exception ignored) {
            // getPlugin() 在 FAILED 状态下可能抛异常，忽略
        }
        return info;
    }

    /** 配置属性绑定 */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "plugin.runtime")
    public static class PluginRuntimeProperties {
        private boolean enabled = true;
        private String pluginsRoot = "./plugins";
        private boolean autoLoadOnStartup = true;
    }
}
