package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.Brand;
import com.meiyetec.entity.BrandStoreRel;
import com.meiyetec.entity.HqCrossStoreMetric;
import com.meiyetec.entity.HqDashboardSnapshot;
import com.meiyetec.entity.HqDashboardWidget;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.10：品牌管理 + 总部看板
 *
 * <p>提供：
 * <ul>
 *   <li>品牌 CRUD（多品牌连锁）</li>
 *   <li>品牌 × 门店关联</li>
 *   <li>总部看板组件（KPI / CHART / TABLE / LIST）</li>
 *   <li>跨门店指标聚合</li>
 *   <li>看板快照</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/brand")
@RequiredArgsConstructor
public class BrandManagementService {

    private final BrandMapper brandMapper;
    private final BrandStoreRelMapper relMapper;
    private final HqDashboardWidgetMapper widgetMapper;
    private final HqDashboardSnapshotMapper snapshotMapper;
    private final HqCrossStoreMetricMapper metricMapper;

    // ====================================================================
    // 1. 品牌管理
    // ====================================================================

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Brand> upsertBrand(@RequestBody Brand b) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        b.setTenantId(tenantId);
        if (b.getStatus() == null) b.setStatus("ACTIVE");
        if (b.getSortOrder() == null) b.setSortOrder(100);

        Brand exist = brandMapper.selectOne(
                new LambdaQueryWrapper<Brand>()
                        .eq(Brand::getTenantId, tenantId)
                        .eq(Brand::getBrandCode, b.getBrandCode()));
        if (exist != null) {
            exist.setBrandName(b.getBrandName());
            exist.setLogoUrl(b.getLogoUrl());
            exist.setDescription(b.getDescription());
            exist.setContactPhone(b.getContactPhone());
            exist.setContactEmail(b.getContactEmail());
            exist.setWebsite(b.getWebsite());
            exist.setAddress(b.getAddress());
            exist.setStatus(b.getStatus());
            brandMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        brandMapper.insert(b);
        return Result.success("已创建", b);
    }

    @GetMapping("/list")
    public Result<List<Brand>> listBrands() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(brandMapper.selectList(
                new LambdaQueryWrapper<Brand>()
                        .eq(Brand::getTenantId, tenantId)
                        .eq(Brand::getStatus, "ACTIVE")
                        .orderByAsc(Brand::getSortOrder)));
    }

    @GetMapping("/{code}")
    public Result<Brand> getBrand(@PathVariable String code) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(brandMapper.selectOne(
                new LambdaQueryWrapper<Brand>()
                        .eq(Brand::getTenantId, tenantId)
                        .eq(Brand::getBrandCode, code)));
    }

    // ====================================================================
    // 2. 品牌 × 门店关联
    // ====================================================================

    @PostMapping("/store/rel")
    @Transactional(rollbackFor = Exception.class)
    public Result<BrandStoreRel> relateStore(@RequestBody BrandStoreRel r) {
        if (r.getBrandId() == null || r.getStoreId() == null)
            return Result.error(BizCode.BAD_REQUEST, "brandId / storeId 必填");
        if (r.getIsFlagship() == null) r.setIsFlagship(0);
        if (r.getIsActive() == null) r.setIsActive(1);
        if (r.getJoinAt() == null) r.setJoinAt(LocalDate.now());

        BrandStoreRel exist = relMapper.selectOne(
                new LambdaQueryWrapper<BrandStoreRel>()
                        .eq(BrandStoreRel::getBrandId, r.getBrandId())
                        .eq(BrandStoreRel::getStoreId, r.getStoreId()));
        if (exist != null) {
            exist.setIsActive(1);
            exist.setLeaveAt(null);
            relMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        relMapper.insert(r);
        return Result.success("已关联", r);
    }

    @PostMapping("/store/unrel")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unrelStore(@RequestBody Map<String, Object> body) {
        Long brandId = ((Number) body.get("brandId")).longValue();
        Long storeId = ((Number) body.get("storeId")).longValue();
        BrandStoreRel r = relMapper.selectOne(
                new LambdaQueryWrapper<BrandStoreRel>()
                        .eq(BrandStoreRel::getBrandId, brandId)
                        .eq(BrandStoreRel::getStoreId, storeId));
        if (r != null) {
            r.setIsActive(0);
            r.setLeaveAt(LocalDate.now());
            relMapper.updateById(r);
        }
        return Result.success("已解绑", null);
    }

    @GetMapping("/{brandId}/stores")
    public Result<List<BrandStoreRel>> brandStores(@PathVariable Long brandId) {
        return Result.success(relMapper.selectList(
                new LambdaQueryWrapper<BrandStoreRel>()
                        .eq(BrandStoreRel::getBrandId, brandId)
                        .eq(BrandStoreRel::getIsActive, 1)));
    }

    // ====================================================================
    // 3. 总部看板组件
    // ====================================================================

    @PostMapping("/widget")
    @Transactional(rollbackFor = Exception.class)
    public Result<HqDashboardWidget> upsertWidget(@RequestBody HqDashboardWidget w) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        w.setTenantId(tenantId);
        if (w.getRefreshSec() == null) w.setRefreshSec(60);
        if (w.getEnabled() == null) w.setEnabled(1);
        if (w.getSortOrder() == null) w.setSortOrder(100);

        HqDashboardWidget exist = widgetMapper.selectOne(
                new LambdaQueryWrapper<HqDashboardWidget>()
                        .eq(HqDashboardWidget::getTenantId, tenantId)
                        .eq(HqDashboardWidget::getWidgetCode, w.getWidgetCode()));
        if (exist != null) {
            widgetMapper.updateById(w);
            return Result.success("已更新", w);
        }
        widgetMapper.insert(w);
        return Result.success("已创建", w);
    }

    @GetMapping("/widget/list")
    public Result<List<HqDashboardWidget>> listWidgets() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(widgetMapper.selectList(
                new LambdaQueryWrapper<HqDashboardWidget>()
                        .eq(HqDashboardWidget::getTenantId, tenantId)
                        .eq(HqDashboardWidget::getEnabled, 1)
                        .orderByAsc(HqDashboardWidget::getSortOrder)));
    }

    // ====================================================================
    // 4. 跨门店指标
    // ====================================================================

    @PostMapping("/metric/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<HqCrossStoreMetric> submitMetric(@RequestBody HqCrossStoreMetric m) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        m.setTenantId(tenantId);
        if (m.getPeriodKey() == null) m.setPeriodKey(LocalDate.now().toString());

        HqCrossStoreMetric exist = metricMapper.selectOne(
                new LambdaQueryWrapper<HqCrossStoreMetric>()
                        .eq(HqCrossStoreMetric::getTenantId, tenantId)
                        .eq(HqCrossStoreMetric::getPeriodType, m.getPeriodType())
                        .eq(HqCrossStoreMetric::getPeriodKey, m.getPeriodKey()));
        if (exist != null) {
            exist.setTotalStores(m.getTotalStores());
            exist.setActiveStores(m.getActiveStores());
            exist.setTotalRevenue(m.getTotalRevenue());
            exist.setTotalOrders(m.getTotalOrders());
            exist.setTotalMembers(m.getTotalMembers());
            exist.setNewMembers(m.getNewMembers());
            if (m.getTotalOrders() != null && m.getTotalOrders() > 0) {
                exist.setAvgOrderAmt(
                        m.getTotalRevenue() != null
                                ? m.getTotalRevenue().divide(BigDecimal.valueOf(m.getTotalOrders()), 2, java.math.RoundingMode.HALF_UP)
                                : BigDecimal.ZERO);
            }
            metricMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        metricMapper.insert(m);
        return Result.success("已记录", m);
    }

    @GetMapping("/metric/list")
    public Result<List<HqCrossStoreMetric>> listMetrics(@RequestParam(defaultValue = "DAY") String periodType,
                                                         @RequestParam(required = false) String periodKey,
                                                         @RequestParam(defaultValue = "30") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<HqCrossStoreMetric> qw = new LambdaQueryWrapper<>();
        qw.eq(HqCrossStoreMetric::getTenantId, tenantId)
                .eq(HqCrossStoreMetric::getPeriodType, periodType);
        if (periodKey != null) qw.eq(HqCrossStoreMetric::getPeriodKey, periodKey);
        qw.orderByDesc(HqCrossStoreMetric::getPeriodKey).last("LIMIT " + limit);
        return Result.success(metricMapper.selectList(qw));
    }

    // ====================================================================
    // 5. 看板快照
    // ====================================================================

    @PostMapping("/snapshot")
    @Transactional(rollbackFor = Exception.class)
    public Result<HqDashboardSnapshot> takeSnapshot(@RequestBody HqDashboardSnapshot s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        s.setTenantId(tenantId);
        if (s.getSnapshotAt() == null) s.setSnapshotAt(LocalDateTime.now());
        snapshotMapper.insert(s);
        return Result.success("已快照", s);
    }

    @GetMapping("/snapshot/latest")
    public Result<List<HqDashboardSnapshot>> latestSnapshot(@RequestParam(required = false) String periodKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<HqDashboardSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(HqDashboardSnapshot::getTenantId, tenantId);
        if (periodKey != null) qw.eq(HqDashboardSnapshot::getPeriodKey, periodKey);
        qw.orderByDesc(HqDashboardSnapshot::getSnapshotAt).last("LIMIT 1");
        return Result.success(snapshotMapper.selectList(qw));
    }
}