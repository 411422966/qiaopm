package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmComplaintTicket;
import com.meiyetec.entity.CrmReview;
import com.meiyetec.mapper.CrmComplaintTicketMapper;
import com.meiyetec.mapper.CrmReviewMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/crm/care/review")
@RequiredArgsConstructor
@SysOpLog(module = "Review")
public class CrmReviewController {

    private final CrmReviewMapper reviewMapper;
    private final CrmComplaintTicketMapper complaintMapper;

    // ===================================================================
    // 0. 公共工具
    // ===================================================================
    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t != null ? t : 1L;
    }

    private String username() {
        String u = TenantContextHolder.get().getUsername();
        return u != null ? u : "system";
    }

    private List<CrmReview> listAll(Long tenantId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<CrmReview>()
                .eq(tenantId != null, CrmReview::getTenantId, tenantId)
                .eq(CrmReview::getDeleted, 0));
    }

    // ===================================================================
    // 1. stats（KPI + 7 日趋势 + 维度均值 + Top10 标签云）
    // ===================================================================
    @GetMapping("/stats")
    @RequirePermission("crm:care:review:list")
    public Result<Map<String, Object>> stats() {
        Long tenantId = tid();
        List<CrmReview> all = listAll(tenantId);

        long total = all.size();
        long today = all.stream().filter(r -> r.getCreateTime() != null
                && r.getCreateTime().toLocalDate().equals(LocalDate.now())).count();
        long low = all.stream().filter(r -> r.getOverallRating() != null && r.getOverallRating() <= 2).count();
        long replied = all.stream().filter(r -> r.getReplyContent() != null && !r.getReplyContent().isBlank()).count();
        String replyRate = total == 0 ? "0%" : String.format("%.0f%%", replied * 100.0 / total);

        // 7 日新增趋势（每天）
        Map<LocalDate, Long> dayMap = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) dayMap.put(LocalDate.now().minusDays(i), 0L);
        for (CrmReview r : all) {
            if (r.getCreateTime() == null) continue;
            LocalDate d = r.getCreateTime().toLocalDate();
            if (dayMap.containsKey(d)) dayMap.put(d, dayMap.get(d) + 1);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> e : dayMap.entrySet()) {
            Map<String, Object> o = new HashMap<>();
            o.put("date", e.getKey().toString());
            o.put("label", e.getKey().format(DateTimeFormatter.ofPattern("MM-dd")));
            o.put("count", e.getValue());
            trend.add(o);
        }

        // 维度均值（保留两位小数，0 表示无数据）
        Map<String, Double> dimAvg = new HashMap<>();
        dimAvg.put("service", safeAvg(all, CrmReview::getServiceRating));
        dimAvg.put("skill", safeAvg(all, CrmReview::getSkillRating));
        dimAvg.put("environment", safeAvg(all, CrmReview::getEnvironmentRating));
        dimAvg.put("price", safeAvg(all, CrmReview::getPriceRating));

        // Top10 标签云（按 , 切）
        Map<String, Long> tagFreq = new LinkedHashMap<>();
        for (CrmReview r : all) {
            if (r.getTags() == null) continue;
            for (String t : r.getTags().split(",")) {
                String k = t.trim();
                if (k.isEmpty()) continue;
                tagFreq.merge(k, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> topTags = tagFreq.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> o = new HashMap<>();
                    o.put("name", e.getKey());
                    o.put("count", e.getValue());
                    return o;
                }).collect(Collectors.toList());

        // 近 7 日好评 / 差评
        Map<String, Long> goodBad = new HashMap<>();
        goodBad.put("good", 0L);
        goodBad.put("bad", 0L);
        for (CrmReview r : all) {
            if (r.getCreateTime() == null) continue;
            if (r.getCreateTime().toLocalDate().isBefore(LocalDate.now().minusDays(7))) continue;
            if (r.getOverallRating() == null) continue;
            if (r.getOverallRating() >= 4) goodBad.merge("good", 1L, Long::sum);
            else if (r.getOverallRating() <= 2) goodBad.merge("bad", 1L, Long::sum);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("total", total);
        out.put("todayReviews", today);
        out.put("lowRating", low);
        out.put("replied", replied);
        out.put("replyRate", replyRate);
        out.put("trend", trend);
        out.put("dimAvg", dimAvg);
        out.put("topTags", topTags);
        out.put("week7GoodBad", goodBad);
        return Result.success(out);
    }

    private double safeAvg(List<CrmReview> all, java.util.function.Function<CrmReview, Integer> f) {
        int sum = 0, cnt = 0;
        for (CrmReview r : all) {
            Integer v = f.apply(r);
            if (v != null && v > 0) { sum += v; cnt++; }
        }
        return cnt == 0 ? 0d : Math.round(sum * 100.0 / cnt) / 100.0;
    }

    // ===================================================================
    // 2. 分页
    // ===================================================================
    @GetMapping("/page")
    @RequirePermission("crm:care:review:list")
    public Result<Page<CrmReview>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Integer overallRating,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tenantId = tid();
        LambdaQueryWrapper<CrmReview> q = new LambdaQueryWrapper<CrmReview>()
                .eq(tenantId != null, CrmReview::getTenantId, tenantId)
                .eq(CrmReview::getDeleted, 0)
                .orderByDesc(CrmReview::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CrmReview::getMemberName, keyword)
                    .or().like(CrmReview::getMemberPhone, keyword)
                    .or().like(CrmReview::getContent, keyword)
                    .or().like(CrmReview::getSourceNo, keyword)
                    .or().like(CrmReview::getTags, keyword));
        }
        if (sourceType != null && !sourceType.isBlank()) q.eq(CrmReview::getSourceType, sourceType);
        if (overallRating != null) q.eq(CrmReview::getOverallRating, overallRating);
        if (status != null) q.eq(CrmReview::getStatus, status);
        if (dateFrom != null && !dateFrom.isBlank()) q.ge(CrmReview::getCreateTime, LocalDate.parse(dateFrom).atStartOfDay());
        if (dateTo != null && !dateTo.isBlank()) q.le(CrmReview::getCreateTime, LocalDate.parse(dateTo).atTime(23, 59, 59));

        return Result.success(reviewMapper.selectPage(new Page<>(pageNum, pageSize), q));
    }

    // ===================================================================
    // 3. 详情
    // ===================================================================
    @GetMapping("/{id:\\d+}")
    @RequirePermission("crm:care:review:list")
    public Result<CrmReview> get(@PathVariable Long id) {
        return Result.success(reviewMapper.selectById(id));
    }

    // ===================================================================
    // 4. 新建 + 低评自动建投诉工单
    // ===================================================================
    @PostMapping
    @RequirePermission("crm:care:review:edit")
    public Result<Map<String, Object>> create(@RequestBody CrmReview review) {
        if (review.getTenantId() == null) review.setTenantId(tid());
        if (review.getStatus() == null) review.setStatus(2);
        if (review.getOperatorName() == null) review.setOperatorName(username());
        reviewMapper.insert(review);

        Long ticketId = null;
        int level = 0;
        Integer r = review.getOverallRating();
        if (r != null && r <= 2) {
            level = r == 1 ? 3 : 2;
            CrmComplaintTicket t = new CrmComplaintTicket();
            t.setTenantId(review.getTenantId());
            t.setStoreId(review.getStoreId());
            t.setTicketNo("C" + System.currentTimeMillis());
            t.setMemberId(review.getMemberId());
            t.setMemberName(review.getMemberName());
            t.setMemberPhone(review.getMemberPhone());
            t.setComplaintType("SERVICE");
            t.setLevel(level);
            t.setSubject("低评自动升级 · " + (review.getContent() != null && review.getContent().length() > 30
                    ? review.getContent().substring(0, 30) : String.valueOf(review.getContent())));
            t.setContent(review.getContent());
            t.setSourceType("REVIEW");
            t.setSourceId(review.getId());
            t.setSourceNo(review.getSourceNo());
            t.setStatus(1);
            t.setSlaMinutes(120);
            t.setSlaExpireAt(LocalDateTime.now().plusMinutes(120));
            t.setOperatorName(username());
            complaintMapper.insert(t);
            ticketId = t.getId();
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", review.getId());
        out.put("level", level);
        out.put("ticketId", ticketId);
        return Result.success(out);
    }

    @PostMapping("/{id:\\d+}/reply")
    @RequirePermission("crm:care:review:edit")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmReview r = reviewMapper.selectById(id);
        if (r == null) return Result.success(null);
        r.setReplyContent((String) body.get("content"));
        r.setReplyBy((String) body.get("replyBy"));
        r.setReplyAt(LocalDateTime.now());
        reviewMapper.updateById(r);
        return Result.success(null);
    }

    @PostMapping("/{id:\\d+}/hide")
    @RequirePermission("crm:care:review:edit")
    public Result<Void> hide(@PathVariable Long id) {
        CrmReview r = reviewMapper.selectById(id);
        if (r != null) { r.setStatus(3); reviewMapper.updateById(r); }
        return Result.success(null);
    }

    @PostMapping("/{id:\\d+}/show")
    @RequirePermission("crm:care:review:edit")
    public Result<Void> show(@PathVariable Long id) {
        CrmReview r = reviewMapper.selectById(id);
        if (r != null) { r.setStatus(2); reviewMapper.updateById(r); }
        return Result.success(null);
    }

    @PostMapping("/{id:\\d+}/appeal")
    @RequirePermission("crm:care:review:edit")
    public Result<Void> appeal(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CrmReview r = reviewMapper.selectById(id);
        if (r == null) return Result.success(null);
        r.setStatus(4);
        r.setOperatorName(username());
        reviewMapper.updateById(r);
        return Result.success(null);
    }

    @PostMapping("/{id:\\d+}/audit")
    @RequirePermission("crm:care:review:edit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body.get("status");
        CrmReview r = reviewMapper.selectById(id);
        if (r == null || v == null) return Result.success(null);
        r.setStatus(v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString()));
        reviewMapper.updateById(r);
        return Result.success(null);
    }

    // ===================================================================
    // 5. demo-seed：在 demo 租户下灌入 12 条评价（含 1-5 分 + 已隐藏）
    // ===================================================================
    @PostMapping("/demo-seed")
    @RequirePermission("crm:care:review:edit")
    public Result<Map<String, Object>> demoSeed() {
        Long tenantId = tid();
        // 清空旧的 demo 数据
        reviewMapper.delete(new LambdaQueryWrapper<CrmReview>()
                .eq(tenantId != null, CrmReview::getTenantId, tenantId)
                .apply("operator_name = 'demo-seed'"));
        String[] names = {"李婧", "王珊", "张婷", "刘芳", "陈琳", "赵敏", "孙洁", "周悦", "吴倩", "郑璇", "黄萍", "何菁"};
        String[] phones = {"13800138001", "13800138002", "13800138003", "13800138004", "13800138005", "13800138006",
                "13800138007", "13800138008", "13800138009", "13800138010", "13800138011", "13800138012"};
        String[] sourceTypes = {"ORDER", "STAFF", "SERVICE", "STORE"};
        String[] tagGroups = {"专业,细致", "热情,手法好", "环境,舒适", "性价比高", "耐心,推荐", "服务到位", "技师专业,环境好", "满意"};
        String[] contents = {
                "做完疗程效果非常满意，技师手法专业，会推荐给朋友！",
                "环境优雅，服务周到，下次还会再来。",
                "性价比很高，比外面便宜。",
                "技师不太耐心，下次可能不来了。",
                "服务还行，没有特别的感受。",
                "体验非常好，皮肤明显改善了。",
                "预约等待时间太长，扣一星。",
                "前台接待很热情，技师手法也好。",
                "项目流程清晰，不会强推销。",
                "客服回复很及时，问题处理得不错。",
                "整体一般，没有惊喜。",
                "非常糟糕的一次体验，完全不推荐。"
        };
        int[] ratings = {5, 5, 4, 1, 3, 5, 2, 4, 4, 4, 3, 1};
        String[] statuses = {"2", "2", "2", "3", "2", "2", "2", "2", "2", "1", "2", "1"};
        int n = names.length;
        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            CrmReview r = new CrmReview();
            r.setTenantId(tenantId);
            r.setBrandId(1L);
            r.setStoreId((long) (1 + rnd.nextInt(3)));
            r.setSourceType(sourceTypes[i % sourceTypes.length]);
            r.setSourceId((long) (1000 + i));
            r.setSourceNo("D" + String.format("%06d", 200000 + i));
            r.setMemberId((long) (100 + i));
            r.setMemberName(names[i]);
            r.setMemberPhone(phones[i]);
            r.setOverallRating(ratings[i]);
            r.setServiceRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setSkillRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setEnvironmentRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setPriceRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setTags(tagGroups[i % tagGroups.length]);
            r.setContent(contents[i]);
            r.setChannel(i % 2 == 0 ? "MINI_PROGRAM" : "INTERNAL");
            r.setStatus(Integer.parseInt(statuses[i]));
            r.setOperatorName("demo-seed");
            r.setCreateTime(LocalDateTime.now().minusHours(rnd.nextInt(168)));
            reviewMapper.insert(r);
            // 给低评 (≤2) 自动建投诉
            if (ratings[i] <= 2) {
                CrmComplaintTicket t = new CrmComplaintTicket();
                t.setTenantId(tenantId);
                t.setStoreId(r.getStoreId());
                t.setTicketNo("C" + System.currentTimeMillis() + i);
                t.setMemberId(r.getMemberId());
                t.setMemberName(r.getMemberName());
                t.setMemberPhone(r.getMemberPhone());
                t.setComplaintType("SERVICE");
                t.setLevel(ratings[i] == 1 ? 3 : 2);
                t.setSubject("低评自动升级 · demo");
                t.setContent(r.getContent());
                t.setSourceType("REVIEW");
                t.setSourceId(r.getId());
                t.setSourceNo(r.getSourceNo());
                t.setStatus(2);
                t.setSlaMinutes(120);
                t.setSlaExpireAt(LocalDateTime.now().plusMinutes(120));
                t.setOperatorName("demo-seed");
                complaintMapper.insert(t);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("inserted", n);
        return Result.success(out);
    }

    // ===================================================================
    // 6. 导出 CSV（前端 a[download] 触发，返回 text/csv）
    // ===================================================================
    @GetMapping("/export")
    @RequirePermission("crm:care:review:list")
    public Result<Map<String, Object>> export(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String sourceType,
                                              @RequestParam(required = false) Integer overallRating,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String dateFrom,
                                              @RequestParam(required = false) String dateTo) {
        Long tenantId = tid();
        LambdaQueryWrapper<CrmReview> q = new LambdaQueryWrapper<CrmReview>()
                .eq(tenantId != null, CrmReview::getTenantId, tenantId)
                .eq(CrmReview::getDeleted, 0)
                .orderByDesc(CrmReview::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CrmReview::getMemberName, keyword)
                    .or().like(CrmReview::getMemberPhone, keyword)
                    .or().like(CrmReview::getContent, keyword)
                    .or().like(CrmReview::getSourceNo, keyword)
                    .or().like(CrmReview::getTags, keyword));
        }
        if (sourceType != null && !sourceType.isBlank()) q.eq(CrmReview::getSourceType, sourceType);
        if (overallRating != null) q.eq(CrmReview::getOverallRating, overallRating);
        if (status != null) q.eq(CrmReview::getStatus, status);
        if (dateFrom != null && !dateFrom.isBlank()) q.ge(CrmReview::getCreateTime, LocalDate.parse(dateFrom).atStartOfDay());
        if (dateTo != null && !dateTo.isBlank()) q.le(CrmReview::getCreateTime, LocalDate.parse(dateTo).atTime(23, 59, 59));

        List<CrmReview> rows = reviewMapper.selectList(q);
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        csv.append("ID,来源单号,会员,手机,来源,综合评分,服务,技术,环境,价格,标签,内容,回复,状态,时间\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (CrmReview r : rows) {
            csv.append(c(r.getId())).append(',')
               .append(c(r.getSourceNo())).append(',')
               .append(c(r.getMemberName())).append(',')
               .append(c(r.getMemberPhone())).append(',')
               .append(c(r.getSourceType())).append(',')
               .append(c(r.getOverallRating())).append(',')
               .append(c(r.getServiceRating())).append(',')
               .append(c(r.getSkillRating())).append(',')
               .append(c(r.getEnvironmentRating())).append(',')
               .append(c(r.getPriceRating())).append(',')
               .append(c(r.getTags())).append(',')
               .append(c(r.getContent())).append(',')
               .append(c(r.getReplyContent())).append(',')
               .append(c(r.getStatus())).append(',')
               .append(r.getCreateTime() == null ? "" : r.getCreateTime().format(fmt))
               .append('\n');
        }
        Map<String, Object> out = new HashMap<>();
        out.put("filename", "review_" + LocalDate.now() + ".csv");
        out.put("content", csv.toString());
        out.put("count", rows.size());
        return Result.success(out);
    }

    private static String c(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}