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
 * FISCAL-001：财税环境配置
 *
 * <p>通过 application.yml 的 {@code fiscal.mode} 切换网关实现：
 * <ul>
 *   <li>{@code mock}     — 默认（沙箱/无凭据环境），所有开票走内存模拟，立即成功</li>
 *   <li>{@code baiwang}  — 真实对接百望云（需要 fiscal_config 表里有 appKey/appSecret + enabled=1）</li>
 *   <li>{@code yintian}  — 真实对接航天信息</li>
 * </ul>
 *
 * <p>SDK 默认 base URL：
 * <ul>
 *   <li>百望沙箱：{@code https://sandbox.baiwang.com/openapi/v1}</li>
 *   <li>百望生产：{@code https://eapi.baiwang.com/openapi/v1}</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "fiscal")
public class FiscalConfigProperties {

    public static final List<String> ALLOWED_MODES =
            Collections.unmodifiableList(Arrays.asList("mock", "baiwang", "yintian"));

    /** mock / baiwang / yintian；默认 mock（无凭据安全） */
    private String mode = "mock";

    /** 全局连接超时（ms） */
    private Integer connectTimeoutMs = 5000;
    /** 全局读超时（ms） */
    private Integer readTimeoutMs = 15000;
    /** 重试次数 */
    private Integer retryMax = 2;

    /** 是否 Mock 模式（dev / 无凭据环境） */
    public boolean isMockEnabled() {
        return "mock".equalsIgnoreCase(mode);
    }

    @PostConstruct
    public void validate() {
        if (mode == null || mode.trim().isEmpty()) {
            throw new BeanInitializationException(
                    "[FISCAL] fiscal.mode 不能为空；合法值: " + ALLOWED_MODES);
        }
        if (!ALLOWED_MODES.contains(mode.toLowerCase())) {
            throw new BeanInitializationException(
                    "[FISCAL] fiscal.mode='" + mode + "' 非法；合法值: " + ALLOWED_MODES);
        }
    }
}
