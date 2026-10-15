package com.meiyetec.payment.bnpl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BNPL Provider 路由器 — 自动收集所有 {@link BnplProvider} 实现，
 * 按 {@code providerCode} 分发请求。
 *
 * <p>注入方式：
 * <pre>
 * BnplProvider p = router.get("KLARNA");
 * BnplProvider p = router.get("AFTERPAY");
 * </pre>
 *
 * <p>降级逻辑：
 * <ul>
 *   <li>请求的 provider 在路由表中找不到 → 返回 Mock（保持向后兼容）</li>
 *   <li>请求的 provider 找到但 {@code isConfigured() == false} → 返回 Mock</li>
 * </ul>
 */
@Slf4j
@Component
public class BnplProviderRouter {

    private final List<BnplProvider> providers;
    private final Map<String, BnplProvider> registry = new LinkedHashMap<>();

    public BnplProviderRouter(List<BnplProvider> providers) {
        this.providers = providers;
    }

    @PostConstruct
    public void init() {
        for (BnplProvider p : providers) {
            String code = p.providerCode().toUpperCase();
            if (registry.putIfAbsent(code, p) != null) {
                log.warn("[BNPL-ROUTER] Duplicate provider code '{}' from {}, skipping",
                        code, p.getClass().getSimpleName());
            } else {
                log.info("[BNPL-ROUTER] Registered provider: {} ({}) isConfigured={}",
                        code, p.getClass().getSimpleName(), p.isConfigured());
            }
        }
        log.info("[BNPL-ROUTER] Initialized with {} providers: {}",
                registry.size(), registry.keySet());
    }

    /**
     * 按 provider code 获取 provider 实例。
     * 未找到或未配置时返回 Mock 作为 fallback。
     *
     * @param providerCode KLARNA / AFTERPAY / ATOME / AFFIRM / ...
     * @return 匹配的 provider（永远不会返回 null）
     */
    public BnplProvider get(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getMock();
        }
        String code = providerCode.toUpperCase();
        BnplProvider p = registry.get(code);
        if (p == null) {
            log.warn("[BNPL-ROUTER] No provider for '{}', falling back to Mock", code);
            return getMock();
        }
        if (!p.isConfigured()) {
            log.warn("[BNPL-ROUTER] Provider '{}' not configured, falling back to Mock", code);
            return getMock();
        }
        return p;
    }

    /**
     * 列出所有已注册的 provider 及配置状态。
     */
    public Map<String, Boolean> listProviders() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, BnplProvider> e : registry.entrySet()) {
            result.put(e.getKey(), e.getValue().isConfigured());
        }
        return result;
    }

    /**
     * 获取真实已配置的 provider（不 fallback）
     */
    public Optional<BnplProvider> getIfConfigured(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) return Optional.empty();
        BnplProvider p = registry.get(providerCode.toUpperCase());
        if (p != null && p.isConfigured()) return Optional.of(p);
        return Optional.empty();
    }

    private MockBnplProvider getMock() {
        // 惰性构造 mock（不在构造时创建，避免依赖循环）
        return new MockBnplProvider();
    }
}
