package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.util.Result;
import com.meiyetec.entity.SysLoginLog;
import com.meiyetec.service.SysLoginLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/login-log")
public class SysLoginLogController {

    @Autowired
    private SysLoginLogService service;

    @GetMapping("/page")
    public Result<IPage<SysLoginLog>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(service.page(pageNum, pageSize, username, status, ip, loginType, startTime, endTime));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(service.stats(days));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(service.trend(days));
    }

    @GetMapping("/anomaly")
    public Result<Map<String, Object>> anomaly(@RequestParam(defaultValue = "7") Integer days,
                                               @RequestParam(defaultValue = "5") Integer top) {
        return Result.success(service.anomaly(days, top));
    }

    @GetMapping("/search")
    public Result<IPage<SysLoginLog>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(service.search(pageNum, pageSize, q, status, startTime, endTime));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(service.removeById(id));
    }

    @PostMapping("/clear")
    public Result<Integer> clear(@RequestParam(defaultValue = "30") Integer days) {
        if (days == null || days <= 0) return Result.success(0);
        int n = service.clearBeforeDays(days);
        return Result.success(n);
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) throws IOException {
        QueryWrapper<SysLoginLog> w = new QueryWrapper<>();
        if (username != null && !username.isEmpty()) w.like("username", username);
        if (status != null) w.eq("status", status);
        if (ip != null && !ip.isEmpty()) w.like("ip", ip);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        w.orderByDesc("created_at");
        List<SysLoginLog> rows = service.list(w);

        String filename = URLEncoder.encode("sys_login_log_" +
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv",
                StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);

        try (OutputStream os = response.getOutputStream()) {
            os.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("ID,用户ID,用户名,状态,消息,IP,登录方式,时间\n");
            for (SysLoginLog r : rows) {
                sb.append(csv(r.getId())).append(',')
                        .append(csv(r.getUserId())).append(',')
                        .append(csv(r.getUsername())).append(',')
                        .append(csv(r.getStatus() != null && r.getStatus() == 1 ? "成功" : "失败")).append(',')
                        .append(csv(r.getMessage())).append(',')
                        .append(csv(r.getIp())).append(',')
                        .append(csv(r.getLoginType())).append(',')
                        .append(csv(r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""))
                        .append('\n');
            }
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
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
}
