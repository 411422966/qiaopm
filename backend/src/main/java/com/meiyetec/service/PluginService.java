package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PluginInstall;
import com.meiyetec.entity.PluginMarket;
import com.meiyetec.entity.PluginReview;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.PluginInstallMapper;
import com.meiyetec.mapper.PluginMarketMapper;
import com.meiyetec.mapper.PluginReviewMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 插件市场（FEATURE_COMPARISON_MATRIX.md 280-287 P3-22）
 */
@Slf4j
@RestController
@RequestMapping("/plugin")
@RequiredArgsConstructor
public class PluginService {

    private final PluginMarketMapper marketMapper;
    private final PluginInstallMapper installMapper;
    private final PluginReviewMapper reviewMapper;

    @PostMapping("/market")
    public Result<PluginMarket> publish(@RequestBody PluginMarket p) {
        if (p.getPluginCode() == null || p.getPluginName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (p.getStatus() == null) p.setStatus("PUBLISHED");
        if (p.getVersion() == null) p.setVersion("1.0.0");
        if (p.getInstallCount() == null) p.setInstallCount(0);
        if (p.getRating() == null) p.setRating(BigDecimal.ZERO);
        p.setTenantId(0L);
        p.setId(null);
        marketMapper.insert(p);
        return Result.success("已上架", p);
    }

    @GetMapping("/market/list")
    public Result<List<PluginMarket>> listMarket(@RequestParam(required = false) String category,
                                                @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PluginMarket> qw = new LambdaQueryWrapper<PluginMarket>()
                .orderByDesc(PluginMarket::getInstallCount);
        if (category != null && !category.isEmpty()) qw.eq(PluginMarket::getCategory, category);
        if (status != null && !status.isEmpty()) qw.eq(PluginMarket::getStatus, status);
        else qw.eq(PluginMarket::getStatus, "PUBLISHED");
        return Result.success(marketMapper.selectList(qw));
    }

    @PostMapping("/market/{id}")
    public Result<PluginMarket> updatePlugin(@PathVariable Long id, @RequestBody PluginMarket p) {
        p.setId(id);
        marketMapper.updateById(p);
        return Result.success("已更新", marketMapper.selectById(id));
    }

    @PostMapping("/install")
    @Transactional(rollbackFor = Exception.class)
    public Result<PluginInstall> install(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long pluginId = ((Number) body.get("pluginId")).longValue();
        PluginMarket plugin = marketMapper.selectById(pluginId);
        if (plugin == null) return Result.error(BizCode.NOT_FOUND, "插件不存在");

        PluginInstall i = new PluginInstall();
        i.setTenantId(tenantId);
        i.setPluginId(pluginId);
        i.setPluginCode(plugin.getPluginCode());
        i.setVersion((String) body.getOrDefault("version", plugin.getVersion()));
        i.setInstalledAt(LocalDateTime.now());
        i.setInstallStatus("ACTIVE");
        i.setEnabled(1);
        installMapper.insert(i);

        // 累加安装数
        int cur = plugin.getInstallCount() == null ? 0 : plugin.getInstallCount();
        plugin.setInstallCount(cur + 1);
        marketMapper.updateById(plugin);
        return Result.success("已安装", i);
    }

    @PostMapping("/install/{id}/uninstall")
    public Result<Void> uninstall(@PathVariable Long id) {
        PluginInstall i = installMapper.selectById(id);
        if (i == null) return Result.error(BizCode.NOT_FOUND, "安装记录不存在");
        i.setInstallStatus("REMOVED");
        i.setEnabled(0);
        installMapper.updateById(i);
        return Result.success("已卸载", null);
    }

    @GetMapping("/install/list")
    public Result<List<PluginInstall>> listInstalls() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(installMapper.selectList(new LambdaQueryWrapper<PluginInstall>()
                .eq(PluginInstall::getTenantId, tenantId)
                .orderByDesc(PluginInstall::getId)));
    }

    @PostMapping("/review")
    public Result<PluginReview> review(@RequestBody Map<String, Object> body) {
        PluginReview r = new PluginReview();
        r.setPluginId(((Number) body.get("pluginId")).longValue());
        r.setRating(((Number) body.get("rating")).intValue());
        r.setContent((String) body.get("comment"));
        r.setMemberId(0L);
        r.setTitle((String) body.getOrDefault("title", "用户评价"));
        reviewMapper.insert(r);
        return Result.success("已评价", r);
    }

    @GetMapping("/review/list/{pluginId}")
    public Result<List<PluginReview>> listReviews(@PathVariable Long pluginId) {
        return Result.success(reviewMapper.selectList(new LambdaQueryWrapper<PluginReview>()
                .eq(PluginReview::getPluginId, pluginId)
                .orderByDesc(PluginReview::getId)));
    }
}