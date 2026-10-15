package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.SysLoginLog;
import com.meiyetec.mapper.SysLoginLogMapper;
import com.meiyetec.service.SysLoginLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {

    @Override
    @Async
    public void asyncRecord(SysLoginLog log) {
        if (log.getCreatedAt() == null) log.setCreatedAt(LocalDateTime.now());
        try { save(log); } catch (Exception ignored) {}
    }

    @Override
    public IPage<SysLoginLog> page(int pageNum, int pageSize, String username, Integer status, String ip,
                                   String loginType, String startTime, String endTime) {
        QueryWrapper<SysLoginLog> w = new QueryWrapper<>();
        if (username != null && !username.isEmpty()) w.like("username", username);
        if (status != null) w.eq("status", status);
        if (ip != null && !ip.isEmpty()) w.like("ip", ip);
        if (loginType != null && !loginType.isEmpty()) w.eq("login_type", loginType);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        w.orderByDesc("created_at");
        return page(new Page<>(pageNum, pageSize), w);
    }

    @Override
    public int clearBeforeDays(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int n = baseMapper.delete(new QueryWrapper<SysLoginLog>().lt("created_at", cutoff));
        return Math.max(n, 0);
    }

    @Override
    public Map<String, Object> stats(Integer days) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            LocalDateTime from = LocalDateTime.now().minusDays(days == null ? 30 : days);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();

            // 直接用 3 次 count 查询，更稳
            long totalCount = count();
            long totalFail = count(new QueryWrapper<SysLoginLog>().eq("status", 0));
            long totalSuccess = count(new QueryWrapper<SysLoginLog>().eq("status", 1));
            long todayCount = count(new QueryWrapper<SysLoginLog>().ge("created_at", todayStart));
            long todayFail = count(new QueryWrapper<SysLoginLog>().ge("created_at", todayStart).eq("status", 0));
            long periodCount = count(new QueryWrapper<SysLoginLog>().ge("created_at", from));
            long periodFail = count(new QueryWrapper<SysLoginLog>().ge("created_at", from).eq("status", 0));
            r.put("totalCount", totalCount);
            r.put("todayCount", todayCount);
            r.put("totalFailCount", totalFail);
            r.put("todayFailCount", todayFail);
            r.put("totalSuccessCount", totalSuccess);
            r.put("totalSuccessRate", totalCount == 0 ? 100d : round((double)(totalCount - totalFail) * 100 / totalCount));
            r.put("todaySuccessRate", todayCount == 0 ? 100d : round((double)(todayCount - todayFail) * 100 / todayCount));
            r.put("periodSuccessRate", periodCount == 0 ? 100d : round((double)(periodCount - periodFail) * 100 / periodCount));

            // Top IP
            List<Map<String, Object>> topIps = baseMapper.selectMaps(new QueryWrapper<SysLoginLog>()
                    .select("ip AS k", "COUNT(*) AS c")
                    .isNotNull("ip").ne("ip", "")
                    .groupBy("ip").orderByDesc("c").last("LIMIT 1"));
            if (!topIps.isEmpty()) {
                r.put("topIp", String.valueOf(topIps.get(0).get("k")));
                r.put("topIpCount", toLong(topIps.get(0).get("c")));
            } else {
                r.put("topIp", "—"); r.put("topIpCount", 0);
            }
        } catch (Exception e) {
            r.put("totalCount", 0); r.put("todayCount", 0);
            r.put("totalSuccessRate", 100d); r.put("todaySuccessRate", 100d);
            r.put("topIp", "—"); r.put("topIpCount", 0);
        }
        return r;
    }

    @Override
    public List<Map<String, Object>> trend(Integer days) {
        int dn = days == null ? 7 : days;
        if (dn < 1) dn = 7;
        if (dn > 90) dn = 90;
        List<Map<String, Object>> r = new ArrayList<>();
        LocalDate today = LocalDate.now();
        try {
            List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<SysLoginLog>()
                    .select("DATE(created_at) AS d", "COUNT(*) AS c",
                            "SUM(IF(status = 1, 1, 0)) AS succ",
                            "SUM(IF(status = 0, 1, 0)) AS fail")
                    .ge("created_at", today.minusDays(dn - 1).atStartOfDay())
                    .groupBy("DATE(created_at)"));
            Map<String, Map<String, Object>> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.get("d"));
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("date", key);
                o.put("total", toLong(row.get("c")));
                o.put("success", toLong(row.get("succ")));
                o.put("fail", toLong(row.get("fail")));
                map.put(key, o);
            }
            for (int i = dn - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                String k = d.toString();
                Map<String, Object> o = map.get(k);
                if (o == null) {
                    o = new LinkedHashMap<>();
                    o.put("date", k); o.put("total", 0); o.put("success", 0); o.put("fail", 0);
                }
                r.add(o);
            }
        } catch (Exception ignored) {}
        return r;
    }

    @Override
    public Map<String, Object> anomaly(Integer days, Integer top) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (days == null || days < 1) days = 7;
        int ti = top == null ? 5 : top;
        if (ti < 1) ti = 5;
        if (ti > 50) ti = 50;
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        try {
            List<Map<String, Object>> ips = baseMapper.selectMaps(new QueryWrapper<SysLoginLog>()
                    .select("ip AS k", "COUNT(*) AS failCount",
                            "ROUND(SUM(IF(status = 0, 1, 0)) * 100.0 / COUNT(*), 1) AS failRate")
                    .ge("created_at", from)
                    .isNotNull("ip").ne("ip", "")
                    .groupBy("ip")
                    .orderByDesc("failCount").last("LIMIT " + ti));
            r.put("topFailIps", ips);

            List<Map<String, Object>> users = baseMapper.selectMaps(new QueryWrapper<SysLoginLog>()
                    .select("username AS k", "COUNT(*) AS failCount",
                            "ROUND(SUM(IF(status = 0, 1, 0)) * 100.0 / COUNT(*), 1) AS failRate")
                    .ge("created_at", from)
                    .isNotNull("username").ne("username", "")
                    .groupBy("username")
                    .orderByDesc("failCount").last("LIMIT " + ti));
            r.put("topFailUsers", users);
        } catch (Exception e) {
            r.put("topFailIps", new ArrayList<>());
            r.put("topFailUsers", new ArrayList<>());
        }
        return r;
    }

    @Override
    public IPage<SysLoginLog> search(int pageNum, int pageSize, String q, Integer status,
                                     String startTime, String endTime) {
        QueryWrapper<SysLoginLog> w = new QueryWrapper<>();
        if (q != null && !q.isEmpty()) {
            w.and(qb -> qb.like("username", q)
                    .or().like("ip", q)
                    .or().like("user_agent", q)
                    .or().like("message", q));
        }
        if (status != null) w.eq("status", status);
        if (startTime != null && !startTime.isEmpty()) w.ge("created_at", startTime);
        if (endTime != null && !endTime.isEmpty()) w.le("created_at", endTime);
        w.orderByDesc("created_at");
        return page(new Page<>(pageNum, pageSize), w);
    }

    private long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
    private double round(double v) { return Math.round(v * 10) / 10.0; }
}
