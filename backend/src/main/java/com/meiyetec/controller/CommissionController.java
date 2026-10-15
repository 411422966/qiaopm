package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.*;
import com.meiyetec.util.Keys;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/commission")
@RequiredArgsConstructor
@SysOpLog(module = "提成")
public class CommissionController {

    private final CommissionRuleMapper ruleMapper;
    private final CommissionRecordMapper recordMapper;
    private final CommissionSalaryMapper salaryMapper;
    private final EmployeeMapper employeeMapper;
    private final CommissionCalculationService calculationService;
    private final CommissionSettlementMapper settlementMapper;
    private final CommissionSettlementService settlementService;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    @GetMapping("/rules/page")
    @RequirePermission("commission:rule:list")
    public Result<Page<CommissionRule>> rulePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "priority") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CommissionRule> query = new LambdaQueryWrapper<>();
        if (tenantId != null) query.eq(CommissionRule::getTenantId, tenantId);
        if (storeId != null) query.eq(CommissionRule::getStoreId, storeId);
        if (businessType != null && !businessType.isBlank()) query.eq(CommissionRule::getBusinessType, businessType);
        if (status != null) query.eq(CommissionRule::getStatus, status);
        if (keyword != null && !keyword.isBlank()) query.like(CommissionRule::getRuleName, keyword.trim());
        // 动态排序：优先级升序（数字小在前），ID降序（新建的在后）
        if ("priority".equalsIgnoreCase(sortField)) {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderByDesc(CommissionRule::getPriority).orderByDesc(CommissionRule::getId);
            } else {
                query.orderByAsc(CommissionRule::getPriority).orderByDesc(CommissionRule::getId);
            }
        } else if ("id".equalsIgnoreCase(sortField)) {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderByDesc(CommissionRule::getId);
            } else {
                query.orderByAsc(CommissionRule::getId);
            }
        } else if ("effectiveDate".equalsIgnoreCase(sortField)) {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderByDesc(CommissionRule::getEffectiveDate);
            } else {
                query.orderByAsc(CommissionRule::getEffectiveDate);
            }
        } else if ("ruleName".equalsIgnoreCase(sortField)) {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderByDesc(CommissionRule::getRuleName);
            } else {
                query.orderByAsc(CommissionRule::getRuleName);
            }
        } else {
            // 默认按优先级升序
            query.orderByAsc(CommissionRule::getPriority).orderByDesc(CommissionRule::getId);
        }
        return Result.success(ruleMapper.selectPage(new Page<>(pageNum, Math.min(pageSize, 200)), query));
    }

    @GetMapping("/rules/list")
    @RequirePermission("commission:rule:list")
    public Result<List<CommissionRule>> ruleList(@RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CommissionRule> query = new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getStatus, 1)
                .orderByDesc(CommissionRule::getPriority)
                .orderByDesc(CommissionRule::getId);
        if (tenantId != null) query.eq(CommissionRule::getTenantId, tenantId);
        if (storeId != null) query.eq(CommissionRule::getStoreId, storeId);
        return Result.success(ruleMapper.selectList(query));
    }

    @GetMapping("/rules/{id:\\d+}")
    @RequirePermission("commission:rule:detail")
    public Result<CommissionRule> getRule(@PathVariable Long id) {
        CommissionRule rule = ruleMapper.selectById(id);
        if (rule == null) return Result.error(404, "提成规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && rule.getTenantId() != null && !rule.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该规则");
        return Result.success(rule);
    }

    @PostMapping("/rules")
    @RequirePermission("commission:rule:add")
    public Result<CommissionRule> createRule(@RequestBody CommissionRule rule) {
        String error = validateRule(rule);
        if (error != null) return Result.error(400, error);
        // T07-D1：Controller 注入 tenant，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        rule.setTenantId(tenantId);
        rule.setId(null);
        applyRuleDefaults(rule);
        ruleMapper.insert(rule);
        return Result.success("规则创建成功", rule);
    }

    @PutMapping("/rules/{id:\\d+}")
    @RequirePermission("commission:rule:edit")
    public Result<CommissionRule> updateRule(@PathVariable Long id, @RequestBody CommissionRule rule) {
        CommissionRule existing = ruleMapper.selectById(id);
        if (existing == null) return Result.error(404, "提成规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权修改该规则");
        String error = validateRule(rule);
        if (error != null) return Result.error(400, error);
        rule.setId(id);
        rule.setTenantId(existing.getTenantId());
        applyRuleDefaults(rule);
        ruleMapper.updateById(rule);
        return Result.success("规则更新成功", ruleMapper.selectById(id));
    }

    @PutMapping("/rules/{id:\\d+}/status")
    @RequirePermission("commission:rule:status")
    public Result<CommissionRule> toggleRuleStatus(@PathVariable Long id) {
        CommissionRule rule = ruleMapper.selectById(id);
        if (rule == null) return Result.error(404, "提成规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && rule.getTenantId() != null && !rule.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该规则");
        rule.setStatus(rule.getStatus() != null && rule.getStatus() == 1 ? 0 : 1);
        ruleMapper.updateById(rule);
        return Result.success(rule.getStatus() == 1 ? "规则已启用" : "规则已停用", rule);
    }

    @DeleteMapping("/rules/{id:\\d+}")
    @RequirePermission("commission:rule:delete")
    public Result<Void> deleteRule(@PathVariable Long id) {
        CommissionRule rule = ruleMapper.selectById(id);
        if (rule == null) return Result.error(404, "提成规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && rule.getTenantId() != null && !rule.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权删除该规则");
        long used = recordMapper.selectCount(new LambdaQueryWrapper<CommissionRecord>()
                .eq(CommissionRecord::getRuleId, id));
        if (used > 0) return Result.error(400, "规则已有提成记录引用，请停用而非删除");
        ruleMapper.deleteById(id);
        return Result.success("规则删除成功", null);
    }

    @GetMapping("/rules/stats")
    @RequirePermission("commission:rule:stat")
    public Result<Map<String, Object>> ruleStats(@RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> stats = new HashMap<>();
        LambdaQueryWrapper<CommissionRule> allQuery = new LambdaQueryWrapper<>();
        if (tenantId != null) allQuery.eq(CommissionRule::getTenantId, tenantId);
        if (storeId != null) allQuery.eq(CommissionRule::getStoreId, storeId);
        stats.put("total", ruleMapper.selectCount(allQuery));

        LambdaQueryWrapper<CommissionRule> activeQuery = new LambdaQueryWrapper<>();
        if (tenantId != null) activeQuery.eq(CommissionRule::getTenantId, tenantId);
        if (storeId != null) activeQuery.eq(CommissionRule::getStoreId, storeId);
        activeQuery.eq(CommissionRule::getStatus, 1);
        stats.put("active", ruleMapper.selectCount(activeQuery));
        return Result.success(stats);
    }

    @PostMapping("/calculate/order/{orderId:\\d+}")
    @RequirePermission("commission:calculate")
    public Result<List<CommissionRecord>> calculateOrder(@PathVariable Long orderId) {
        try {
            return Result.success("提成计算完成", calculationService.calculateOrder(orderId));
        } catch (IllegalArgumentException ex) {
            return Result.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @GetMapping("/records/page")
    @RequirePermission("commission:record:list")
    public Result<Page<Map<String, Object>>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder(" WHERE r.deleted=0 ");
        List<Object> args = new java.util.ArrayList<>();
        if (tenantId != null) { where.append("AND r.tenant_id=? "); args.add(tenantId); }
        if (storeId != null) { where.append("AND r.store_id=? "); args.add(storeId); }
        if (employeeId != null) { where.append("AND r.employee_id=? "); args.add(employeeId); }
        if (salaryMonth != null && !salaryMonth.isBlank()) { where.append("AND r.salary_month=? "); args.add(salaryMonth); }
        if (status != null) { where.append("AND r.status=? "); args.add(status); }
        if (keyword != null && !keyword.isBlank()) {
            where.append("AND (r.employee_name LIKE ? OR r.order_no LIKE ? OR r.service_name LIKE ?) ");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM commission_record r" + where, Long.class, args.toArray());
        String sql = "SELECT r.* FROM commission_record r" + where + "ORDER BY r.business_time DESC,r.id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(Math.min(pageSize, 200));
        pageArgs.add((Math.max(pageNum, 1) - 1) * Math.min(pageSize, 200));
        List<Map<String, Object>> records = Keys.camel(jdbc.queryForList(sql, pageArgs.toArray()));
        Page<Map<String, Object>> page = new Page<>(pageNum, Math.min(pageSize, 200));
        page.setTotal(total == null ? 0 : total);
        page.setRecords(records);
        return Result.success(page);
    }

    @GetMapping("/records/stats")
    @RequirePermission("commission:record:stat")
    public Result<Map<String, Object>> recordStats(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth) {
        String month = normalizeMonth(salaryMonth);
        Long tenantId = TenantContextHolder.get().getTenantId();
        String tenantFilter = tenantId == null ? "" : " AND tenant_id=" + tenantId;
        String storeFilter = storeId == null ? "" : " AND store_id=" + storeId;
        Map<String, Object> result = Keys.camel(jdbc.queryForMap(
                "SELECT COUNT(*) record_count,COUNT(DISTINCT employee_id) employee_count," +
                        "COALESCE(SUM(CASE WHEN status=1 THEN base_amount ELSE 0 END),0) performance_amount," +
                        "COALESCE(SUM(CASE WHEN status=1 THEN commission_amount ELSE 0 END),0) commission_amount," +
                        "COALESCE(SUM(CASE WHEN status=2 THEN commission_amount ELSE 0 END),0) settled_amount " +
                        "FROM commission_record WHERE deleted=0 AND salary_month=?" + tenantFilter + storeFilter, month));
        result.put("salaryMonth", month);
        return Result.success(result);
    }

    @PostMapping("/salary/generate")
    @RequirePermission("commission:salary:add")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<CommissionSalary>> generateSalary(@RequestBody Map<String, Object> body) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        String month = normalizeMonth(body.get("salaryMonth") == null ? null : body.get("salaryMonth").toString());
        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .ne(Employee::getStatus, 0)
                .orderByAsc(Employee::getId));
        List<CommissionSalary> generated = new java.util.ArrayList<>();
        for (Employee employee : employees) {
            CommissionSalary salary = salaryMapper.selectOne(new LambdaQueryWrapper<CommissionSalary>()
                    .eq(CommissionSalary::getStoreId, storeId)
                    .eq(CommissionSalary::getEmployeeId, employee.getId())
                    .eq(CommissionSalary::getSalaryMonth, month));
            if (salary != null && salary.getStatus() != null && salary.getStatus() > 1) {
                generated.add(salary);
                continue;
            }
            Map<String, Object> sum = jdbc.queryForMap(
                    "SELECT COALESCE(SUM(base_amount),0) performance,COALESCE(SUM(commission_amount),0) commission " +
                            "FROM commission_record WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=?",
                    storeId, employee.getId(), month);
            BigDecimal performance = decimal(sum.get("performance"));
            BigDecimal commission = decimal(sum.get("commission"));
            if (salary == null) {
                salary = new CommissionSalary();
                salary.setTenantId(tenantId);
                salary.setStoreId(storeId);
                salary.setEmployeeId(employee.getId());
                salary.setEmployeeName(employee.getEmployeeName());
                salary.setSalaryMonth(month);
                salary.setAdjustmentAmount(BigDecimal.ZERO);
                salary.setStatus(1);
            }
            salary.setBaseSalary(employee.getSalary() == null ? BigDecimal.ZERO : employee.getSalary());
            salary.setPerformanceAmount(performance);
            salary.setCommissionAmount(commission);
            if (salary.getAdjustmentAmount() == null) salary.setAdjustmentAmount(BigDecimal.ZERO);
            salary.setPayableAmount(salary.getBaseSalary().add(commission).add(salary.getAdjustmentAmount()));
            if (salary.getId() == null) salaryMapper.insert(salary); else salaryMapper.updateById(salary);
            generated.add(salary);
        }
        return Result.success("工资报表生成成功", generated);
    }

    @GetMapping("/salary/page")
    @RequirePermission("commission:salary:list")
    public Result<Page<Map<String, Object>>> salaryPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder(" WHERE s.deleted=0 ");
        List<Object> args = new java.util.ArrayList<>();
        if (tenantId != null) { where.append(" AND s.tenant_id=?"); args.add(tenantId); }
        if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }
        if (employeeId != null) { where.append(" AND s.employee_id=?"); args.add(employeeId); }
        if (salaryMonth != null && !salaryMonth.isBlank()) { where.append(" AND s.salary_month=?"); args.add(salaryMonth); }
        if (status != null) { where.append(" AND s.status=?"); args.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM commission_salary s" + where, Long.class, args.toArray());
        String sql = "SELECT s.*, e.avatar_url avatar FROM commission_salary s " +
                "LEFT JOIN pos_employee e ON e.id=s.employee_id AND e.deleted=0" +
                where + " ORDER BY s.salary_month DESC,s.employee_id ASC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(Math.min(pageSize, 200));
        pageArgs.add((Math.max(pageNum, 1) - 1) * Math.min(pageSize, 200));
        List<Map<String, Object>> records = Keys.camel(jdbc.queryForList(sql, pageArgs.toArray()));
        Page<Map<String, Object>> page = new Page<>(pageNum, Math.min(pageSize, 200));
        page.setTotal(total == null ? 0 : total);
        page.setRecords(records);
        return Result.success(page);
    }

    @PutMapping("/salary/{id:\\d+}/adjustment")
    @RequirePermission("commission:salary:adjust")
    public Result<CommissionSalary> updateAdjustment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CommissionSalary salary = salaryMapper.selectById(id);
        if (salary == null) return Result.error(404, "工资记录不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && salary.getTenantId() != null && !salary.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该工资记录");
        if (salary.getStatus() != null && salary.getStatus() > 1) return Result.error(400, "已确认工资不能调整");
        BigDecimal adjustment = decimal(body.get("adjustmentAmount"));
        salary.setAdjustmentAmount(adjustment);
        salary.setRemark(body.get("remark") == null ? salary.getRemark() : body.get("remark").toString());
        salary.setPayableAmount(salary.getBaseSalary().add(salary.getCommissionAmount()).add(adjustment));
        salaryMapper.updateById(salary);
        return Result.success("调整成功", salary);
    }

    @PostMapping("/salary/{id:\\d+}/confirm")
    @RequirePermission("commission:salary:confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSalary> confirmSalary(@PathVariable Long id) {
        CommissionSalary salary = salaryMapper.selectById(id);
        if (salary == null) return Result.error(404, "工资记录不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && salary.getTenantId() != null && !salary.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该工资记录");
        if (salary.getStatus() != null && salary.getStatus() != 1) return Result.error(400, "当前状态不能确认");
        salary.setStatus(2);
        salary.setConfirmTime(LocalDateTime.now());
        salaryMapper.updateById(salary);
        jdbc.update("UPDATE commission_record SET status=2,update_time=NOW() WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=?",
                salary.getStoreId(), salary.getEmployeeId(), salary.getSalaryMonth());
        return Result.success("工资已确认", salary);
    }

    @PostMapping("/salary/{id:\\d+}/pay")
    @RequirePermission("commission:salary:pay")
    public Result<CommissionSalary> paySalary(@PathVariable Long id) {
        CommissionSalary salary = salaryMapper.selectById(id);
        if (salary == null) return Result.error(404, "工资记录不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && salary.getTenantId() != null && !salary.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该工资记录");
        if (salary.getStatus() == null || salary.getStatus() != 2) return Result.error(400, "仅已确认工资可发放");
        salary.setStatus(3);
        salary.setPayTime(LocalDateTime.now());
        salaryMapper.updateById(salary);
        return Result.success("工资已标记发放", salary);
    }

    @GetMapping("/salary/stats")
    @RequirePermission("commission:salary:stat")
    public Result<Map<String, Object>> salaryStats(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth) {
        String month = normalizeMonth(salaryMonth);
        Long tenantId = TenantContextHolder.get().getTenantId();
        String tenantFilter = tenantId == null ? "" : " AND tenant_id=" + tenantId;
        String storeFilter = storeId == null ? "" : " AND store_id=" + storeId;
        Map<String, Object> result = Keys.camel(jdbc.queryForMap(
                "SELECT COUNT(*) record_count," +
                        "COALESCE(SUM(base_salary),0) base_salary_total," +
                        "COALESCE(SUM(performance_amount),0) performance_total," +
                        "COALESCE(SUM(commission_amount),0) commission_total," +
                        "COALESCE(SUM(adjustment_amount),0) adjustment_total," +
                        "COALESCE(SUM(payable_amount),0) payable_total," +
                        "COALESCE(SUM(CASE WHEN status=2 THEN payable_amount ELSE 0 END),0) confirmed_total," +
                        "COALESCE(SUM(CASE WHEN status=3 THEN payable_amount ELSE 0 END),0) paid_total," +
                        "COALESCE(SUM(CASE WHEN status IN (1,2) THEN payable_amount ELSE 0 END),0) pending_total " +
                        "FROM commission_salary WHERE deleted=0 AND salary_month=?" + tenantFilter + storeFilter, month));
        result.put("salaryMonth", month);
        return Result.success(result);
    }

    @GetMapping("/salary/export")
    @RequirePermission("commission:salary:export")
    public void salaryExport(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String month = normalizeMonth(salaryMonth);
        StringBuilder where = new StringBuilder(" WHERE s.deleted=0 AND s.salary_month=?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(month);
        if (tenantId != null) { where.append(" AND s.tenant_id=?"); args.add(tenantId); }
        if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }
        if (employeeId != null) { where.append(" AND s.employee_id=?"); args.add(employeeId); }
        if (status != null) { where.append(" AND s.status=?"); args.add(status); }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.salary_month,s.employee_name,e.avatar_url,s.base_salary," +
                        "s.performance_amount,s.commission_amount,s.adjustment_amount,s.payable_amount," +
                        "CASE s.status WHEN 1 THEN '草稿' WHEN 2 THEN '已确认' WHEN 3 THEN '已发放' ELSE '-' END status_name," +
                        "s.confirm_time,s.pay_time,s.remark " +
                        "FROM commission_salary s " +
                        "LEFT JOIN pos_employee e ON e.id=s.employee_id AND e.deleted=0" +
                        where + " ORDER BY s.salary_month DESC,s.employee_id ASC", args.toArray());

        String[] headers = {"月份", "员工", "头像", "基础工资", "业绩", "提成", "调整", "应发工资", "状态", "确认时间", "发放时间", "备注"};
        String[][] data = rows.stream().map(r -> new String[]{
                str(r.get("salaryMonth")), str(r.get("employeeName")), str(r.get("avatarUrl")),
                money(r.get("baseSalary")), money(r.get("performanceAmount")), money(r.get("commissionAmount")),
                money(r.get("adjustmentAmount")), money(r.get("payableAmount")), str(r.get("statusName")),
                str(r.get("confirmTime")), str(r.get("payTime")), str(r.get("remark"))
        }).toArray(String[][]::new);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=salary_" + month + ".xlsx");
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("工资报表");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            for (int i = 0; i < data.length; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
            }
            wb.write(response.getOutputStream());
        }
    }

    @PutMapping("/salary/batch/confirm")
    @RequirePermission("commission:salary:batch-confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchConfirmSalary(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) return Result.error(400, "请选择要确认的工资记录");
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        List<CommissionSalary> salaries = salaryMapper.selectBatchIds(ids);
        if (salaries.isEmpty()) return Result.error(404, "工资记录不存在");
        int success = 0;
        for (CommissionSalary salary : salaries) {
            if (salary.getStatus() != null && salary.getStatus() == 1) {
                salary.setStatus(2);
                salary.setConfirmTime(LocalDateTime.now());
                salaryMapper.updateById(salary);
                jdbc.update("UPDATE commission_record SET status=2,update_time=NOW() WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=?",
                        salary.getStoreId(), salary.getEmployeeId(), salary.getSalaryMonth());
                success++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("total", ids.size());
        return Result.success("批量确认完成，成功 " + success + " 条", result);
    }

    @PutMapping("/salary/batch/pay")
    @RequirePermission("commission:salary:batch-pay")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchPaySalary(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) return Result.error(400, "请选择要发放的工资记录");
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        List<CommissionSalary> salaries = salaryMapper.selectBatchIds(ids);
        if (salaries.isEmpty()) return Result.error(404, "工资记录不存在");
        int success = 0;
        for (CommissionSalary salary : salaries) {
            if (salary.getStatus() != null && salary.getStatus() == 2) {
                salary.setStatus(3);
                salary.setPayTime(LocalDateTime.now());
                salaryMapper.updateById(salary);
                success++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("total", ids.size());
        return Result.success("批量发放完成，成功 " + success + " 条", result);
    }

    // ==================== M09-C 结算管理 ====================

    @GetMapping("/settlement/stats")
    @RequirePermission("commission:settlement:stat")
    public Result<Map<String, Object>> settlementStats(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String tenantFilter = tenantId == null ? "" : " AND tenant_id=" + tenantId;
        String storeFilter = storeId == null ? "" : " AND store_id=" + storeId;
        String monthFilter = salaryMonth == null || salaryMonth.isBlank() ? "" : " AND salary_month='" + salaryMonth + "'";
        Map<String, Object> result = Keys.camel(jdbc.queryForMap(
                "SELECT COUNT(*) record_count," +
                        "COALESCE(SUM(total_payable),0) total_payable," +
                        "COALESCE(SUM(CASE WHEN status=1 THEN total_payable ELSE 0 END),0) pending_total," +
                        "COALESCE(SUM(CASE WHEN status=2 THEN total_payable ELSE 0 END),0) confirmed_total," +
                        "COALESCE(SUM(CASE WHEN status=3 THEN total_payable ELSE 0 END),0) paid_total " +
                        "FROM commission_settlement WHERE deleted=0" + tenantFilter + storeFilter + monthFilter));
        return Result.success(result);
    }

    @PostMapping("/settlement/generate")
    @RequirePermission("commission:settlement:add")
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlement> generateSettlement(@RequestBody Map<String, Object> body) {
        // T07-D5：storeId 改为必填；缺失直接失败，禁止默认 storeId=1
        if (body.get("storeId") == null) {
            return Result.error(BizCode.BAD_REQUEST, "storeId 必填");
        }
        Long storeId = ((Number) body.get("storeId")).longValue();
        String month = body.get("salaryMonth") == null ? null : body.get("salaryMonth").toString();
        try {
            CommissionSettlement settlement = settlementService.generate(storeId, month);
            return Result.success("结算单生成成功", settlement);
        } catch (IllegalStateException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @GetMapping("/settlement/page")
    @RequirePermission("commission:settlement:list")
    public Result<Page<CommissionSettlement>> settlementPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CommissionSettlement> query = new LambdaQueryWrapper<>();
        if (tenantId != null) query.eq(CommissionSettlement::getTenantId, tenantId);
        if (storeId != null) query.eq(CommissionSettlement::getStoreId, storeId);
        if (salaryMonth != null && !salaryMonth.isBlank()) query.eq(CommissionSettlement::getSalaryMonth, salaryMonth);
        if (status != null) query.eq(CommissionSettlement::getStatus, status);
        query.orderByDesc(CommissionSettlement::getSalaryMonth).orderByDesc(CommissionSettlement::getId);
        return Result.success(settlementMapper.selectPage(new Page<>(pageNum, Math.min(pageSize, 200)), query));
    }

    @GetMapping("/settlement/{id:\\d+}")
    @RequirePermission("commission:settlement:detail")
    public Result<Map<String, Object>> settlementDetail(@PathVariable Long id) {
        CommissionSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) return Result.error(404, "结算单不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && settlement.getTenantId() != null && !settlement.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该结算单");
        List<Map<String, Object>> records = settlementService.getRecords(id);
        Map<String, Object> result = new HashMap<>();
        result.put("settlement", settlement);
        result.put("records", records);
        return Result.success(result);
    }

    @PutMapping("/settlement/{id:\\d+}/confirm")
    @RequirePermission("commission:settlement:confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlement> confirmSettlement(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = extractOperatorIdOrFail(request);
        try {
            return Result.success("结算单已确认", settlementService.confirm(id, operatorId));
        } catch (IllegalArgumentException ex) {
            return Result.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PutMapping("/settlement/{id:\\d+}/pay")
    @RequirePermission("commission:settlement:pay")
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlement> paySettlement(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = extractOperatorIdOrFail(request);
        try {
            return Result.success("结算单已发放", settlementService.pay(id, operatorId));
        } catch (IllegalArgumentException ex) {
            return Result.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PutMapping("/settlement/batch/confirm")
    @RequirePermission("commission:settlement:batch-confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchConfirmSettlement(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) return Result.error(400, "请选择要确认的结算单");
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        List<CommissionSettlement> settlements = settlementMapper.selectBatchIds(ids);
        if (settlements.isEmpty()) return Result.error(404, "结算单不存在");
        int success = 0;
        for (CommissionSettlement settlement : settlements) {
            if (settlement.getStatus() != null && settlement.getStatus() == 1) {
                settlement.setStatus(2);
                settlement.setConfirmTime(LocalDateTime.now());
                settlementMapper.updateById(settlement);
                success++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("total", ids.size());
        return Result.success("批量确认完成，成功 " + success + " 条", result);
    }

    @PutMapping("/settlement/batch/pay")
    @RequirePermission("commission:settlement:batch-pay")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchPaySettlement(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) return Result.error(400, "请选择要发放的结算单");
        // P0-T04: 财务操作必须有真实操作人，缺失直接拒绝，禁止 fallback 到 1L
        Long operatorId = extractOperatorIdOrFail(request);
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        List<CommissionSettlement> settlements = settlementMapper.selectBatchIds(ids);
        if (settlements.isEmpty()) return Result.error(404, "结算单不存在");
        int success = 0;
        for (CommissionSettlement settlement : settlements) {
            if (settlement.getStatus() != null && settlement.getStatus() == 2) {
                settlementService.pay(settlement.getId(), operatorId);
                success++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("total", ids.size());
        return Result.success("批量发放完成，成功 " + success + " 条", result);
    }

    @GetMapping("/settlement/export")
    @RequirePermission("commission:settlement:export")
    public void settlementExport(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws Exception {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder(" WHERE s.deleted=0");
        List<Object> args = new java.util.ArrayList<>();
        if (tenantId != null) { where.append(" AND s.tenant_id=?"); args.add(tenantId); }
        if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }
        if (salaryMonth != null && !salaryMonth.isBlank()) { where.append(" AND s.salary_month=?"); args.add(salaryMonth); }
        if (status != null) { where.append(" AND s.status=?"); args.add(status); }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.settlement_no,s.salary_month,s.employee_count,s.total_commission," +
                        "s.total_adjustment,s.total_payable," +
                        "CASE s.status WHEN 1 THEN '待确认' WHEN 2 THEN '已确认' WHEN 3 THEN '已发放' ELSE '-' END status_name," +
                        "s.confirm_time,s.pay_time " +
                        "FROM commission_settlement s" + where + " ORDER BY s.salary_month DESC,s.id DESC", args.toArray());

        String[] headers = {"结算单号", "工资月份", "员工人数", "总提成", "调整金额", "应付总额", "状态", "确认时间", "发放时间"};
        String[][] data = rows.stream().map(r -> new String[]{
                str(r.get("settlementNo")), str(r.get("salaryMonth")), str(r.get("employeeCount")),
                money(r.get("totalCommission")), money(r.get("totalAdjustment")), money(r.get("totalPayable")),
                str(r.get("statusName")), str(r.get("confirmTime")), str(r.get("payTime"))
        }).toArray(String[][]::new);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=settlement_export.xlsx");
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("结算单");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            for (int i = 0; i < data.length; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
            }
            wb.write(response.getOutputStream());
        }
    }

    private Long extractOperatorId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            return 1L;
        }
    }

    /**
     * P0-T04 · 财务类操作专用：从 JWT 提取 operatorId，缺失则抛异常。
     * 与 {@link #extractOperatorId(HttpServletRequest)} 区别在于：失败不回退到 1L，
     * 而是抛 BusinessException(UNAUTHORIZED, ...)，防止 settlement confirm/pay 等财务动作
     * 在 token 缺失/解析失败时仍被错误归属给 user 1。
     */
    private Long extractOperatorIdOrFail(HttpServletRequest request) {
        try {
            String auth = request == null ? null : request.getHeader("Authorization");
            if (auth == null || auth.isBlank()) {
                throw new com.meiyetec.exception.BusinessException(
                        BizCode.UNAUTHORIZED, "未识别操作人");
            }
            String token = auth.replace("Bearer ", "").trim();
            Long uid = jwtUtil.extractUserId(token);
            if (uid == null) {
                throw new com.meiyetec.exception.BusinessException(
                        BizCode.UNAUTHORIZED, "未识别操作人");
            }
            return uid;
        } catch (com.meiyetec.exception.BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new com.meiyetec.exception.BusinessException(
                    BizCode.UNAUTHORIZED, "未识别操作人: " + e.getMessage());
        }
    }

    private String validateRule(CommissionRule rule) {
        if (rule.getRuleName() == null || rule.getRuleName().isBlank()) return "规则名称必填";
        if (rule.getStoreId() == null) return "门店必填";
        if (!List.of("SERVICE", "PRODUCT", "CARD", "RECHARGE").contains(rule.getBusinessType())) return "业务类型不正确";
        if (!List.of("RATE", "FIXED").contains(rule.getCalculationMethod())) return "计算方式不正确";
        if ("RATE".equals(rule.getCalculationMethod()) && (rule.getRate() == null || rule.getRate().compareTo(BigDecimal.ZERO) < 0 || rule.getRate().compareTo(BigDecimal.ONE) > 0)) return "提成比例应在0到1之间";
        if ("FIXED".equals(rule.getCalculationMethod()) && (rule.getFixedAmount() == null || rule.getFixedAmount().compareTo(BigDecimal.ZERO) < 0)) return "固定金额不能小于0";
        if (rule.getEffectiveDate() == null) return "生效日期必填";
        if (rule.getExpiryDate() != null && rule.getExpiryDate().isBefore(rule.getEffectiveDate())) return "失效日期不能早于生效日期";
        return null;
    }

    private void applyRuleDefaults(CommissionRule rule) {
        // T07-D1：不再 fallback 到默认租户 1L；缺失应抛错（Controller 已注入 context）
        if (rule.getTenantId() == null) {
            throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        }
        if (rule.getPriority() == null) rule.setPriority(0);
        if (rule.getStatus() == null) rule.setStatus(1);
        if ("RATE".equals(rule.getCalculationMethod())) rule.setFixedAmount(null);
        if ("FIXED".equals(rule.getCalculationMethod())) rule.setRate(null);
    }

    private String normalizeMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now().toString() : YearMonth.parse(month).toString();
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private String money(Object value) {
        if (value == null) return "0.00";
        try {
            return new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toString();
        } catch (Exception e) {
            return "0.00";
        }
    }
}



