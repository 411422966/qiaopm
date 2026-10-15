package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.SysPermission;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.security.PermissionAutoRegistrar;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 权限管理 API（P0-2 权限注册配套）
 *
 * <p>提供：</p>
 * <ul>
 *   <li>{@code GET  /sys/permission/list}        - 分页查权限码（可按 module 过滤）</li>
 *   <li>{@code GET  /sys/permission/page}        - 分页（含 type）</li>
 *   <li>{@code POST /sys/permission/sync}        - 触发 PermissionAutoRegistrar 重扫</li>
 *   <li>{@code POST /sys/permission}             - 手动新增权限码（兜底）</li>
 *   <li>{@code PUT  /sys/permission/{id}}        - 更新名称 / 状态</li>
 *   <li>{@code DELETE /sys/permission/{id}}      - 删除（软删）</li>
 * </ul>
 *
 * <p>权限码同步流程：</p>
 * <pre>
 *   1) 开发者在 Controller 方法上加 {@code @RequirePermission("xxx:yyy")}
 *   2) 应用启动期 PermissionAutoRegistrar 自动 INSERT sys_permission
 *   3) 也可手动调 POST /sys/permission/sync 触发重扫（admin only）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/sys/permission")
@RequiredArgsConstructor
@SysOpLog(module = "权限管理")
public class SysPermissionController {

    private final SysPermissionMapper permissionMapper;
    private final PermissionAutoRegistrar autoRegistrar;

    @GetMapping("/list")
    @RequirePermission("sys:permission:list")
    public Result<List<SysPermission>> list(@RequestParam(required = false) String module) {
        LambdaQueryWrapper<SysPermission> w = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            w.like(SysPermission::getPermissionCode, module + ":%");
        }
        w.orderByAsc(SysPermission::getSortOrder);
        w.orderByAsc(SysPermission::getId);
        return Result.success(permissionMapper.selectList(w));
    }

    @GetMapping("/page")
    @RequirePermission("sys:permission:list")
    public Result<PageResult<SysPermission>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysPermission> w = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            w.like(SysPermission::getPermissionCode, module + ":%");
        }
        if (status != null) w.eq(SysPermission::getStatus, status);
        w.orderByAsc(SysPermission::getSortOrder);
        w.orderByAsc(SysPermission::getId);
        Page<SysPermission> page = new Page<>(pageNum, pageSize);
        return Result.success(PageResult.of(permissionMapper.selectPage(page, w)));
    }

    @PostMapping("/sync")
    @RequirePermission("sys:permission:sync")
    public Result<Map<String, Object>> syncPermissions() {
        // 只允许平台超管
        requirePlatformAdmin();
        int added = autoRegistrar.scanAndRegister();
        // 统计总数
        long total = permissionMapper.selectCount(null);
        Map<String, Object> data = new HashMap<>();
        data.put("addedThisRun", added);
        data.put("totalInDb", total);
        return Result.success("同步完成，新增 " + added + " 条", data);
    }

    @PostMapping
    @RequirePermission("sys:permission:add")
    public Result<Void> create(@Valid @RequestBody SysPermission p) {
        requirePlatformAdmin();
        if (p.getPermissionCode() == null || p.getPermissionCode().isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "permissionCode 不能为空");
        }
        Long exist = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, p.getPermissionCode()));
        if (exist > 0) {
            throw new BusinessException(BizCode.CONFLICT, "权限码已存在：" + p.getPermissionCode());
        }
        if (p.getPermissionType() == null) p.setPermissionType("API");
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getSortOrder() == null) p.setSortOrder(0);
        permissionMapper.insert(p);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:permission:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPermission p) {
        requirePlatformAdmin();
        SysPermission exist = permissionMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "权限码不存在");
        }
        // permissionCode 不可改
        p.setId(id);
        p.setPermissionCode(null);
        permissionMapper.updateById(p);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:permission:delete")
    public Result<Void> delete(@PathVariable Long id) {
        requirePlatformAdmin();
        permissionMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 平台超管校验（仅 SUPER_ADMIN / PLATFORM_ADMIN 可调）
     */
    private void requirePlatformAdmin() {
        String role = TenantContextHolder.getRoleCode();
        if (role == null || !(role.contains("SUPER_ADMIN") || role.contains("PLATFORM_ADMIN"))) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅平台超管可操作");
        }
    }
}
