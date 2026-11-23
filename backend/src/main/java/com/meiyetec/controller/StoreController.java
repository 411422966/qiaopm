package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.PageQueryDTO;
import com.meiyetec.dto.StoreDTO;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.Store;
import com.meiyetec.security.DataScopeService;
import com.meiyetec.security.StoreScope;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.StoreService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
@SysOpLog(module = "门店")
public class StoreController {

    private final StoreService storeService;
    private final DataScopeService dataScopeService;

    @GetMapping("/list")
    @RequirePermission("store:list")
    public Result<List<Store>> list() {
        return Result.success(storeService.list());
    }

    /**
     * 当前会话可见的门店列表（I194-0：前端筛选下拉的数据源）。
     *
     * <p>与 {@code GET /store/list} 的差别：本端点按「租户 + 当前会话有效门店集合」过滤，
     * {@code /store/list} 不带任何过滤条件。</p>
     *
     * <p>语义与 {@link DataScopeService} 保持一致：</p>
     * <ul>
     *   <li>数据范围 = ALL → 本租户全部门店；</li>
     *   <li>数据范围受限 → 仅 {@code ctx.storeIds} 内的门店；</li>
     *   <li>可见门店为空 → 返回空列表（恒空集，不回退为「全部门店」）。</li>
     * </ul>
     */
    @GetMapping("/accessible")
    @RequirePermission("store:list")
    public Result<List<Store>> accessible() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StoreScope scope = dataScopeService.resolveStoreScope();
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(Store::getTenantId, tenantId);
        }
        if (!scope.isAll()) {
            // 空范围 → resolveIds() 返回 [-9999] → IN (-9999) 恒空集
            wrapper.in(Store::getId, scope.resolveIds());
        }
        wrapper.orderByAsc(Store::getId);
        return Result.success(storeService.list(wrapper));
    }

    @PostMapping("/page")
    @RequirePermission("store:list")
    public Result<PageResult<Store>> page(@RequestBody PageQueryDTO query) {
        Page<Store> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(Store::getStoreName, query.getKeyword())
                   .or().like(Store::getStoreCode, query.getKeyword())
                   .or().like(Store::getContactPerson, query.getKeyword());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Store::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Store::getId);
        return Result.success(PageResult.of(storeService.page(page, wrapper)));
    }

    @GetMapping("/stats")
    @RequirePermission("store:list")
    public Result<Map<String, Object>> stats() {
        List<Store> all = storeService.list();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("active", all.stream().filter(s -> s.getStatus() != null && s.getStatus() == 1).count());
        stats.put("inactive", all.stream().filter(s -> s.getStatus() != null && s.getStatus() == 0).count());
        return Result.success(stats);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("store:list")
    public Result<Store> getById(@PathVariable Long id) {
        return Result.success(storeService.getById(id));
    }

    @PostMapping
    @RequirePermission("store:edit")
    public Result<Void> create(@Valid @RequestBody StoreDTO dto) {
        Store store = new Store();
        org.springframework.beans.BeanUtils.copyProperties(dto, store);
        storeService.save(store);
        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("store:edit")
    public Result<Void> update(@Valid @RequestBody StoreDTO dto) {
        Store store = new Store();
        org.springframework.beans.BeanUtils.copyProperties(dto, store);
        storeService.updateById(store);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("store:edit")
    public Result<Void> delete(@PathVariable Long id) {
        storeService.removeById(id);
        return Result.success("删除成功", null);
    }
}

