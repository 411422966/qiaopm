package com.meiyetec.plugin;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * EXT-23：插件沙箱 — 包级白名单策略
 *
 * <p>设计目标：
 * <ol>
 *   <li>插件不得直接访问宿主内部类（{@code com.meiyetec.service.internal.*} 等）</li>
 *   <li>插件可调用的"宿主公共 API"通过 {@link #HOST_PUBLIC_PACKAGES} 白名单放行</li>
 *   <li>插件不得 import 危险包（如 {@code java.lang.reflect.*}、{@code java.io.FileSystem}）</li>
 * </ol>
 *
 * <p>实现方式（轻量级，不依赖 SecurityManager — Java 17 已弃用）：
 * <ul>
 *   <li>{@link #assertPackageAllowed(String, String)}：在 {@link MeiyetecPlugin#onStart} / 业务调用前主动调用</li>
 *   <li>{@link #assertCallerAllowed(Class)}：校验发起方的 classloader 链路是否包含宿主内部包</li>
 * </ul>
 *
 * <p>调用方契约：
 * <pre>{@code
 *   // 在插件 onStart 中：
 *   PluginSecurityPolicy.assertPackageAllowed(pluginCode, "my.plugin.pkg");
 *
 *   // 在宿主业务代码调用插件入口前：
 *   PluginSecurityPolicy.assertCallerAllowed(MyExtension.class);
 * }</pre>
 *
 * <p>注意：这是"合作式沙箱"，插件作者主动调用 {@code assertPackageAllowed} 才生效。
 * 若插件直接反射加载 {@code com.meiyetec.service.*} 类，会被 {@link #assertCallerAllowed} 拦截。
 */
@Slf4j
public final class PluginSecurityPolicy {

    /** 宿主允许插件访问的公共包前缀（白名单） */
    public static final List<String> HOST_PUBLIC_PACKAGES = List.of(
            "com.meiyetec.plugin.",   // SPI / 上下文
            "com.meiyetec.util.",     // Result / BizCode
            "com.meiyetec.exception."
    );

    /** 严禁插件访问的宿主包前缀（黑名单 — 比白名单优先级高） */
    public static final List<String> HOST_FORBIDDEN_PACKAGES = List.of(
            "com.meiyetec.service.",            // ⭐ I020：顶级 service 包整体拦截（覆盖 service.UserService 等非 internal/impl 类）
            "com.meiyetec.service.internal.",
            "com.meiyetec.service.impl.",
            "com.meiyetec.security.",
            "com.meiyetec.mapper.",
            "com.meiyetec.config.",
            "com.meiyetec.plugin.PluginRuntimeService",   // 自身也不允许反射拿
            "com.meiyetec.plugin.PluginPersistenceService",
            "com.meiyetec.plugin.PluginTenantService"
    );

    /** 插件严禁 import 的危险 JDK 包前缀 */
    public static final List<String> FORBIDDEN_JDK_PACKAGES = List.of(
            "java.lang.reflect.",
            "java.io.FileSystem",
            "java.lang.Runtime",
            "java.lang.Process",
            "java.nio.file.Files",
            "java.net.URLClassLoader",
            "sun.misc.",
            "sun.reflect.",
            "jdk.internal."
    );

    private PluginSecurityPolicy() {}

    /**
     * 校验某个包前缀是否在白名单中（用于插件 onStart 自检）。
     *
     * @param pluginCode 插件编码（仅日志）
     * @param pkgPrefix  待校验的包前缀
     * @throws SecurityException 不在白名单时抛出
     */
    public static void assertPackageAllowed(String pluginCode, String pkgPrefix) {
        if (pkgPrefix == null || pkgPrefix.isEmpty()) return;
        // 黑名单优先
        for (String forbidden : HOST_FORBIDDEN_PACKAGES) {
            if (pkgPrefix.startsWith(forbidden)) {
                throw new SecurityException("[PLUGIN-SECURITY] plugin=" + pluginCode
                        + " tried to access forbidden package: " + forbidden);
            }
        }
        for (String forbidden : FORBIDDEN_JDK_PACKAGES) {
            if (pkgPrefix.startsWith(forbidden)) {
                throw new SecurityException("[PLUGIN-SECURITY] plugin=" + pluginCode
                        + " tried to use forbidden JDK API: " + forbidden);
            }
        }
        // 白名单（仅对 com.meiyetec.* 生效；其它业务包默认放行 — 插件自有代码域）
        if (pkgPrefix.startsWith("com.meiyetec.")) {
            boolean inWhitelist = HOST_PUBLIC_PACKAGES.stream().anyMatch(pkgPrefix::startsWith);
            if (!inWhitelist) {
                throw new SecurityException("[PLUGIN-SECURITY] plugin=" + pluginCode
                        + " host package not in whitelist: " + pkgPrefix);
            }
        }
        log.debug("[PLUGIN-SECURITY] package allowed: plugin={} pkg={}", pluginCode, pkgPrefix);
    }

    /**
     * 校验调用方的 classloader 链路是否包含宿主内部类（防止插件通过反射访问）。
     *
     * @param caller 实际发起调用的 Class 对象
     * @throws SecurityException 调用方 classloader 链路含 com.meiyetec 时抛出
     */
    public static void assertCallerAllowed(Class<?> caller) {
        if (caller == null) return;
        // 检查 caller 类本身是否在 com.meiyetec.* — 这种调用一般是宿主自己，OK
        String callerName = caller.getName();
        if (!callerName.startsWith("com.meiyetec.") && !callerName.startsWith("org.pf4j.")) {
            // 第三方 / 插件调用方，检查其 classloader 是否能加载宿主内部类
            try {
                ClassLoader cl = caller.getClassLoader();
                if (cl != null) {
                    // ⭐ I020 fix：探测 service 顶级包 — 用真实类 StoreService 作为探测目标
                    // 原 probe 仅测 service.internal.NonExistent（fictional 类），永远 CNFE，无法生效
                    // 现用真实类：若 classloader 能加载 → caller 能访问 service.* → 拒绝（沙箱阻断）
                    try {
                        Class.forName("com.meiyetec.service.StoreService", false, cl);
                        // 加载成功 → caller 能访问 com.meiyetec.service.* → 抛出 SecurityException
                        throw new SecurityException("[PLUGIN-SECURITY] caller=" + callerName
                                + " rejected: can load forbidden host package com.meiyetec.service.* (I020)");
                    } catch (ClassNotFoundException expected) {
                        // 找不到 → OK（caller 不能加载 service.*）
                    } catch (NoClassDefFoundError expected) {
                        // 依赖缺失（NoClassDefFoundError） → 也算 OK（不能完整加载）
                    } catch (LinkageError expected) {
                        // 其他链接错误 → OK
                    }

                    // 探测 internal 包（原逻辑 — 找到 → warn；保留作 fallback 检测）
                    try {
                        Class.forName("com.meiyetec.service.internal.NonExistent",
                                false, cl);
                    } catch (ClassNotFoundException expected) {
                        // 找不到是预期的，说明 classloader 不能加载宿主内部类
                        return;
                    } catch (Exception ignored) {
                        // SecurityException / NoClassDefFoundError 也算 OK
                        return;
                    }
                    // 如果找到（说明 internal 包暴露了），报警
                    log.warn("[PLUGIN-SECURITY] caller {} can load host internal classes; "
                            + "verify plugin trust level", callerName);
                }
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                log.debug("[PLUGIN-SECURITY] assertCallerAllowed check skipped: {}", e.getMessage());
            }
        }
    }

    /**
     * 校验一个 pluginCode 是否被允许加载（即处于沙箱黑名单中）。
     * 用于插件 install 时的初步筛查。
     */
    public static Set<String> deniedPackages() {
        return Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList(
                        "java.lang.reflect.*",
                        "sun.misc.*",
                        "com.meiyetec.service.internal.*",
                        "com.meiyetec.security.*"
                )));
    }
}
