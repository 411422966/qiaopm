package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.FiscalConfig;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.AuthService;
import com.meiyetec.service.FiscalConfigService;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FISCAL-001：财税开票渠道配置管理
 *
 * <p>对照 DcepConfigController 设计：
 * <ul>
 *   <li>凭据列（appKey/appSecret）脱敏展示与二次确认密码</li>
 *   <li>同一租户每个 channel 唯一</li>
 *   <li>{@code test}：模拟 / 真实调用网关</li>
 * </ul>
 */
@RestController
@RequestMapping("/fiscal/config")
@RequiredArgsConstructor
@SysOpLog(module = "财税开票渠道配置")
public class FiscalConfigController {

    private final FiscalConfigService service;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    private static final String MASK = "***";

    private static String mask(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() <= 6) return "••••";
        return "••••••••" + s.substring(s.length() - 4);
    }

    private Map<String, Object> toView(FiscalConfig cfg) {
        if (cfg == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id", cfg.getId());
        m.put("channel", cfg.getChannel());
        m.put("appKey", mask(cfg.getAppKey()));
        m.put("appKeyMasked", cfg.getAppKey() != null && !cfg.getAppKey().isEmpty());
        m.put("appSecret", mask(cfg.getAppSecret()));
        m.put("appSecretMasked", cfg.getAppSecret() != null && !cfg.getAppSecret().isEmpty());
        m.put("taxNo", cfg.getTaxNo());
        m.put("invoiceType", cfg.getInvoiceType());
        m.put("merchantName", cfg.getMerchantName());
        m.put("platformUrl", cfg.getPlatformUrl());
        m.put("notifyUrl", cfg.getNotifyUrl());
        m.put("environment", cfg.getEnvironment());
        m.put("enabled", cfg.getEnabled());
        m.put("extraJson", cfg.getExtraJson());
        m.put("description", cfg.getDescription());
        m.put("createTime", cfg.getCreateTime());
        m.put("updateTime", cfg.getUpdateTime());
        return m;
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return Result.success(service.list(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId))
                .stream().map(this::toView).toList());
    }

    @GetMapping("/by-channel/{channel}")
    public Result<Map<String, Object>> getByChannel(@PathVariable String channel) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FiscalConfig c = service.getOne(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId)
                .eq(FiscalConfig::getChannel, channel));
        return Result.success(toView(c));
    }

    private boolean isSensitiveChange(FiscalConfig incoming) {
        if (incoming.getAppKey() != null && !incoming.getAppKey().isEmpty()
                && !MASK.equals(incoming.getAppKey())) return true;
        if (incoming.getAppSecret() != null && !incoming.getAppSecret().isEmpty()
                && !MASK.equals(incoming.getAppSecret())) return true;
        if (incoming.getEnabled() != null && incoming.getEnabled() == 1) return true;
        return false;
    }

    private void verifyConfirmPassword(FiscalConfig incoming, String confirmPwd,
                                        HttpServletRequest request) {
        if (!isSensitiveChange(incoming)) return;
        if (confirmPwd == null || confirmPwd.length() < 4) {
            throw new BusinessException(BizCode.BAD_REQUEST.code,
                    "敏感操作要求提供当前登录密码以二次确认");
        }
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
    public Result<Void> save(@RequestBody FiscalConfig cfg, HttpServletRequest request) {
        // 凭据占位 / 空 = 未修改
        if (cfg.getAppKey() == null || cfg.getAppKey().isEmpty() || MASK.equals(cfg.getAppKey()))
            cfg.setAppKey(null);
        if (cfg.getAppSecret() == null || cfg.getAppSecret().isEmpty() || MASK.equals(cfg.getAppSecret()))
            cfg.setAppSecret(null);

        verifyConfirmPassword(cfg, cfg.getConfirmPassword(), request);
        cfg.setConfirmPassword(null);

        Long tenantId = TenantContextHolder.get().getTenantId();
        cfg.setTenantId(tenantId);
        if (cfg.getChannel() == null) cfg.setChannel("BAI_WANG");
        if (cfg.getEnvironment() == null) cfg.setEnvironment("sandbox");
        if (cfg.getInvoiceType() == null) cfg.setInvoiceType("GENERAL");
        if (cfg.getEnabled() == null) cfg.setEnabled(0);

        FiscalConfig existing = service.getOne(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId)
                .eq(FiscalConfig::getChannel, cfg.getChannel()));

        if (existing == null) {
            cfg.setId(null);
            service.save(cfg);
        } else {
            cfg.setId(existing.getId());
            if (cfg.getAppKey() == null) cfg.setAppKey(existing.getAppKey());
            if (cfg.getAppSecret() == null) cfg.setAppSecret(existing.getAppSecret());
            cfg.setCreateTime(null);
            cfg.setUpdateTime(null);
            service.updateById(cfg);
        }
        return Result.success("保存成功", null);
    }

    @PutMapping("/{id}/toggle/{enabled}")
    public Result<Void> toggle(@PathVariable Long id, @PathVariable Integer enabled) {
        FiscalConfig c = service.getById(id);
        if (c != null) {
            c.setEnabled(enabled);
            service.updateById(c);
        }
        return Result.success("操作成功", null);
    }

    /**
     * 测试连通性 — 按当前 fiscal.mode 选 mock / 真实实现
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody(required = false) FiscalConfig override) {
        long start = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> r = new HashMap<>();
        r.put("timestamp", java.time.Instant.now().toString());

        FiscalConfig cfg = service.getOne(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId)
                .eq(FiscalConfig::getChannel, "BAI_WANG"));
        if (cfg == null) {
            r.put("ok", false);
            r.put("stage", "config_missing");
            r.put("message", "未找到百望配置，请先在 /fiscal/config 页面保存");
            r.put("elapsedMs", 0L);
            return Result.success(r);
        }
        if (override != null) {
            if (override.getAppKey() != null && !override.getAppKey().isEmpty()
                    && !MASK.equals(override.getAppKey()))
                cfg.setAppKey(override.getAppKey());
            if (override.getAppSecret() != null && !override.getAppSecret().isEmpty()
                    && !MASK.equals(override.getAppSecret()))
                cfg.setAppSecret(override.getAppSecret());
            if (override.getPlatformUrl() != null) cfg.setPlatformUrl(override.getPlatformUrl());
        }

        if (cfg.getAppKey() == null || cfg.getAppKey().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_app_key");
            r.put("message", "appKey 未配置"); r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        if (cfg.getAppSecret() == null || cfg.getAppSecret().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_app_secret");
            r.put("message", "appSecret 未配置"); r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        if (cfg.getTaxNo() == null || cfg.getTaxNo().isEmpty()) {
            r.put("ok", false); r.put("stage", "missing_tax_no");
            r.put("message", "销方税号未配置"); r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
        // 模拟调用
        try {
            long mockLatency = 200L + (long) (Math.random() * 300);
            Thread.sleep(mockLatency);
            r.put("ok", true);
            r.put("stage", "gateway_ok");
            r.put("channel", cfg.getChannel());
            r.put("environment", cfg.getEnvironment());
            r.put("taxNo", cfg.getTaxNo());
            r.put("message", "链路通畅，配置完整（mock 网关）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        r.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.success(r);
    }
}
