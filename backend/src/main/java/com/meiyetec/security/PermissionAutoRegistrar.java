package com.meiyetec.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.SysPermission;
import com.meiyetec.mapper.SysPermissionMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 权限码自动注册器
 *
 * <p>启动期扫描所有 Controller 方法上的 {@link RequirePermission} 注解，
 * 把声明的权限码同步进 {@code sys_permission} 表（缺失则补，已存在则更新名称）。</p>
 *
 * <p>目的：</p>
 * <ul>
 *   <li>新 Controller 加 {@code @RequirePermission} 后无需手动 INSERT permission 记录</li>
 *   <li>运维侧可通过 {@code POST /sys/permission/sync} 触发一次重扫</li>
 *   <li>PermissionAspect 校验依赖 {@code sys_permission} 行存在 → 自动注册保证不漏</li>
 * </ul>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只插入缺失行；已存在行不覆盖 permissionCode（保持稳定）</li>
 *   <li>permissionName 默认取自模块名（Controller 路由前缀）</li>
 *   <li>permissionType = 'API'，status = 1</li>
 *   <li>启动失败不影响业务（异常仅 warn）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionAutoRegistrar {

    private final ApplicationContext ctx;
    private final SysPermissionMapper permissionMapper;

    /** 启动期自动跑一次（异步，不阻塞） */
    @PostConstruct
    public void init() {
        try {
            // 异步执行，避免拖慢启动
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    int n = scanAndRegister();
                    log.info("[PermAutoRegister] 启动期扫描完成，新增/更新 {} 个权限码", n);
                } catch (Exception e) {
                    log.warn("[PermAutoRegister] 启动期扫描异常：{}", e.getMessage());
                }
            }, "perm-auto-register");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            log.warn("[PermAutoRegister] 启动钩子注册失败：{}", e.getMessage());
        }
    }

    /**
     * 扫描所有 Controller 并注册缺失的权限码。
     *
     * @return 本次新增数量（更新不计）
     */
    public int scanAndRegister() {
        Map<String, Object> controllers = ctx.getBeansWithAnnotation(RestController.class);
        Map<String, Object> legacyControllers = ctx.getBeansWithAnnotation(Controller.class);

        Map<String, String> routePrefixByBean = new HashMap<>();
        Set<String> permissionCodes = new LinkedHashSet<>();

        collectPermissions(controllers, permissionCodes, routePrefixByBean);
        collectPermissions(legacyControllers, permissionCodes, routePrefixByBean);

        if (permissionCodes.isEmpty()) {
            log.debug("[PermAutoRegister] 未发现任何 @RequirePermission 声明");
            return 0;
        }

        // 查已存在的
        Set<String> existing = new HashSet<>();
        List<SysPermission> all = permissionMapper.selectList(null);
        for (SysPermission p : all) {
            if (p.getPermissionCode() != null) existing.add(p.getPermissionCode());
        }

        int added = 0;
        for (String code : permissionCodes) {
            if (existing.contains(code)) continue;
            SysPermission p = new SysPermission();
            p.setPermissionCode(code);
            // 解析名称：从 "module:action" 拆出模块
            String[] parts = code.split(":", 2);
            String module = parts.length > 0 ? parts[0] : code;
            String action = parts.length > 1 ? parts[1] : "";
            p.setPermissionName(action.isEmpty() ? module : (module + "·" + action));
            p.setPermissionType("API");
            p.setStatus(1);
            p.setSortOrder(0);
            try {
                permissionMapper.insert(p);
                added++;
                log.info("[PermAutoRegister] 注册新权限码：{}", code);
            } catch (Exception e) {
                log.warn("[PermAutoRegister] 注册 {} 失败：{}", code, e.getMessage());
            }
        }
        return added;
    }

    private void collectPermissions(Map<String, Object> beans,
                                    Set<String> codes,
                                    Map<String, String> routePrefix) {
        for (Map.Entry<String, Object> e : beans.entrySet()) {
            Object bean = e.getValue();
            Class<?> clazz = bean.getClass();
            // CGLIB 代理场景下取父类
            if (clazz.getName().contains("$$")) {
                clazz = clazz.getSuperclass();
            }
            if (clazz == null) continue;

            // 类级注解（兜底）
            RequirePermission classAnn = clazz.getAnnotation(RequirePermission.class);
            String[] classPerms = classAnn == null ? null : classAnn.value();

            // 路由前缀：取 @RequestMapping("/xxx")
            String prefix = "";
            org.springframework.web.bind.annotation.RequestMapping rm =
                    clazz.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
            if (rm != null && rm.value().length > 0) prefix = rm.value()[0];

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                RequirePermission ann = m.getAnnotation(RequirePermission.class);
                String[] targets = ann != null ? ann.value() : classPerms;
                if (targets == null) continue;
                for (String code : targets) {
                    if (code == null || code.isEmpty() || "*".equals(code)) continue;
                    codes.add(code);
                }
            }
        }
    }
}
