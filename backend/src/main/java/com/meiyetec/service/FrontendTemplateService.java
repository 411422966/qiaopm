package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MiniProgramVersion;
import com.meiyetec.entity.WebRouteAudit;
import com.meiyetec.entity.WebTemplate;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MiniProgramVersionMapper;
import com.meiyetec.mapper.WebRouteAuditMapper;
import com.meiyetec.mapper.WebTemplateMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.9：前端模板 + 小程序版本 + 路由审计
 */
@Slf4j
@RestController
@RequestMapping("/frontend")
@RequiredArgsConstructor
public class FrontendTemplateService {

    private final WebTemplateMapper templateMapper;
    private final MiniProgramVersionMapper mpVersionMapper;
    private final WebRouteAuditMapper routeAuditMapper;

    // ====================================================================
    // 1. Web 模板
    // ====================================================================

    @PostMapping("/template")
    @Transactional(rollbackFor = Exception.class)
    public Result<WebTemplate> upsertTemplate(@RequestBody WebTemplate t) {
        if (t.getTemplateCode() == null) return Result.error(BizCode.BAD_REQUEST, "templateCode 必填");
        if (t.getPlatformCode() == null) t.setPlatformCode("WEB");
        if (t.getFramework() == null) t.setFramework("vue");
        if (t.getTheme() == null) t.setTheme("default");
        if (t.getVersion() == null) t.setVersion("1.0.0");
        if (t.getEnabled() == null) t.setEnabled(1);

        WebTemplate exist = templateMapper.selectOne(
                new LambdaQueryWrapper<WebTemplate>().eq(WebTemplate::getTemplateCode, t.getTemplateCode()));
        if (exist != null) {
            exist.setTemplateName(t.getTemplateName());
            exist.setPlatformCode(t.getPlatformCode());
            exist.setFramework(t.getFramework());
            exist.setDescription(t.getDescription());
            exist.setPagesJson(t.getPagesJson());
            exist.setTheme(t.getTheme());
            exist.setVersion(t.getVersion());
            exist.setEnabled(t.getEnabled());
            exist.setDownloadUrl(t.getDownloadUrl());
            templateMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        templateMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/template/list")
    public Result<List<WebTemplate>> listTemplates(@RequestParam(required = false) String platformCode,
                                                     @RequestParam(required = false) String framework) {
        LambdaQueryWrapper<WebTemplate> qw = new LambdaQueryWrapper<>();
        if (platformCode != null) qw.eq(WebTemplate::getPlatformCode, platformCode);
        if (framework != null) qw.eq(WebTemplate::getFramework, framework);
        qw.eq(WebTemplate::getEnabled, 1);
        qw.orderByAsc(WebTemplate::getTemplateCode);
        return Result.success(templateMapper.selectList(qw));
    }

    @GetMapping("/template/{code}")
    public Result<WebTemplate> getTemplate(@PathVariable String code) {
        return Result.success(templateMapper.selectOne(
                new LambdaQueryWrapper<WebTemplate>().eq(WebTemplate::getTemplateCode, code)));
    }

    // ====================================================================
    // 2. 小程序版本
    // ====================================================================

    @PostMapping("/mp/version")
    @Transactional(rollbackFor = Exception.class)
    public Result<MiniProgramVersion> upsertMpVersion(@RequestBody MiniProgramVersion v) {
        if (v.getPlatformCode() == null || v.getVersionCode() == null)
            return Result.error(BizCode.BAD_REQUEST, "platformCode / versionCode 必填");
        if (v.getForceUpgrade() == null) v.setForceUpgrade(0);
        if (v.getGrayscale() == null) v.setGrayscale(0);
        if (v.getStatus() == null) v.setStatus("DRAFT");

        MiniProgramVersion exist = mpVersionMapper.selectOne(
                new LambdaQueryWrapper<MiniProgramVersion>()
                        .eq(MiniProgramVersion::getPlatformCode, v.getPlatformCode())
                        .eq(MiniProgramVersion::getVersionCode, v.getVersionCode()));
        if (exist != null) {
            exist.setVersionName(v.getVersionName());
            exist.setDownloadUrl(v.getDownloadUrl());
            exist.setQrCodeUrl(v.getQrCodeUrl());
            exist.setForceUpgrade(v.getForceUpgrade());
            exist.setMinClient(v.getMinClient());
            exist.setGrayscale(v.getGrayscale());
            exist.setStatus(v.getStatus());
            exist.setChangelog(v.getChangelog());
            mpVersionMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        mpVersionMapper.insert(v);
        return Result.success("已创建", v);
    }

    @PostMapping("/mp/version/{id}/release")
    @Transactional(rollbackFor = Exception.class)
    public Result<MiniProgramVersion> releaseMp(@PathVariable Long id) {
        MiniProgramVersion v = mpVersionMapper.selectById(id);
        if (v == null) return Result.error(BizCode.NOT_FOUND, "版本不存在");
        v.setStatus("RELEASED");
        v.setReleasedAt(LocalDateTime.now());
        mpVersionMapper.updateById(v);
        return Result.success("已发布", v);
    }

    @PostMapping("/mp/version/{id}/rollback")
    @Transactional(rollbackFor = Exception.class)
    public Result<MiniProgramVersion> rollbackMp(@PathVariable Long id) {
        MiniProgramVersion v = mpVersionMapper.selectById(id);
        if (v == null) return Result.error(BizCode.NOT_FOUND, "版本不存在");
        v.setStatus("ROLLBACK");
        mpVersionMapper.updateById(v);
        return Result.success("已回滚", v);
    }

    /**
     * 客户端升级检测（小程序）：返回最新版本信息
     */
    @GetMapping("/mp/latest")
    public Result<MiniProgramVersion> latestVersion(@RequestParam String platformCode) {
        List<MiniProgramVersion> list = mpVersionMapper.selectList(
                new LambdaQueryWrapper<MiniProgramVersion>()
                        .eq(MiniProgramVersion::getPlatformCode, platformCode)
                        .eq(MiniProgramVersion::getStatus, "RELEASED")
                        .orderByDesc(MiniProgramVersion::getReleasedAt)
                        .last("LIMIT 1"));
        return Result.success(list.isEmpty() ? null : list.get(0));
    }

    @GetMapping("/mp/version/list")
    public Result<List<MiniProgramVersion>> listMpVersions(@RequestParam String platformCode) {
        return Result.success(mpVersionMapper.selectList(
                new LambdaQueryWrapper<MiniProgramVersion>()
                        .eq(MiniProgramVersion::getPlatformCode, platformCode)
                        .orderByDesc(MiniProgramVersion::getId)));
    }

    // ====================================================================
    // 3. 前端路由审计
    // ====================================================================

    @PostMapping("/route/audit")
    @Transactional(rollbackFor = Exception.class)
    public Result<WebRouteAudit> auditRoute(@RequestBody WebRouteAudit a) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        a.setTenantId(tenantId);
        if (a.getVisitedAt() == null) a.setVisitedAt(LocalDateTime.now());
        if (a.getDurationMs() == null) a.setDurationMs(0);
        if (a.getApiCalls() == null) a.setApiCalls(0);
        routeAuditMapper.insert(a);
        return Result.success("已审计", a);
    }

    @GetMapping("/route/recent")
    public Result<List<WebRouteAudit>> recentRoutes(@RequestParam(required = false) Long userId,
                                                       @RequestParam(defaultValue = "20") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WebRouteAudit> qw = new LambdaQueryWrapper<>();
        qw.eq(WebRouteAudit::getTenantId, tenantId);
        if (userId != null) qw.eq(WebRouteAudit::getUserId, userId);
        qw.orderByDesc(WebRouteAudit::getId).last("LIMIT " + limit);
        return Result.success(routeAuditMapper.selectList(qw));
    }

    @GetMapping("/route/top")
    public Result<List<WebRouteAudit>> topRoutes(@RequestParam(required = false) String platformCode) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WebRouteAudit> qw = new LambdaQueryWrapper<>();
        qw.eq(WebRouteAudit::getTenantId, tenantId);
        if (platformCode != null) qw.eq(WebRouteAudit::getPlatformCode, platformCode);
        qw.orderByDesc(WebRouteAudit::getId).last("LIMIT 10");
        return Result.success(routeAuditMapper.selectList(qw));
    }
}