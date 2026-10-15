package com.meiyetec.cn.domestic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 国内支付三通道凭据 + 环境配置（沙箱占位）
 *
 * <p>对应 application.yml：
 * <pre>
 * app-cn-pay:
 *   env-mode: sandbox
 *     notify-url: https://dev.meiyetec.com/domestic/webhook
 *     wechat:
 *       app-id: wx0000000000000000
 *       mch-id: 1900000000
 *       api-v3-key: PLACEHOLDER_SET_VIA_ENV
 *       private-key-path: classpath:keys/wechat/apiclient_key.pem
 *       cert-serial-no: PLACEHOLDER
 *       cert-path: classpath:keys/wechat/apiclient_cert.pem
 *     alipay:
 *       app-id: 2021000000000000
 *       private-key: PLACEHOLDER_SET_VIA_ENV
 *       alipay-public-key: PLACEHOLDER
 *       sign-type: RSA2
 *     union:
 *       # 暂未接入，本期跳过
 *       enabled: false
 * </pre>
 *
 * <p>生产部署必须通过环境变量注入真实凭据（如 {@code APP_CN_PAY_WECHAT_API_V3_KEY}）。
 *
 * @since 2026-09-07
 */
@Data
@ConfigurationProperties(prefix = "app-cn-pay")
public class DomesticPayProperties {

    /** sandbox / production */
    private String envMode = "sandbox";

    /** 异步通知外网可访问地址 */
    private String notifyUrl;

    private final Wechat wechat = new Wechat();
    private final Alipay alipay = new Alipay();
    private final Union union = new Union();

    @Data
    public static class Wechat {
        /** 公众号/小程序/移动应用 AppID */
        private String appId;
        /** 商户号 */
        private String mchId;
        /** API v3 密钥（32 位） */
        private String apiV3Key;
        /** 商户 API 证书私钥路径（classpath:/file:） */
        private String privateKeyPath;
        /** 商户 API 证书序列号 */
        private String certSerialNo;
        /** 商户 API 证书路径（classpath:/file:） */
        private String certPath;
        /** 微信支付平台证书（自动从 v3/certificates 拉取，缓存到本地） */
        private String platformCertCachePath;
        private Map<String, String> extra = new HashMap<>();
    }

    @Data
    public static class Alipay {
        /** 应用 AppID */
        private String appId;
        /** 应用私钥（PEM 字符串 或 classpath 路径） */
        private String privateKey;
        /** 支付宝公钥（验签用） */
        private String alipayPublicKey;
        /** RSA2 / RSA */
        private String signType = "RSA2";
        /** 网关：dev=https://openapi.alipaydev.com/gateway.do, prod=https://openapi.alipay.com/gateway.do */
        private String gateway = "https://openapi.alipaydev.com/gateway.do";
        private Map<String, String> extra = new HashMap<>();
    }

    @Data
    public static class Union {
        /** 本期跳过 */
        private boolean enabled = false;
        private String merId;
        private String signCertPath;
        private String signCertPwd;
        private String encryptCertPath;
        private String middleCertPath;
        private String rootCertPath;
        private String frontUrl;
    }
}
