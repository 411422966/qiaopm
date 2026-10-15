package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * DCEP-03/04 阶段 B：运营机构注册中心
 *
 * <p>职责：
 * <ol>
 *   <li>根据 channelCode（{@code DCEP-ICBC} / {@code DCEP-CCB} / ...）查找到对应的 {@link Operator} 配置</li>
 *   <li>判断机构是否启用（未启用则路由到 SandboxDcepAdapter）</li>
 *   <li>提供连接健康检查接口</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DcepOperatorProperties.class)
public class DcepOperatorRegistry {

    private final DcepOperatorProperties properties;

    /** 将 channelCode（如 DCEP-ICBC）转换为 operator key（如 ICBC） */
    public static String operatorKey(String channelCode) {
        if (channelCode == null) return null;
        String s = channelCode.toUpperCase();
        if (s.startsWith("DCEP-")) {
            return s.substring("DCEP-".length());
        }
        return s;
    }

    /** 根据 channelCode 查找 operator 配置 */
    public Operator lookup(String channelCode) {
        String key = operatorKey(channelCode);
        return properties.getOperators().get(key);
    }

    /** 该 channelCode 是否启用了真实机构 */
    public boolean isEnabled(String channelCode) {
        Operator op = lookup(channelCode);
        return op != null && op.isEnabled();
    }

    /** 该 channelCode 应走 SANDBOX（未启用真实机构） */
    public boolean shouldUseSandbox(String channelCode) {
        return !isEnabled(channelCode);
    }

    /** 列出所有已配置的运营机构 key */
    public Set<String> listAllOperators() {
        return Collections.unmodifiableSet(properties.getOperators().keySet());
    }

    /** 列出所有已启用真实机构的 key */
    public Set<String> listEnabledOperators() {
        return properties.getOperators().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 全局默认配置 */
    public DcepOperatorProperties getProperties() {
        return properties;
    }

    /** 健康检查：打印所有机构的连接状态 */
    public String healthCheckReport() {
        StringBuilder sb = new StringBuilder("DCEP Operator Health:\n");
        properties.getOperators().forEach((key, op) -> {
            sb.append("  - ").append(key)
              .append(" : enabled=").append(op.isEnabled())
              .append(", env=").append(op.getEnvMode())
              .append(", protocol=").append(op.getProtocol())
              .append(", endpoint=").append(op.getBaseUrl() != null
                      ? op.getBaseUrl() : op.getHost() + ":" + op.getPort())
              .append("\n");
        });
        return sb.toString();
    }
}
