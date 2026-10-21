package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.DcepConfig;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.service.DcepConfigService;
import com.meiyetec.service.AuthService;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/sys/dcep")
@RequiredArgsConstructor
@SysOpLog(module = "数字人民币")
public class DcepConfigController {

    private final DcepConfigService service;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    private static final String MASK = "***";

    private static String mask(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() <= 6) return "••••";
        return "••••••••" + s.substring(s.length() - 4);
    }

    private static String fingerprint(String s) {
        if (s == null || s.isEmpty()) return null;
        String trimmed = s.replaceAll("\\s+", "");
        if (trimmed.length() <= 8) return trimmed;
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    private Map<String, Object> toView(DcepConfig cfg) {
        if (cfg == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id", cfg.getId());
        m.put("merchantId", cfg.getMerchantId());
        m.put("merchantName", cfg.getMerchantName());
        m.put("appId", cfg.getAppId());
        m.put("apiKey", mask(cfg.getApiKey()));
        m.put("apiKeyMasked", cfg.getApiKey() != null && !cfg.getApiKey().isEmpty());
        m.put("privateKey", mask(cfg.getPrivateKey()));
        m.put("privateKeyMasked", cfg.getPrivateKey() != null && !cfg.getPrivateKey().isEmpty());
        m.put("privateKeyFingerprint", fingerprint(cfg.getPrivateKey()));
        m.put("publicKey", mask(cfg.getPublicKey()));
        m.put("publicKeyMasked", cfg.getPublicKey() != null && !cfg.getPublicKey().isEmpty());
        m.put("publicKeyFingerprint", fingerprint(cfg.getPublicKey()));
        m.put("notifyUrl", cfg.getNotifyUrl());
        m.put("environment", cfg.getEnvironment());
        m.put("enabled", cfg.getEnabled());
        m.put("description", cfg.getDescription());
        m.put("createdAt", cfg.getCreatedAt());
        m.put("updatedAt", cfg.getUpdatedAt());
        return m;
    }

    @GetMapping("/current")
    @RequirePermission("sys:dcep:list")
    public Result<Map<String, Object>> current() {
        DcepConfig cfg = service.list().stream().findFirst().orElse(null);
        return Result.success(toView(cfg));
    }

    private boolean isSensitiveChange(DcepConfig incoming) {
        DcepConfig existing = service.list().stream().findFirst().orElse(null);
        if (existing == null) {
            // 首次配置：任何凭据 / 启用 都视为敏感
            return (incoming.getApiKey() != null && !incoming.getApiKey().isEmpty())
                || (incoming.getPrivateKey() != null && !incoming.getPrivateKey().isEmpty())
                || (incoming.getPublicKey() != null && !incoming.getPublicKey().isEmpty())
                || (incoming.getEnabled() != null && incoming.getEnabled() == 1);
        }
        // 任一凭据被新传入
        if (incoming.getApiKey() != null && !incoming.getApiKey().isEmpty() && !MASK.equals(incoming.getApiKey()))
            return true;
        if (incoming.getPrivateKey() != null && !incoming.getPrivateKey().isEmpty() && !MASK.equals(incoming.getPrivateKey()))
            return true;
        if (incoming.getPublicKey() != null && !incoming.getPublicKey().isEmpty() && !MASK.equals(incoming.getPublicKey()))
            return true;
        // 环境沙→产
        if (!"production".equals(existing.getEnvironment()) && "production".equals(incoming.getEnvironment()))
            return true;
        // 启用 0→1
        if ((existing.getEnabled() == null || existing.getEnabled() == 0) && incoming.getEnabled() != null && incoming.getEnabled() == 1)
            return true;
        return false;
    }

    private void verifyConfirmPassword(DcepConfig incoming, String confirmPwd, HttpServletRequest request) {
        if (!isSensitiveChange(incoming)) return;
        if (confirmPwd == null || confirmPwd.length() < 4) {
            throw new BusinessException(BizCode.BAD_REQUEST.code, "敏感操作要求提供当前登录密码以二次确认");
        }
        // 从 Authorization header 解析当前登录用户，再用 BCrypt 比对其密码
        Long currentUserId = extractUserIdFromRequest(request);
        try {
            authService.checkPassword(currentUserId, confirmPwd);
        } catch (BusinessException e) {
            throw new BusinessException(BizCode.PASSWORD_NOT_MATCH.code, "二次确认密码错误");
        }
    }

    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(BizCode.UNAUTHORIZED);
        }
        String token = header.substring("Bearer ".length()).trim();
        try {
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            throw new BusinessException(BizCode.AUTH_TOKEN_EXPIRED);
        }
    }

    @PostMapping
    @RequirePermission("sys:dcep:edit")
    public Result<Void> save(@RequestBody DcepConfig cfg, HttpServletRequest request) {
        // 凭据掩码占位或空串（即"没改"）时跳过更新对应字段
        if (cfg.getApiKey() == null || cfg.getApiKey().isEmpty() || MASK.equals(cfg.getApiKey()))
            cfg.setApiKey(null);
        if (cfg.getPrivateKey() == null || cfg.getPrivateKey().isEmpty() || MASK.equals(cfg.getPrivateKey()))
            cfg.setPrivateKey(null);
        if (cfg.getPublicKey() == null || cfg.getPublicKey().isEmpty() || MASK.equals(cfg.getPublicKey()))
            cfg.setPublicKey(null);

        verifyConfirmPassword(cfg, cfg.getConfirmPassword(), request);
        cfg.setConfirmPassword(null);

        DcepConfig existing = service.list().stream().findFirst().orElse(null);
        if (existing == null) {
            cfg.setId(null);
            cfg.setUpdatedAt(null);
            service.save(cfg);
        } else {
            cfg.setId(existing.getId());
            // 凭据未传入时不覆盖
            if (cfg.getApiKey() == null) cfg.setApiKey(existing.getApiKey());
            if (cfg.getPrivateKey() == null) cfg.setPrivateKey(existing.getPrivateKey());
            if (cfg.getPublicKey() == null) cfg.setPublicKey(existing.getPublicKey());
            cfg.setCreatedAt(null);
            cfg.setUpdatedAt(null);
            service.updateById(cfg);
        }
        return Result.success("保存成功", null);
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:dcep:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody DcepConfig cfg) {
        cfg.setId(id);
        if (cfg.getApiKey() == null || cfg.getApiKey().isEmpty() || MASK.equals(cfg.getApiKey()))
            cfg.setApiKey(null);
        if (cfg.getPrivateKey() == null || cfg.getPrivateKey().isEmpty() || MASK.equals(cfg.getPrivateKey()))
            cfg.setPrivateKey(null);
        if (cfg.getPublicKey() == null || cfg.getPublicKey().isEmpty() || MASK.equals(cfg.getPublicKey()))
            cfg.setPublicKey(null);
        service.updateById(cfg);
        return Result.success("更新成功", null);
    }

    @PutMapping("/{id}/toggle/{enabled}")
    @RequirePermission("sys:dcep:edit")
    public Result<Void> toggle(@PathVariable Long id, @PathVariable Integer enabled) {
        DcepConfig c = service.getById(id);
        if (c != null) {
            c.setEnabled(enabled);
            service.updateById(c);
        }
        return Result.success("操作成功", null);
    }

    /**
     * 测试连接：基于当前（或临时传入的）凭据，向 DCEP 网关发一次"心跳"。
     *
     * 当前为 mock：模拟真实链路中的 3 个失败点（缺凭据 / 凭据格式错 / 私钥解析失败），
     * 全部通过则模拟 200~600ms 网络延迟后返回"OK"。
     * 真接入数研所时把 mockBody() 替换为 DCEP SDK 调用即可。
     */
    @PostMapping("/test")
    @RequirePermission("sys:dcep:test")
    public Result<Map<String, Object>> test(@RequestBody(required = false) DcepConfig override) {
        long start = System.currentTimeMillis();
        DcepConfig cfg = service.list().stream().findFirst().orElse(null);
        Map<String, Object> r = new HashMap<>();
        r.put("timestamp", java.time.Instant.now().toString());

        if (cfg == null) {
            r.put("ok", false);
            r.put("stage", "config_missing");
            r.put("message", "未找到数字人民币配置，请先保存");
            r.put("elapsedMs", 0L);
            return Result.success(r);
        }

        // 用 override 覆盖（用于测试"未保存的临时 PEM"）
        if (override != null) {
            if (override.getPrivateKey() != null && !override.getPrivateKey().isEmpty() && !MASK.equals(override.getPrivateKey()))
                cfg.setPrivateKey(override.getPrivateKey());
            if (override.getPublicKey() != null && !override.getPublicKey().isEmpty() && !MASK.equals(override.getPublicKey()))
                cfg.setPublicKey(override.getPublicKey());
            if (override.getApiKey() != null && !override.getApiKey().isEmpty() && !MASK.equals(override.getApiKey()))
                cfg.setApiKey(override.getApiKey());
            if (override.getEnvironment() != null) cfg.setEnvironment(override.getEnvironment());
        }

        // 阶段 1：完整性
        if (cfg.getApiKey() == null || cfg.getApiKey().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_api_key");
            r.put("message", "API Key 未配置");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        if (cfg.getPrivateKey() == null || cfg.getPrivateKey().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_private_key");
            r.put("message", "商户私钥未配置");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        if (cfg.getPublicKey() == null || cfg.getPublicKey().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_public_key");
            r.put("message", "商户公钥未配置");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        if (cfg.getMerchantId() == null || cfg.getMerchantId().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_merchant_id");
            r.put("message", "商户号未配置");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }

        // 阶段 2：私钥 PEM 解析（真解析）
        try {
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            String pk = cfg.getPrivateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(
                java.util.Base64.getDecoder().decode(pk));
            java.security.PrivateKey privateKey = kf.generatePrivate(spec);
            r.put("privateKeyBits", ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus().bitLength());
        } catch (Exception e) {
            r.put("ok", false); r.put("stage", "private_key_parse");
            r.put("message", "私钥 PEM 无法解析：" + e.getClass().getSimpleName());
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }

        // 阶段 3：公钥 PEM 解析
        try {
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            String pub = cfg.getPublicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(
                java.util.Base64.getDecoder().decode(pub));
            java.security.PublicKey publicKey = kf.generatePublic(spec);
            r.put("publicKeyBits", ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength());
        } catch (Exception e) {
            r.put("ok", false); r.put("stage", "public_key_parse");
            r.put("message", "公钥 PEM 无法解析：" + e.getClass().getSimpleName());
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }

        // 阶段 4：模拟调用网关（替换为真实 DCEP 心跳调用）
        try {
            long mockLatency = 200 + (long) (Math.random() * 400);
            Thread.sleep(mockLatency);
            r.put("ok", true);
            r.put("stage", "gateway_ok");
            r.put("environment", cfg.getEnvironment());
            r.put("gateway", cfg.getEnvironment().equals("production")
                ? "https://api.dcep.example.cn/v1"
                : "https://api-sandbox.dcep.example.cn/v1");
            r.put("merchantId", cfg.getMerchantId());
            r.put("message", "链路通畅，所有凭据有效（mock 网关）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        r.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.success(r);
    }
}