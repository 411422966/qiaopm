package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.BizServiceMaterial;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BizServiceMaterialMapper;
import com.meiyetec.mapper.StockProductMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/material/binding")
@RequiredArgsConstructor
@SysOpLog(module = "ServiceMaterial")
public class ServiceMaterialController {

    private final BizServiceMaterialMapper mapper;
    private final StockProductMapper stockProductMapper;

    private Long currentTenantIdOrReject() {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) {
            throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        }
        return tid;
    }

    @GetMapping("/by-service/{serviceId}")
    @RequirePermission("material:binding:list")
    public Result<List<Map<String, Object>>> findByServiceId(@PathVariable Long serviceId) {
        Long tid = currentTenantIdOrReject();
        List<Map<String, Object>> rows = mapper.listBomByService(serviceId);
        rows = rows.stream()
                .filter(r -> tid.equals(((Number) r.get("tenant_id")).longValue()))
                .collect(java.util.stream.Collectors.toList());
        return Result.success(rows);
    }

    @PostMapping("/page")
    @RequirePermission("material:binding:list")
    public Result<PageResult<BizServiceMaterial>> page(@RequestBody Map<String, Object> params) {
        Long tid = currentTenantIdOrReject();
        int pageNum = (int) params.getOrDefault("pageNum", 1);
        int pageSize = (int) params.getOrDefault("pageSize", 20);
        Long serviceId = params.get("serviceId") == null ? null : Long.valueOf(params.get("serviceId").toString());
        Page<BizServiceMaterial> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizServiceMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizServiceMaterial::getTenantId, tid);
        if (serviceId != null) {
            wrapper.eq(BizServiceMaterial::getServiceId, serviceId);
        }
        wrapper.orderByAsc(BizServiceMaterial::getServiceId);
        Page<BizServiceMaterial> result = mapper.selectPage(page, wrapper);
        return Result.success(PageResult.of(result));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("material:binding:edit")
    public Result<BizServiceMaterial> create(@RequestBody BizServiceMaterial material) {
        Long tid = currentTenantIdOrReject();
        if (material.getServiceId() == null) {
            return Result.error(BizCode.BAD_REQUEST, "serviceId 必填");
        }
        if (material.getProductId() == null) {
            return Result.error(BizCode.BAD_REQUEST, "productId 必填");
        }
        if (material.getQuantity() == null
                || material.getQuantity().signum() <= 0) {
            return Result.error(BizCode.BAD_REQUEST, "quantity 必须 > 0");
        }
        // 校验耗材存在 + 同租户
        var product = stockProductMapper.selectById(material.getProductId());
        if (product == null || !tid.equals(product.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "耗材不存在或不属于当前租户");
        }
        // 唯一性：同一服务 + 同一耗材
        Long exist = mapper.selectCount(new LambdaQueryWrapper<BizServiceMaterial>()
                .eq(BizServiceMaterial::getTenantId, tid)
                .eq(BizServiceMaterial::getServiceId, material.getServiceId())
                .eq(BizServiceMaterial::getProductId, material.getProductId()));
        if (exist > 0) {
            return Result.error(BizCode.CONFLICT, "该服务已绑定该耗材，请勿重复添加");
        }
        if (material.getUnit() == null) {
            material.setUnit(product.getUnit());
        }
        if (material.getIsRequired() == null) {
            material.setIsRequired(1);
        }
        material.setId(null);
        material.setTenantId(tid);
        mapper.insert(material);
        return Result.success("创建成功", material);
    }

    @PutMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("material:binding:edit")
    public Result<BizServiceMaterial> update(@PathVariable Long id, @RequestBody BizServiceMaterial material) {
        Long tid = currentTenantIdOrReject();
        BizServiceMaterial old = mapper.selectById(id);
        if (old == null || !tid.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "BOM 不存在或不属于当前租户");
        }
        if (material.getQuantity() != null
                && material.getQuantity().signum() <= 0) {
            return Result.error(BizCode.BAD_REQUEST, "quantity 必须 > 0");
        }
        // 不允许改 serviceId / productId / tenantId（破坏唯一性）
        material.setId(id);
        material.setTenantId(tid);
        material.setServiceId(old.getServiceId());
        material.setProductId(old.getProductId());
        if (material.getUnit() == null) material.setUnit(old.getUnit());
        if (material.getIsRequired() == null) material.setIsRequired(old.getIsRequired());
        mapper.updateById(material);
        return Result.success("更新成功", mapper.selectById(id));
    }

    @DeleteMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("material:binding:edit")
    public Result<Void> delete(@PathVariable Long id) {
        Long tid = currentTenantIdOrReject();
        BizServiceMaterial old = mapper.selectById(id);
        if (old == null || !tid.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "BOM 不存在或不属于当前租户");
        }
        mapper.deleteById(id);
        return Result.success("删除成功", null);
    }
}