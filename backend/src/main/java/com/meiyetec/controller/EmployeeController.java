package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.EmployeeDTO;
import com.meiyetec.dto.PageQueryDTO;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.Store;
import com.meiyetec.exception.BizCode;
import com.meiyetec.service.EmployeeService;
import com.meiyetec.service.StoreService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.meiyetec.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/employee")
@RequiredArgsConstructor
@SysOpLog(module = "员工")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final StoreService storeService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Long tenantId() {
        Long id = TenantContextHolder.get().getTenantId();
        if (id == null) return null;
        return id;
    }

    private LambdaQueryWrapper<Employee> baseWrapper(Long tid) {
        return new LambdaQueryWrapper<Employee>().eq(Employee::getTenantId, tid);
    }

    @GetMapping("/list")
    public Result<List<Employee>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String position) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        LambdaQueryWrapper<Employee> wrapper = baseWrapper(tid);
        if (storeId != null) {
            wrapper.eq(Employee::getStoreId, storeId);
        }
        if (position != null && !position.isEmpty()) {
            wrapper.eq(Employee::getPosition, position);
        }
        List<Employee> list = employeeService.list(wrapper.orderByAsc(Employee::getId));
        Map<Long, String> storeMap = storeService.list().stream()
                .collect(Collectors.toMap(Store::getId, Store::getStoreName));
        list.forEach(e -> e.setStoreName(storeMap.get(e.getStoreId())));
        return Result.success(list);
    }

    @PostMapping("/page")
    public Result<PageResult<Employee>> page(@RequestBody PageQueryDTO query) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Page<Employee> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Employee> wrapper = baseWrapper(tid);
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(Employee::getEmployeeName, query.getKeyword())
                    .or().like(Employee::getEmployeeCode, query.getKeyword())
                    .or().like(Employee::getPhone, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Employee::getStatus, query.getStatus());
        }
        if (query.getStoreId() != null) {
            wrapper.eq(Employee::getStoreId, query.getStoreId());
        }
        wrapper.orderByAsc(Employee::getId);
        IPage<Employee> result = employeeService.page(page, wrapper);
        Map<Long, String> storeMap = storeService.list().stream()
                .collect(Collectors.toMap(Store::getId, Store::getStoreName));
        result.getRecords().forEach(e -> e.setStoreName(storeMap.get(e.getStoreId())));
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        List<Employee> all = employeeService.list(baseWrapper(tid));
        Map<String, Object> s = new HashMap<>();
        s.put("total", all.size());
        s.put("active", all.stream().filter(e -> e.getStatus() != null && e.getStatus() == 1).count());
        s.put("leave", all.stream().filter(e -> e.getStatus() != null && e.getStatus() == 2).count());
        s.put("quit", all.stream().filter(e -> e.getStatus() != null && e.getStatus() == 0).count());
        return Result.success(s);
    }

    @GetMapping("/{id:\\d+}")
    public Result<Employee> getById(@PathVariable Long id) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Employee e = employeeService.getOne(baseWrapper(tid).eq(Employee::getId, id));
        if (e == null) return Result.error(BizCode.NOT_FOUND, "员工不存在");
        return Result.success(e);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody EmployeeDTO dto) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Employee e = new Employee();
        org.springframework.beans.BeanUtils.copyProperties(dto, e);
        e.setTenantId(tid);
        if (e.getPassword() != null && !e.getPassword().isEmpty()) {
            e.setPassword(passwordEncoder.encode(e.getPassword()));
        }
        employeeService.save(e);
        return Result.success("创建成功", null);
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody EmployeeDTO dto) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        if (dto.getId() == null) return Result.error(BizCode.BAD_REQUEST, "员工ID不能为空");
        Employee old = employeeService.getOne(baseWrapper(tid).eq(Employee::getId, dto.getId()));
        if (old == null) return Result.error(BizCode.NOT_FOUND, "员工不存在");
        Employee e = new Employee();
        org.springframework.beans.BeanUtils.copyProperties(dto, e);
        e.setTenantId(old.getTenantId());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            e.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        employeeService.updateById(e);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Employee e = employeeService.getOne(baseWrapper(tid).eq(Employee::getId, id));
        if (e == null) return Result.error(BizCode.NOT_FOUND, "员工不存在");
        employeeService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id:\\d+}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Employee e = employeeService.getOne(baseWrapper(tid).eq(Employee::getId, id));
        if (e == null) return Result.error(BizCode.NOT_FOUND, "员工不存在");
        String pwd = body.get("password");
        if (pwd == null || pwd.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "密码不能为空");
        Employee u = new Employee();
        u.setId(id);
        u.setPassword(passwordEncoder.encode(pwd));
        employeeService.updateById(u);
        return Result.success("密码重置成功", null);
    }
}
