package com.meiyetec.dcep.operator;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * DCEP-03/04 阶段 B：运营机构（工/农/中/建/交）连接配置
 *
 * <p>支持在 application.yml 中声明多个运营机构：
 *
 * <pre>
 * dcep:
 *   operators:
 *     icbc:
 *       enabled: true
 *       env-mode: SANDBOX          # SANDBOX / PROD
 *       protocol: REST            # REST / NETTY-LONG
 *       base-url: https://open.icbc.com.cn
 *       prepay-path: /api/pay/prepay
 *       query-path: /api/pay/query
 *       refund-path: /api/pay/refund
 *       close-path: /api/pay/close
 *       notify-path: /api/pay/notify
 *       merchant-id: 1234567890
 *       app-id: app-merchant-001
 *       sign-algorithm: RSA-SHA256 # RSA-SHA256 / SM3-SM2
 *       client-cert-path: /opt/dcep/certs/icbc_client.pfx
 *       client-cert-password: ${DCEP_ICBC_PFX_PWD}
 *       bank-public-key: -----BEGIN PUBLIC KEY-----...
 *       connect-timeout-ms: 10000
 *       read-timeout-ms: 30000
 *       notify-replay-ttl-days: 30
 *     ccb:
 *       enabled: true
 *       env-mode: SANDBOX
 *       protocol: NETTY-LONG
 *       host: 127.0.0.1
 *       port: 18001
 *       ...
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "dcep")
public class DcepOperatorProperties {

    /** 默认环境模式 */
    private String defaultEnvMode = "SANDBOX";

    /** notifyId 防重放 Redis key 前缀 */
    private String replayKeyPrefix = "dcep:notify:replay:";

    /** notifyId 默认 TTL（天） */
    private int defaultReplayTtlDays = 30;

    /** 异步通知时间戳允许偏差（秒） */
    private long notifyTimestampToleranceSeconds = 300;

    /** 所有运营机构（key = 机构代号，如 ICBC / CCB / ABC / BOC / BCOM） */
    private Map<String, Operator> operators = new HashMap<>();

    @Data
    public static class Operator {
        /** 是否启用（false 则路由到 Sandbox） */
        private boolean enabled = false;
        /** SANDBOX / PROD */
        private String envMode = "SANDBOX";
        /** REST / NETTY-LONG */
        private String protocol = "REST";
        /** REST base URL（ICBC 等开放平台） */
        private String baseUrl;
        /** 各个端点路径（可选，未配置时走默认值） */
        private String prepayPath = "/api/pay/prepay";
        private String queryPath = "/api/pay/query";
        private String refundPath = "/api/pay/refund";
        private String closePath = "/api/pay/close";
        private String notifyPath = "/api/pay/notify";
        /** Netty 长连接（CCB 等） */
        private String host;
        private Integer port;
        /** 商户凭证 */
        private String merchantId;
        private String appId;
        /** RSA / SM2 */
        private String signAlgorithm = "RSA-SHA256";
        /** 客户端证书路径（pfx/p12） */
        private String clientCertPath;
        private String clientCertPassword;
        /** 银行公钥（PEM 字符串，可直接 inline 或 ${ENV:VAR} 引用） */
        private String bankPublicKey;
        /** 我方商户私钥（PEM 字符串） */
        private String merchantPrivateKey;
        /** 超时配置 */
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
        /** notifyId 防重放 TTL（天） */
        private int notifyReplayTtlDays = 30;
        /** 自定义 HTTP 头（部分银行有特殊要求） */
        private Map<String, String> customHeaders = new HashMap<>();
    }
}
