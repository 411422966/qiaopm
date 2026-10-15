package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.SysLog;
import com.meiyetec.service.SysLogService;
import com.meiyetec.util.Result;
import com.meiyetec.util.SysLogAnomalyVO;
import com.meiyetec.util.SysLogEnumsVO;
import com.meiyetec.util.SysLogStatsVO;
import com.meiyetec.util.TrendPointVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService service;

    @GetMapping("/page")
    public Result<Page<SysLog>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Boolean failFirst) {
        QueryWrapper<SysLog> w = new QueryWrapper<>();
        if (module != null && !module.isEmpty()) w.eq("module", module);
        if (action != null && !action.isEmpty()) w.eq("action", action);
        if (username != null && !username.isEmpty()) w.like("username", username);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like("description", keyword)
                    .or().like("request_url", keyword)
                    .or().like("ip", keyword));
        }
        if (status != null) w.eq("status", status);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        // 失败优先：status asc（MySQL:0 失败在前），再按时间倒序
        if (Boolean.TRUE.equals(failFirst)) {
            w.orderByAsc("status").orderByDesc("created_at");
        } else {
            w.orderByDesc("created_at");
        }
        return Result.success(service.page(new Page<>(pageNum, pageSize), w));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String fields,
            HttpServletResponse response) throws IOException {
        QueryWrapper<SysLog> w = new QueryWrapper<>();
        if (module != null && !module.isEmpty()) w.eq("module", module);
        if (action != null && !action.isEmpty()) w.eq("action", action);
        if (username != null && !username.isEmpty()) w.like("username", username);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like("description", keyword)
                    .or().like("request_url", keyword)
                    .or().like("ip", keyword));
        }
        if (status != null) w.eq("status", status);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        w.orderByDesc("created_at");
        List<SysLog> rows = service.list(w);

        // 字段筛选
        List<String> cols = new ArrayList<>();
        if (fields != null && !fields.isEmpty()) {
            for (String f : fields.split(",")) {
                String t = f.trim();
                if (!t.isEmpty()) cols.add(t);
            }
        }
        if (cols.isEmpty()) {
            cols = Arrays.asList("id", "username", "module", "action", "description",
                    "requestMethod", "requestUrl", "ip", "costMs", "status", "createdAt");
        }
        Map<String, String> headerMap = new LinkedHashMap<>();
        headerMap.put("id", "ID");
        headerMap.put("username", "用户");
        headerMap.put("module", "模块");
        headerMap.put("action", "操作");
        headerMap.put("description", "描述");
        headerMap.put("requestMethod", "方法");
        headerMap.put("requestUrl", "URL");
        headerMap.put("ip", "IP");
        headerMap.put("costMs", "耗时(ms)");
        headerMap.put("status", "状态");
        headerMap.put("createdAt", "时间");
        headerMap.put("errorMsg", "错误信息");
        headerMap.put("userAgent", "User-Agent");
        headerMap.put("requestParams", "请求参数");

        // 头顺序按 cols
        List<String> orderedCols = new ArrayList<>();
        for (String c : cols) if (headerMap.containsKey(c)) orderedCols.add(c);
        for (String c : cols) if (!orderedCols.contains(c)) orderedCols.add(c);

        String filename = URLEncoder.encode("sys_log_" +
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv",
                StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);

        try (OutputStream os = response.getOutputStream()) {
            os.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            // 头
            for (int i = 0; i < orderedCols.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(csv(headerMap.getOrDefault(orderedCols.get(i), orderedCols.get(i))));
            }
            sb.append('\n');
            // 取值
            for (SysLog r : rows) {
                for (int i = 0; i < orderedCols.size(); i++) {
                    if (i > 0) sb.append(',');
                    String c = orderedCols.get(i);
                    Object v = getCellValue(r, c);
                    sb.append(csv(v));
                }
                sb.append('\n');
            }
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    private static Object getCellValue(SysLog r, String col) {
        switch (col) {
            case "id": return r.getId();
            case "username": return r.getUsername();
            case "module": return r.getModule();
            case "action": return r.getAction();
            case "description": return r.getDescription();
            case "requestMethod": return r.getRequestMethod();
            case "requestUrl": return r.getRequestUrl();
            case "ip": return r.getIp();
            case "costMs": return r.getCostMs();
            case "status": return r.getStatus() != null && r.getStatus() == 1 ? "成功" : "失败";
            case "createdAt": return r.getCreatedAt() != null ? r.getCreatedAt().toString() : "";
            case "errorMsg": return r.getErrorMsg();
            case "userAgent": return r.getUserAgent();
            case "requestParams": return r.getRequestParams();
            default: return "";
        }
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 清理 N 天前的日志（不允许 days<1）。不再支持无差别清空。
     * 请求体：{ "days": 30 }
     */
    @DeleteMapping("/clear")
    public Result<Integer> clear(@RequestBody(required = false) ClearBody body) {
        int days = body == null ? 30 : (body.days == null ? 30 : body.days);
        if (days < 1) return Result.error("days 必须 >= 1");
        int n = service.clearBeforeDays(days);
        return Result.success("已清理 " + n + " 条 " + days + " 天前的日志", n);
    }

    public static class ClearBody {
        public Integer days;
    }

    @GetMapping("/stats")
    public Result<SysLogStatsVO> stats() {
        return Result.success(service.stats());
    }

    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(service.trend(days));
    }

    /**
     * 异常 TopN：失败最多的 IP / URL
     */
    @GetMapping("/anomaly")
    public Result<SysLogAnomalyVO> anomaly(
            @RequestParam(defaultValue = "7") Integer days,
            @RequestParam(defaultValue = "5") Integer top) {
        return Result.success(service.anomaly(days, top));
    }

    /**
     * 下拉枚举：模块 / 操作（默认 + DB DISTINCT 合并）
     */
    @GetMapping("/enums")
    public Result<SysLogEnumsVO> enums(@RequestParam(defaultValue = "30") Integer defaultClearDays) {
        return Result.success(service.enums(defaultClearDays));
    }

    /**
     * 上下文：同一用户时间窗口 ±N 分钟
     */
    @GetMapping("/context")
    public Result<List<SysLog>> context(
            @RequestParam Long id,
            @RequestParam(defaultValue = "10") Integer minutes,
            @RequestParam(defaultValue = "50") Integer limit) {
        SysLog center = service.getById(id);
        if (center == null) return Result.error("日志不存在");
        return Result.success(service.contextByUser(center.getUsername(), id, minutes, limit));
    }

    /**
     * 全文搜索：按关键字（描述 / URL / IP / 用户 / 错误信息）AND 过滤项
     */
    @GetMapping("/search")
    public Result<IPage<SysLog>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        QueryWrapper<SysLog> w = new QueryWrapper<>();
        if (q != null && !q.isEmpty()) {
            w.and(qb -> qb.like("description", q)
                    .or().like("request_url", q)
                    .or().like("ip", q)
                    .or().like("username", q)
                    .or().like("error_msg", q)
                    .or().like("request_params", q));
        }
        if (module != null && !module.isEmpty()) w.eq("module", module);
        if (action != null && !action.isEmpty()) w.eq("action", action);
        if (status != null) w.eq("status", status);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        w.orderByDesc("created_at");
        IPage<SysLog> page = service.page(new Page<>(pageNum, pageSize), w);
        return Result.success(page);
    }
}
