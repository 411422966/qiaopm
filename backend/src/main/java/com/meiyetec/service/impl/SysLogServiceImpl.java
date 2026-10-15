package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.SysLog;
import com.meiyetec.mapper.SysLogMapper;
import com.meiyetec.service.SysLogService;
import com.meiyetec.util.SysLogAnomalyVO;
import com.meiyetec.util.SysLogEnumsVO;
import com.meiyetec.util.SysLogStatsVO;
import com.meiyetec.util.TrendPointVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    private static final List<String> DEFAULT_MODULES = Arrays.asList(
            "会员", "收银", "商品", "登录", "报表", "库存", "套餐", "系统");
    private static final List<String> DEFAULT_ACTIONS = Arrays.asList(
            "create", "update", "delete", "login", "export", "import", "enable", "disable", "clear");

    @Override
    public SysLogStatsVO stats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        SysLogStatsVO vo = new SysLogStatsVO();

        Long todayCount = baseMapper.selectCount(new QueryWrapper<SysLog>().ge("created_at", todayStart));
        Long totalCount = baseMapper.selectCount(new QueryWrapper<>());

        Long todayFail = baseMapper.selectCount(new QueryWrapper<SysLog>().ge("created_at", todayStart).eq("status", 0));
        Long totalFail = baseMapper.selectCount(new QueryWrapper<SysLog>().eq("status", 0));

        vo.setTodayCount(todayCount == null ? 0 : todayCount);
        vo.setTotalCount(totalCount == null ? 0 : totalCount);
        long tf = todayFail == null ? 0 : todayFail;
        long totF = totalFail == null ? 0 : totalFail;
        long tc = todayCount == null ? 0 : todayCount;
        long totC = totalCount == null ? 0 : totalCount;
        vo.setTodayFailCount(tf);
        vo.setTotalFailCount(totF);
        vo.setTodaySuccessRate(tc == 0 ? 100.0 : Math.round(((double) (tc - tf) / tc) * 10000.0) / 100.0);
        vo.setTotalSuccessRate(totC == 0 ? 100.0 : Math.round(((double) (totC - totF) / totC) * 10000.0) / 100.0);

        Map<String, Object> topIpMap = new HashMap<>();
        topIpMap.put("ip", null);
        topIpMap.put("cnt", 0);
        try {
            List<Map<String, Object>> topIp = baseMapper.selectMaps(new QueryWrapper<SysLog>()
                    .select("ip, COUNT(*) AS cnt")
                    .groupBy("ip")
                    .orderByDesc("cnt")
                    .last("LIMIT 1"));
            if (!topIp.isEmpty()) {
                topIpMap = topIp.get(0);
            }
        } catch (Exception ignored) {}
        Object rawIpObj = topIpMap.get("ip");
        String topIpDisplay = rawIpObj == null ? "未知" : normalizeIp(String.valueOf(rawIpObj));
        vo.setTopIp(topIpDisplay);
        vo.setTopIpCount(((Number) (topIpMap.get("cnt") == null ? 0 : topIpMap.get("cnt"))).longValue());

        return vo;
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isEmpty()) return "—";
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        if (ip.startsWith("::ffff:")) return ip.substring(7);
        return ip;
    }

    private static String normalizeKey(String column, String key) {
        if ("ip".equalsIgnoreCase(column)) return normalizeIp(key);
        return key;
    }

    @Override
    public List<TrendPointVO> trend(int days) {
        if (days <= 0) days = 7;
        if (days > 30) days = 30;

        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(days - 1).atStartOfDay();

        List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<SysLog>()
                .select("DATE(created_at) AS d, status, COUNT(*) AS cnt")
                .ge("created_at", from)
                .groupBy("d, status"));

        Map<String, long[]> bucket = new HashMap<>();
        for (int i = 0; i < days; i++) {
            String k = today.minusDays(days - 1 - i).toString();
            bucket.put(k, new long[]{0, 0});
        }
        for (Map<String, Object> r : rows) {
            Object dObj = r.get("d");
            if (dObj == null) continue;
            String k;
            if (dObj instanceof java.sql.Date sd) {
                k = sd.toLocalDate().toString();
            } else if (dObj instanceof LocalDate ld) {
                k = ld.toString();
            } else {
                k = dObj.toString().substring(0, 10);
            }
            if (!bucket.containsKey(k)) continue;
            long[] a = bucket.get(k);
            Object sObj = r.get("status");
            Object cObj = r.get("cnt");
            int status = sObj == null ? 0 : Integer.parseInt(String.valueOf(sObj));
            long cnt = cObj == null ? 0L : ((Number) cObj).longValue();
            if (status == 1) a[1] += cnt; else a[0] += cnt;
        }

        List<TrendPointVO> list = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String k = today.minusDays(days - 1 - i).toString();
            long[] a = bucket.get(k);
            TrendPointVO p = new TrendPointVO();
            p.setDate(k);
            p.setTotal(a[0] + a[1]);
            p.setSuccess(a[1]);
            p.setFail(a[0]);
            list.add(p);
        }
        return list;
    }

    @Override
    public SysLogAnomalyVO anomaly(int days, int top) {
        if (days <= 0) days = 7;
        if (days > 90) days = 90;
        if (top <= 0) top = 5;
        if (top > 20) top = 20;

        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        SysLogAnomalyVO vo = new SysLogAnomalyVO();

        vo.setTopIps(aggregateTop(from, "ip", top));
        vo.setTopUrls(aggregateTop(from, "request_url", top));
        return vo;
    }

    private List<SysLogAnomalyVO.TopEntry> aggregateTop(LocalDateTime from, String column, int top) {
        List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<SysLog>()
                .select(column + " AS k, status, COUNT(*) AS cnt")
                .ge("created_at", from)
                .groupBy(column + ", status"));

        Map<String, long[]> agg = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object kObj = r.get("k");
            String raw;
            if (kObj == null) {
                raw = "__UNKNOWN__";
            } else {
                raw = String.valueOf(kObj);
                if (raw.isEmpty()) raw = "__UNKNOWN__";
            }
            String key = normalizeKey(column, raw);
            int status = r.get("status") == null ? 0 : Integer.parseInt(String.valueOf(r.get("status")));
            long cnt = r.get("cnt") == null ? 0L : ((Number) r.get("cnt")).longValue();
            long[] a = agg.computeIfAbsent(key, x -> new long[]{0, 0});
            a[0] += cnt;
            if (status == 0) a[1] += cnt;
        }

        List<SysLogAnomalyVO.TopEntry> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long total = e.getValue()[0];
            long fail = e.getValue()[1];
            if (fail == 0) continue;
            SysLogAnomalyVO.TopEntry t = new SysLogAnomalyVO.TopEntry();
            t.setKey(e.getKey());
            t.setFailCount(fail);
            t.setTotalCount(total);
            double rate = total == 0 ? 0.0 : Math.round(((double) fail / total) * 10000.0) / 100.0;
            t.setFailRate(rate);
            list.add(t);
        }
        list.sort((a, b) -> Long.compare(b.getFailCount(), a.getFailCount()));
        return list.size() > top ? list.subList(0, top) : list;
    }

    @Override
    public int clearBeforeDays(int days) {
        if (days < 1) days = 1;
        LocalDateTime cutoff = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        return baseMapper.delete(new QueryWrapper<SysLog>().lt("created_at", cutoff));
    }

    @Override
    public SysLogEnumsVO enums(int defaultClearDays) {
        SysLogEnumsVO vo = new SysLogEnumsVO();
        Set<String> modules = new LinkedHashSet<>(DEFAULT_MODULES);
        Set<String> actions = new LinkedHashSet<>(DEFAULT_ACTIONS);
        try {
            List<Map<String, Object>> modRows = baseMapper.selectMaps(new QueryWrapper<SysLog>()
                    .select("DISTINCT module AS m").isNotNull("module").ne("module", "")
                    .orderByAsc("m"));
            for (Map<String, Object> r : modRows) {
                Object o = r.get("m");
                if (o != null) modules.add(String.valueOf(o));
            }
            List<Map<String, Object>> actRows = baseMapper.selectMaps(new QueryWrapper<SysLog>()
                    .select("DISTINCT action AS a").isNotNull("action").ne("action", "")
                    .orderByAsc("a"));
            for (Map<String, Object> r : actRows) {
                Object o = r.get("a");
                if (o != null) actions.add(String.valueOf(o));
            }
        } catch (Exception ignored) {}
        vo.setModules(new ArrayList<>(modules));
        vo.setActions(new ArrayList<>(actions));
        vo.setDefaultClearDays(defaultClearDays);
        return vo;
    }

    @Override
    public List<SysLog> contextByUser(String username, Long centerId, int minutes, int limit) {
        if (username == null || username.isEmpty()) return new ArrayList<>();
        if (minutes < 1) minutes = 10;
        if (limit < 1) limit = 50;
        if (limit > 200) limit = 200;
        SysLog center = baseMapper.selectById(centerId);
        if (center == null) return new ArrayList<>();
        LocalDateTime from = center.getCreatedAt() == null ? LocalDateTime.now().minusMinutes(minutes) : center.getCreatedAt().minusMinutes(minutes);
        LocalDateTime to = center.getCreatedAt() == null ? LocalDateTime.now() : center.getCreatedAt().plusMinutes(minutes);
        QueryWrapper<SysLog> w = new QueryWrapper<>();
        w.eq("username", username);
        w.between("created_at", from, to);
        w.ne("id", centerId);
        w.orderByDesc("created_at");
        w.last("LIMIT " + limit);
        return baseMapper.selectList(w);
    }

    @Override
    public List<SysLog> sameSession(String username, String ip, Long centerId, int minutes, int limit) {
        if (username == null || username.isEmpty()) return new ArrayList<>();
        if (minutes < 1) minutes = 30;
        if (limit < 1) limit = 50;
        if (limit > 200) limit = 200;
        SysLog center = baseMapper.selectById(centerId);
        if (center == null) return new ArrayList<>();
        LocalDateTime from = center.getCreatedAt() == null ? LocalDateTime.now().minusMinutes(minutes) : center.getCreatedAt().minusMinutes(minutes);
        LocalDateTime to = center.getCreatedAt() == null ? LocalDateTime.now() : center.getCreatedAt().plusMinutes(minutes);
        QueryWrapper<SysLog> w = new QueryWrapper<>();
        w.eq("username", username);
        w.between("created_at", from, to);
        w.ne("id", centerId);
        if (ip != null && !ip.isEmpty()) {
            w.and(q -> q.eq("ip", ip).or().isNull("ip"));
        }
        w.orderByDesc("created_at");
        w.last("LIMIT " + limit);
        return baseMapper.selectList(w);
    }
}
