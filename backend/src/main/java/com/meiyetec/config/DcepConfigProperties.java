package com.meiyetec.config;

import lombok.Data;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * P0-MONEY-001：DCEP 环境配置
 *
 * <p>通过 application.yml 的 {@code app.dcep.mode} 切换：
 * <ul>
 *   <li>{@code dev} — Mock 模式，mockPrepay 直接成功，verifySign 接受 MOCK- 前缀签名（默认）</li>
 *   <li>{@code prod} — 严格模式，mockPrepay 返回 FAIL/NOT_IMPLEMENTED，verifySign 拒绝 MOCK- 前缀</li>
 *   <li>{@code sandbox} — 沙箱模式，对接银行沙箱（暂未实现，等价于 prod + 待对接）</li>
 * </ul>
 *
 * <p>使用方式：在 application.yml 中设置 {@code app.dcep.mode: dev} 或 {@code prod}。</p>
 *
 * <p>默认值 {@code dev}，确保开发环境开箱即用；生产部署时必须显式设置为 {@code prod}。</p>
 *
 * <p>采用 {@code @Component + @ConfigurationProperties} 让 Bean 在 Spring 启动时立即初始化，
 * 避免被 {@code @EnableConfigurationProperties} 延迟初始化导致的构造器注入 null 问题。</p>
 *
 * <p>P0-T12：{@code @PostConstruct validate()} 强制 {@code mode} 必须是显式合法值，
 * 非法值（如空串、"test"、未知字符串）将抛 {@link BeanInitializationException} 阻断 Spring 启动。
 * 不允许静默 fallback。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.dcep")
public class DcepConfigProperties {

    /**
     * 允许的 mode 集合（P0-T12 引入）。顺序固定，便于错误信息列出。
     */
    public static final List<String> ALLOWED_MODES =
            Collections.unmodifiableList(Arrays.asList("dev", "prod", "sandbox"));

    /** dev / prod / sandbox；默认 dev */
    private String mode = "dev";

    /**
     * 是否当前为开发/沙箱模式（允许 Mock 行为）
     */
    public boolean isMockEnabled() {
        return "dev".equalsIgnoreCase(mode) || "sandbox".equalsIgnoreCase(mode);
    }

    /**
     * 是否当前为严格生产模式（禁止 Mock 行为）
     */
    public boolean isStrictProd() {
        return "prod".equalsIgnoreCase(mode);
    }

    /**
     * P0-T12：启动期 mode 校验。必须在 {@link #setMode(String)} 之前完成 Bean 初始化，
     * 非法值直接阻断 Spring 上下文启动（不允许静默 fallback 到 dev）。
     *
     * <p>校验规则：
     * <ul>
     *   <li>非 null 且非空白字符串</li>
     *   <li>必须命中 {@link #ALLOWED_MODES} 之一（区分大小写以匹配 application.yml 约定）</li>
     * </ul>
     */
    @PostConstruct
    public void validate() {
        if (mode == null || mode.trim().isEmpty()) {
            throw new BeanInitializationException(
                    "[DCEP] app.dcep.mode 不能为空；合法值: " + ALLOWED_MODES);
        }
        if (!ALLOWED_MODES.contains(mode)) {
            throw new BeanInitializationException(
                    "[DCEP] app.dcep.mode='" + mode + "' 非法；合法值: " + ALLOWED_MODES);
        }
    }
}
