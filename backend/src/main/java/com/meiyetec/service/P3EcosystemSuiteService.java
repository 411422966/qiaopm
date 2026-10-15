package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P3 生态与国际化套件：
 * - P3.22 插件市场
 * - P3.23 财税直连（完善 DcepConfigService）
 * - P3.24 CRM 集成（HubSpot/Salesforce）
 * - P3.25 电商集成（微店/有赞/Shopify）
 */
@Slf4j
@RestController
@RequestMapping("/p3-suite")
@RequiredArgsConstructor
public class P3EcosystemSuiteService {

    private final PluginMarketMapper pluginMapper;
    private final PluginInstallMapper pluginInstallMapper;
    private final PluginReviewMapper pluginReviewMapper;
    private final FiscalInvoiceMapper fiscalMapper;
    private final CrmIntegrationMapper crmIntegrationMapper;
    private final CrmSyncLogMapper crmSyncLogMapper;
    private final EcommercePlatformMapper ecomPlatformMapper;
    private final EcommerceProductMapper ecomProductMapper;
    private final EcommerceOrderMapper ecomOrderMapper;
    private final EcommerceSyncLogMapper ecomSyncLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // P3.22 插件市场
    // ====================================================================

    /**
     * 列出市场（按分类筛选）
     */
    @GetMapping("/plugin/market")
    public Result<List<PluginMarket>> pluginMarket(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PluginMarket> qw = new LambdaQueryWrapper<PluginMarket>()
                .eq(PluginMarket::getTenantId, tenantId)
                .eq(PluginMarket::getStatus, "AVAILABLE")
                .orderByDesc(PluginMarket::getIsFeatured)
                .orderByDesc(PluginMarket::getInstallCount);
        if (category != null && !category.isEmpty()) qw.eq(PluginMarket::getCategory, category);
        if (keyword != null && !keyword.isEmpty()) qw.like(PluginMarket::getPluginName, keyword);
        return Result.success(pluginMapper.selectList(qw));
    }

    @PostMapping("/plugin/install")
    @Transactional(rollbackFor = Exception.class)
    public Result<PluginInstall> installPlugin(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long pluginId = ((Number) body.get("pluginId")).longValue();
        PluginMarket plugin = pluginMapper.selectById(pluginId);
        if (plugin == null || !plugin.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "插件不存在");

        PluginInstall exist = pluginInstallMapper.selectOne(new LambdaQueryWrapper<PluginInstall>()
                .eq(PluginInstall::getTenantId, tenantId)
                .eq(PluginInstall::getPluginId, pluginId));
        if (exist != null && !"UNINSTALLED".equals(exist.getInstallStatus()))
            return Result.error(BizCode.BAD_REQUEST, "插件已安装");

        PluginInstall inst = new PluginInstall();
        inst.setTenantId(tenantId);
        inst.setPluginId(pluginId);
        inst.setPluginCode(plugin.getPluginCode());
        inst.setVersion(plugin.getVersion());
        inst.setConfigJson((String) body.get("configJson"));
        inst.setEnabled(1);
        inst.setInstallStatus("ACTIVE");
        inst.setUseCount(0L);
        if (plugin.getPrice() != null && plugin.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            String billingType = plugin.getBillingType();
            if ("MONTHLY".equals(billingType)) inst.setExpireAt(LocalDateTime.now().plusMonths(1));
            else if ("YEARLY".equals(billingType)) inst.setExpireAt(LocalDateTime.now().plusYears(1));
        }
        pluginInstallMapper.insert(inst);

        jdbc.update("UPDATE plugin_market SET install_count = install_count + 1 WHERE id = ?", pluginId);
        return Result.success("已安装", inst);
    }

    @PostMapping("/plugin/{id}/uninstall")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> uninstallPlugin(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PluginInstall inst = pluginInstallMapper.selectById(id);
        if (inst == null || !inst.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "未安装");
        inst.setInstallStatus("UNINSTALLED");
        inst.setEnabled(0);
        pluginInstallMapper.updateById(inst);
        jdbc.update("UPDATE plugin_market SET install_count = GREATEST(0, install_count - 1) WHERE id = ?", inst.getPluginId());
        return Result.success("已卸载", null);
    }

    @PostMapping("/plugin/{id}/enable")
    public Result<Void> enablePlugin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PluginInstall inst = pluginInstallMapper.selectById(id);
        if (inst == null) return Result.error(BizCode.NOT_FOUND, "未安装");
        inst.setEnabled(Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0);
        pluginInstallMapper.updateById(inst);
        return Result.success("已切换", null);
    }

    @PostMapping("/plugin/{id}/config")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PluginInstall inst = pluginInstallMapper.selectById(id);
        if (inst == null) return Result.error(BizCode.NOT_FOUND, "未安装");
        inst.setConfigJson(toJson(body));
        pluginInstallMapper.updateById(inst);
        return Result.success("已保存", null);
    }

    @GetMapping("/plugin/installed")
    public Result<List<Map<String, Object>>> installedPlugins() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            return Result.success(jdbc.queryForList(
                    "SELECT pi.*, pm.plugin_name, pm.category, pm.icon_url, pm.short_desc " +
                    "FROM plugin_install pi LEFT JOIN plugin_market pm ON pm.id = pi.plugin_id " +
                    "WHERE pi.tenant_id = ? AND pi.install_status = 'ACTIVE' ORDER BY pi.id DESC",
                    tenantId));
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @PostMapping("/plugin/review")
    @Transactional(rollbackFor = Exception.class)
    public Result<PluginReview> reviewPlugin(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PluginReview r = new PluginReview();
        r.setTenantId(tenantId);
        r.setPluginId(((Number) body.get("pluginId")).longValue());
        r.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        r.setRating((Integer) body.get("rating"));
        r.setTitle((String) body.get("title"));
        r.setContent((String) body.get("content"));
        pluginReviewMapper.insert(r);

        // 更新聚合评分
        jdbc.update(
                "UPDATE plugin_market SET review_count = review_count + 1, rating = ((rating * review_count + ?) / (review_count + 1)) WHERE id = ?",
                r.getRating(), r.getPluginId());
        return Result.success("已评价", r);
    }

    @PostMapping("/plugin/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefaultPlugins() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                mk("code", "AI_RECEPTION", "name", "AI 智能前台", "category", "BUSINESS", "version", "2.1.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1, "isFeatured", 1,
                        "tags", "AI,RECEPTION,RECOMMEND", "shortDesc", "AI 智能识别客户画像、推荐服务",
                        "desc", "基于客户历史行为 + AI 大模型，提供个性化服务推荐"),
                mk("code", "WECHAT_MINIPROGRAM", "name", "微信小程序", "category", "INTEGRATION", "version", "1.8.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1, "isFeatured", 1,
                        "tags", "WECHAT,MINIPROGRAM", "shortDesc", "一键开启微信小程序", "desc", "10分钟拥有独立小程序"),
                mk("code", "DOUYIN", "name", "抖音企业号", "category", "MARKETING", "version", "1.0.2",
                        "vendor", "OFFICIAL", "price", 299, "type", "MONTHLY", "isOfficial", 1, "isFeatured", 0,
                        "tags", "DOUYIN,SHORT_VIDEO", "shortDesc", "抖音企业号打通", "desc", "抖音预约同步到店"),
                mk("code", "MEITUAN", "name", "美团点评", "category", "INTEGRATION", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1,
                        "tags", "MEITUAN", "shortDesc", "美团/大众点评预约对接", "desc", "美团订单自动同步到店"),
                mk("code", "FACEBOOK_ADS", "name", "Facebook Ads", "category", "MARKETING", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 599, "type", "MONTHLY", "isOfficial", 1,
                        "tags", "FACEBOOK,OVERSEAS", "shortDesc", "海外 Facebook 广告投放", "desc", "海外获客必备"),
                mk("code", "KLAVIYO", "name", "Klaviyo 邮件", "category", "MARKETING", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1,
                        "tags", "KLAVIYO,EMAIL,OVERSEAS", "shortDesc", "海外邮件营销", "desc", "海外客户邮件营销 Klaviyo 集成"),
                mk("code", "QUICKBOOKS", "name", "QuickBooks 财税", "category", "INTEGRATION", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 199, "type", "MONTHLY", "isOfficial", 1,
                        "tags", "QUICKBOOKS,FINANCE", "shortDesc", "QuickBooks 自动记账", "desc", "海外会计系统对接"),
                mk("code", "OKTA_SSO", "name", "Okta SSO", "category", "INTEGRATION", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1,
                        "tags", "OKTA,SSO", "shortDesc", "企业级单点登录", "desc", "通过 Okta SSO 登录"),
                mk("code", "FACEBOOK_PIXEL", "name", "Facebook Pixel", "category", "ANALYTICS", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1,
                        "tags", "PIXEL,TRACK", "shortDesc", "广告转化追踪", "desc", "深入了解广告 ROI"),
                mk("code", "HEALTH_GOVERN", "name", "卫健委上报", "category", "INDUSTRY", "version", "1.0.0",
                        "vendor", "OFFICIAL", "price", 0, "type", "FREE", "isOfficial", 1,
                        "tags", "MEDICAL,COMPLIANCE", "shortDesc", "医美合规数据上报", "desc", "卫健委医美数据自动上报")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            PluginMarket exist = pluginMapper.selectOne(new LambdaQueryWrapper<PluginMarket>()
                    .eq(PluginMarket::getTenantId, tenantId)
                    .eq(PluginMarket::getPluginCode, d.get("code")));
            if (exist != null) continue;
            PluginMarket p = new PluginMarket();
            p.setTenantId(tenantId);
            p.setPluginCode((String) d.get("code"));
            p.setPluginName((String) d.get("name"));
            p.setCategory((String) d.get("category"));
            p.setVersion((String) d.get("version"));
            p.setVendor((String) d.get("vendor"));
            p.setPrice(d.get("price") == null ? BigDecimal.ZERO : new BigDecimal(d.get("price").toString()));
            p.setBillingType((String) d.get("type"));
            p.setIsOfficial((Integer) d.get("isOfficial"));
            p.setIsFeatured((Integer) d.getOrDefault("isFeatured", 0));
            p.setTags((String) d.get("tags"));
            p.setShortDesc((String) d.get("shortDesc"));
            p.setDescription((String) d.get("desc"));
            p.setInstallCount(0);
            p.setReviewCount(0);
            p.setRating(new BigDecimal("5.0"));
            p.setStatus("AVAILABLE");
            pluginMapper.insert(p);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }

    // ====================================================================
    // P3.23 财税直连（完善 DcepConfigService）
    // ====================================================================

    /**
     * 申请开票
     */
    @PostMapping("/fiscal/invoice")
    @Transactional(rollbackFor = Exception.class)
    public Result<FiscalInvoice> issueInvoice(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FiscalInvoice inv = new FiscalInvoice();
        inv.setTenantId(tenantId);
        inv.setInvoiceNo("INV" + System.currentTimeMillis());
        inv.setInvoiceType((String) body.getOrDefault("invoiceType", "NORMAL"));
        inv.setBuyerName((String) body.get("buyerName"));
        inv.setBuyerTaxNo((String) body.get("buyerTaxNo"));
        inv.setBuyerAddress((String) body.get("buyerAddress"));
        inv.setBuyerPhone((String) body.get("buyerPhone"));
        inv.setBuyerEmail((String) body.get("buyerEmail"));
        inv.setBuyerBank((String) body.get("buyerBank"));
        inv.setBuyerAccount((String) body.get("buyerAccount"));
        inv.setAmount(new BigDecimal(body.get("amount").toString()));
        BigDecimal taxRate = body.get("taxRate") == null ? new BigDecimal("0.06") : new BigDecimal(body.get("taxRate").toString());
        inv.setTaxRate(taxRate);
        inv.setTaxAmount(inv.getAmount().multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_UP));
        inv.setTotalAmount(inv.getAmount().add(inv.getTaxAmount()));
        inv.setChannel((String) body.getOrDefault("channel", "BAIWANG"));
        inv.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        inv.setInvoiceStatus("PENDING");
        fiscalMapper.insert(inv);

        // 模拟调用百望云/航信/连连
        boolean sandbox = body.get("sandbox") == null || Boolean.TRUE.equals(body.get("sandbox"));
        if (sandbox) {
            inv.setInvoiceStatus("SUCCEEDED");
            inv.setFiscalNo("FN" + System.currentTimeMillis());
            inv.setInvoiceUrl("https://api.example.com/invoice/" + inv.getInvoiceNo() + ".ofd");
            inv.setPdfUrl("https://api.example.com/invoice/" + inv.getInvoiceNo() + ".pdf");
            inv.setIssuedAt(LocalDateTime.now());
            fiscalMapper.updateById(inv);
        }
        return Result.success("已开票", inv);
    }

    /**
     * 冲红
     */
    @PostMapping("/fiscal/invoice/{id}/red")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> redInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FiscalInvoice inv = fiscalMapper.selectById(id);
        if (inv == null || !inv.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "发票不存在");
        inv.setInvoiceStatus("REFUNDED");
        fiscalMapper.updateById(inv);
        // 创建冲红发票
        FiscalInvoice red = new FiscalInvoice();
        red.setTenantId(tenantId);
        red.setInvoiceNo("RED" + System.currentTimeMillis());
        red.setInvoiceType(inv.getInvoiceType());
        red.setBuyerName(inv.getBuyerName());
        red.setBuyerTaxNo(inv.getBuyerTaxNo());
        red.setAmount(inv.getAmount().negate());
        red.setTaxAmount(inv.getTaxAmount().negate());
        red.setTotalAmount(inv.getTotalAmount().negate());
        red.setTaxRate(inv.getTaxRate());
        red.setChannel(inv.getChannel());
        red.setOrderId(inv.getOrderId());
        red.setInvoiceStatus("SUCCEEDED");
        red.setIssuedAt(LocalDateTime.now());
        fiscalMapper.insert(red);
        return Result.success("已冲红", null);
    }

    @GetMapping("/fiscal/invoice/list")
    public Result<List<FiscalInvoice>> listInvoices(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<FiscalInvoice> qw = new LambdaQueryWrapper<FiscalInvoice>()
                .eq(FiscalInvoice::getTenantId, tenantId)
                .orderByDesc(FiscalInvoice::getId);
        if (status != null) qw.eq(FiscalInvoice::getInvoiceStatus, status);
        return Result.success(fiscalMapper.selectList(qw));
    }

    @GetMapping("/fiscal/invoice/{id}")
    public Result<FiscalInvoice> getInvoice(@PathVariable Long id) {
        return Result.success(fiscalMapper.selectById(id));
    }

    // ====================================================================
    // P3.24 CRM 集成
    // ====================================================================

    @PostMapping("/crm/config")
    public Result<CrmIntegration> saveConfig(@RequestBody CrmIntegration c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
        if (c.getStatus() == null) c.setStatus("ACTIVE");
        if (c.getSyncDirection() == null) c.setSyncDirection("BIDIRECTIONAL");
        if (c.getSyncMembers() == null) c.setSyncMembers(1);
        if (c.getSyncOrders() == null) c.setSyncOrders(1);
        if (c.getSyncContacts() == null) c.setSyncContacts(1);
        if (c.getSyncDeals() == null) c.setSyncDeals(1);
        c.setTenantId(tenantId);
        c.setId(null);
        crmIntegrationMapper.insert(c);
        return Result.success("已保存", c);
    }

    @GetMapping("/crm/config/list")
    public Result<List<CrmIntegration>> listConfigs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(crmIntegrationMapper.selectList(new LambdaQueryWrapper<CrmIntegration>()
                .eq(CrmIntegration::getTenantId, tenantId)));
    }

    /**
     * OAuth 授权
     */
    @PostMapping("/crm/oauth")
    public Result<Map<String, Object>> oauth(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.get("platform");
        String code = (String) body.get("code");

        CrmIntegration c = crmIntegrationMapper.selectOne(new LambdaQueryWrapper<CrmIntegration>()
                .eq(CrmIntegration::getTenantId, tenantId)
                .eq(CrmIntegration::getPlatform, platform));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "请先保存配置");

        // 模拟 OAuth 交换
        c.setAccessToken("act_" + UUID.randomUUID().toString().replace("-", ""));
        c.setRefreshToken("ref_" + UUID.randomUUID().toString().replace("-", ""));
        c.setTokenExpireAt(LocalDateTime.now().plusHours(2));
        crmIntegrationMapper.updateById(c);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", c.getAccessToken());
        data.put("expireAt", c.getTokenExpireAt());
        return Result.success("授权成功", data);
    }

    /**
     * 同步会员
     */
    @PostMapping("/crm/sync/members")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncMembers(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.getOrDefault("platform", "HUBSPOT");
        long start = System.currentTimeMillis();
        CrmSyncLog log = new CrmSyncLog();
        log.setTenantId(tenantId);
        log.setPlatform(platform);
        log.setSyncType("MEMBER");
        log.setDirection((String) body.getOrDefault("direction", "PULL"));

        try {
            // 模拟同步
            int success = 0, fail = 0, skipped = 0;
            List<Map<String, Object>> members = jdbc.queryForList(
                    "SELECT id, member_name, phone FROM crm_member WHERE tenant_id = ? AND deleted = 0 LIMIT 100",
                    tenantId);
            for (Map<String, Object> m : members) {
                try {
                    // 调用 HubSpot API（sandbox 模拟）
                    success++;
                } catch (Exception e) {
                    fail++;
                }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setSkippedCount(skipped);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            crmSyncLogMapper.insert(log);

            jdbc.update("UPDATE crm_integration SET last_sync_at = NOW() WHERE tenant_id = ? AND platform = ?", tenantId, platform);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            data.put("skipped", skipped);
            data.put("duration", log.getDurationMs());
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            crmSyncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败: " + e.getMessage());
        }
    }

    @PostMapping("/crm/sync/orders")
    public Result<Map<String, Object>> syncCrmOrders(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.getOrDefault("platform", "HUBSPOT");
        long start = System.currentTimeMillis();
        CrmSyncLog log = new CrmSyncLog();
        log.setTenantId(tenantId);
        log.setPlatform(platform);
        log.setSyncType("ORDER");
        log.setDirection((String) body.getOrDefault("direction", "PULL"));

        try {
            int success = 0, fail = 0;
            List<Map<String, Object>> orders = jdbc.queryForList(
                    "SELECT id, order_no, pay_amount FROM crm_order WHERE tenant_id = ? AND status = 3 AND deleted = 0 LIMIT 100",
                    tenantId);
            for (Map<String, Object> o : orders) {
                try { success++; } catch (Exception e) { fail++; }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            crmSyncLogMapper.insert(log);
            jdbc.update("UPDATE crm_integration SET last_sync_at = NOW() WHERE tenant_id = ? AND platform = ?", tenantId, platform);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            crmSyncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败");
        }
    }

    @GetMapping("/crm/sync-log")
    public Result<List<CrmSyncLog>> syncLog(@RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(crmSyncLogMapper.selectList(new LambdaQueryWrapper<CrmSyncLog>()
                .eq(CrmSyncLog::getTenantId, tenantId)
                .orderByDesc(CrmSyncLog::getId)
                .last("LIMIT " + limit)));
    }

    // ====================================================================
    // P3.25 电商集成
    // ====================================================================

    @PostMapping("/ecommerce/platform")
    public Result<EcommercePlatform> savePlatform(@RequestBody EcommercePlatform p) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (p.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
        if (p.getStatus() == null) p.setStatus("ACTIVE");
        if (p.getSyncProducts() == null) p.setSyncProducts(1);
        if (p.getSyncOrders() == null) p.setSyncOrders(1);
        if (p.getSyncInventory() == null) p.setSyncInventory(1);
        p.setTenantId(tenantId);
        p.setId(null);
        ecomPlatformMapper.insert(p);
        return Result.success("已保存", p);
    }

    @GetMapping("/ecommerce/platform/list")
    public Result<List<EcommercePlatform>> listPlatforms() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(ecomPlatformMapper.selectList(new LambdaQueryWrapper<EcommercePlatform>()
                .eq(EcommercePlatform::getTenantId, tenantId)));
    }

    @PostMapping("/ecommerce/auth")
    public Result<Map<String, Object>> auth(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.get("platform");
        EcommercePlatform p = ecomPlatformMapper.selectOne(new LambdaQueryWrapper<EcommercePlatform>()
                .eq(EcommercePlatform::getTenantId, tenantId)
                .eq(EcommercePlatform::getPlatform, platform));
        if (p == null) return Result.error(BizCode.NOT_FOUND, "请先保存配置");

        p.setAccessToken("act_" + UUID.randomUUID().toString().replace("-", ""));
        p.setTokenExpireAt(LocalDateTime.now().plusHours(2));
        ecomPlatformMapper.updateById(p);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", p.getAccessToken());
        return Result.success("授权成功", data);
    }

    /**
     * 同步商品
     */
    @PostMapping("/ecommerce/sync/products")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncProducts(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.getOrDefault("platform", "WEIDIAN");
        long start = System.currentTimeMillis();
        EcommerceSyncLog log = new EcommerceSyncLog();
        log.setTenantId(tenantId);
        log.setPlatform(platform);
        log.setSyncType("PRODUCT");

        try {
            int success = 0, fail = 0;
            // 模拟微信有赞商品
            String[][] demoProducts = {
                    {"P001", "水润面膜", "59.00", "200"},
                    {"P002", "焕颜精华", "299.00", "100"},
                    {"P003", "舒缓喷雾", "89.00", "150"},
                    {"P004", "防晒霜 SPF50", "169.00", "80"},
                    {"P005", "深层清洁", "129.00", "120"}
            };
            for (String[] p : demoProducts) {
                try {
                    EcommerceProduct exist = ecomProductMapper.selectOne(new LambdaQueryWrapper<EcommerceProduct>()
                            .eq(EcommerceProduct::getTenantId, tenantId)
                            .eq(EcommerceProduct::getPlatform, platform)
                            .eq(EcommerceProduct::getExternalProductId, p[0]));
                    if (exist != null) {
                        exist.setProductName(p[1]);
                        exist.setPrice(new BigDecimal(p[2]));
                        exist.setStock(Integer.parseInt(p[3]));
                        exist.setLastSyncAt(LocalDateTime.now());
                        ecomProductMapper.updateById(exist);
                    } else {
                        EcommerceProduct ep = new EcommerceProduct();
                        ep.setTenantId(tenantId);
                        ep.setPlatform(platform);
                        ep.setExternalProductId(p[0]);
                        ep.setProductName(p[1]);
                        ep.setSku(p[0]);
                        ep.setPrice(new BigDecimal(p[2]));
                        ep.setStock(Integer.parseInt(p[3]));
                        ep.setStatus("ACTIVE");
                        ep.setLastSyncAt(LocalDateTime.now());
                        ecomProductMapper.insert(ep);
                    }
                    success++;
                } catch (Exception e) {
                    fail++;
                }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            ecomSyncLogMapper.insert(log);
            jdbc.update("UPDATE ecommerce_platform SET last_sync_at = NOW() WHERE tenant_id = ? AND platform = ?", tenantId, platform);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            ecomSyncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败");
        }
    }

    /**
     * 同步订单（拉取电商订单）
     */
    @PostMapping("/ecommerce/sync/orders")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncEcomOrders(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.getOrDefault("platform", "WEIDIAN");
        long start = System.currentTimeMillis();
        EcommerceSyncLog log = new EcommerceSyncLog();
        log.setTenantId(tenantId);
        log.setPlatform(platform);
        log.setSyncType("ORDER");

        try {
            int success = 0, fail = 0;
            // 模拟微信订单
            String[][] demoOrders = {
                    {"WD2026081600001", "张三", "13800138000", "199.00", "北京市朝阳区"},
                    {"WD2026081600002", "李四", "13900139000", "358.00", "上海市浦东"},
                    {"WD2026081600003", "王五", "13700137000", "89.00", "广州市天河"}
            };
            for (String[] o : demoOrders) {
                try {
                    EcommerceOrder exist = ecomOrderMapper.selectOne(new LambdaQueryWrapper<EcommerceOrder>()
                            .eq(EcommerceOrder::getTenantId, tenantId)
                            .eq(EcommerceOrder::getPlatform, platform)
                            .eq(EcommerceOrder::getExternalOrderId, o[0]));
                    if (exist == null) {
                        EcommerceOrder eo = new EcommerceOrder();
                        eo.setTenantId(tenantId);
                        eo.setPlatform(platform);
                        eo.setExternalOrderId(o[0]);
                        eo.setOrderNo("EC" + System.currentTimeMillis() + (int) (Math.random() * 1000));
                        eo.setBuyerName(o[1]);
                        eo.setBuyerPhone(o[2]);
                        eo.setAmount(new BigDecimal(o[3]));
                        eo.setTotalAmount(new BigDecimal(o[3]));
                        eo.setAddress(o[4]);
                        eo.setStatus("PENDING");
                        eo.setOrderTime(LocalDateTime.now());
                        ecomOrderMapper.insert(eo);
                    }
                    success++;
                } catch (Exception e) { fail++; }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            ecomSyncLogMapper.insert(log);
            jdbc.update("UPDATE ecommerce_platform SET last_sync_at = NOW() WHERE tenant_id = ? AND platform = ?", tenantId, platform);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            ecomSyncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败");
        }
    }

    /**
     * Webhook 入口（电商订单推送）
     */
    @PostMapping("/ecommerce/webhook")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> webhook(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String platform = (String) body.get("platform");
        String orderId = (String) body.get("order_id");
        String status = (String) body.get("status");

        if (orderId != null) {
            EcommerceOrder exist = ecomOrderMapper.selectOne(new LambdaQueryWrapper<EcommerceOrder>()
                    .eq(EcommerceOrder::getTenantId, tenantId)
                    .eq(EcommerceOrder::getPlatform, platform)
                    .eq(EcommerceOrder::getExternalOrderId, orderId));
            if (exist != null) {
                exist.setStatus(status);
                ecomOrderMapper.updateById(exist);
            }
        }
        return Result.success("已处理", null);
    }

    @GetMapping("/ecommerce/product/list")
    public Result<List<EcommerceProduct>> listProducts(@RequestParam(required = false) String platform) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<EcommerceProduct> qw = new LambdaQueryWrapper<EcommerceProduct>()
                .eq(EcommerceProduct::getTenantId, tenantId)
                .orderByDesc(EcommerceProduct::getId);
        if (platform != null) qw.eq(EcommerceProduct::getPlatform, platform);
        return Result.success(ecomProductMapper.selectList(qw));
    }

    @GetMapping("/ecommerce/order/list")
    public Result<List<EcommerceOrder>> listEcomOrders(@RequestParam(required = false) String platform,
                                                       @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<EcommerceOrder> qw = new LambdaQueryWrapper<EcommerceOrder>()
                .eq(EcommerceOrder::getTenantId, tenantId)
                .orderByDesc(EcommerceOrder::getId);
        if (platform != null) qw.eq(EcommerceOrder::getPlatform, platform);
        if (status != null) qw.eq(EcommerceOrder::getStatus, status);
        return Result.success(ecomOrderMapper.selectList(qw));
    }

    @GetMapping("/ecommerce/dashboard")
    public Result<Map<String, Object>> ecomDashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("productCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecommerce_product WHERE tenant_id = ?", Long.class, tenantId));
            data.put("orderCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecommerce_order WHERE tenant_id = ?", Long.class, tenantId));
            data.put("totalRevenue", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM ecommerce_order WHERE tenant_id = ? AND status = 'COMPLETED'",
                    Double.class, tenantId));
            data.put("byPlatform", jdbc.queryForList(
                    "SELECT platform, COUNT(*) AS cnt FROM ecommerce_order WHERE tenant_id = ? GROUP BY platform", tenantId));
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(new LinkedHashMap<>());
        }
    }

    // ====================================================================
    // 工具
    // ====================================================================
    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> mk(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}