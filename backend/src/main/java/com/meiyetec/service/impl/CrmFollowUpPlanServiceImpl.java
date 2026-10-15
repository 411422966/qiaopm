package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.service.CrmFollowUpPlanService;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CrmFollowUpPlanServiceImpl extends ServiceImpl<CrmFollowUpPlanMapper, CrmFollowUpPlan>
        implements CrmFollowUpPlanService {

    private final CrmFollowUpPlanMapper planMapper;
    private final CrmFollowUpTriggerLogMapper triggerLogMapper;
    private final CrmFollowUpTaskMapper taskMapper;
    private final BookingMapper bookingMapper;
    private final PackageOrderMapper packageOrderMapper;
    private final MemberMapper memberMapper;
    private final IdGenerator idGenerator;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    @Override
    public Map<String, Object> stats() {
        long tenantId = tid();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", planMapper.countTotal(tenantId));
        stats.put("enabled", planMapper.countEnabled(tenantId));
        stats.put("todayGenerated", planMapper.sumTodayGenerated(tenantId));
        stats.put("monthGenerated", planMapper.sumMonthGenerated(tenantId));
        return stats;
    }

    /**
     * 最近 7 天趋势（含 0 填充），由 controller 包装后返回。
     */
    public List<Map<String, Object>> weekTrend() {
        long tenantId = tid();
        List<Map<String, Object>> raw = planMapper.weekTrend(tenantId);
        Map<String, Long> byDate = new HashMap<>();
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                Object d = row.get("date");
                Object c = row.get("count");
                if (d != null) {
                    byDate.put(d.toString(),
                            c == null ? 0L : ((Number) c).longValue());
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.put("label", d.format(fmt));
            row.put("count", byDate.getOrDefault(key, 0L));
            out.add(row);
        }
        return out;
    }

    @Override
    public Page<CrmFollowUpPlan> pagePlan(long pageNum, long pageSize) {
        Long tenantId = tid();
        Page<CrmFollowUpPlan> page = new Page<>(pageNum, pageSize);
        QueryWrapper<CrmFollowUpPlan> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("deleted", 0).orderByDesc("id");
        return planMapper.selectPage(page, qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> triggerPlan(Long planId) {
        CrmFollowUpPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("generatedCount", 0);
            empty.put("samples", new ArrayList<>());
            return empty;
        }

        int offset = plan.getTriggerOffsetDays() == null ? 0 : plan.getTriggerOffsetDays();
        String planType = plan.getPlanType() == null ? "CUSTOM" : plan.getPlanType();
        Integer slaMinutes = plan.getSlaMinutes() == null ? 1440 : plan.getSlaMinutes();
        Integer priority = plan.getPriority() == null ? 1 : plan.getPriority();
        String subjectT = plan.getSubjectTemplate();
        String contentT = plan.getContentTemplate();
        String scriptT = plan.getScriptTemplate();

        List<CrmFollowUpTask> created = new ArrayList<>();
        try {
            switch (planType) {
                case "POST_TREATMENT" -> created.addAll(scanPostTreatment(plan, offset, slaMinutes, priority, subjectT, contentT, scriptT));
                case "BIRTHDAY" -> created.addAll(scanBirthday(plan, offset, slaMinutes, priority, subjectT, contentT, scriptT));
                case "EXPIRE" -> created.addAll(scanExpire(plan, offset, slaMinutes, priority, subjectT, contentT, scriptT));
                default -> { }
            }
        } catch (Exception ignore) {
        }

        CrmFollowUpTriggerLog log = new CrmFollowUpTriggerLog();
        log.setTenantId(plan.getTenantId() == null ? 1L : plan.getTenantId());
        log.setPlanId(plan.getId());
        log.setPlanCode(plan.getPlanCode());
        log.setGeneratedCount(created.size());
        log.setTriggerSource("MANUAL");
        log.setTriggerTime(LocalDateTime.now());
        triggerLogMapper.insert(log);

        CrmFollowUpPlan update = new CrmFollowUpPlan();
        update.setId(plan.getId());
        update.setLastRunAt(LocalDateTime.now());
        update.setLastRunCount(created.size());
        planMapper.updateById(update);

        List<Map<String, Object>> samples = new ArrayList<>();
        for (CrmFollowUpTask t : created) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", t.getId());
            s.put("taskNo", t.getTaskNo());
            s.put("subject", t.getSubject());
            samples.add(s);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedCount", created.size());
        result.put("samples", samples);
        return result;
    }

    private List<CrmFollowUpTask> scanPostTreatment(CrmFollowUpPlan plan, int offset, Integer slaMinutes,
                                                     Integer priority, String subjectT, String contentT, String scriptT) {
        List<CrmFollowUpTask> out = new ArrayList<>();
        List<Booking> bookings = bookingMapper.selectList(
                new QueryWrapper<Booking>()
                        .eq("status", 4)
                        .ge("complete_time", LocalDateTime.now().minusDays(90))
                        .orderByDesc("id")
                        .last("LIMIT 50"));
        if (bookings == null) return out;
        for (Booking b : bookings) {
            if (b.getId() == null || b.getCompleteTime() == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(plan.getTenantId() == null ? tid() : plan.getTenantId(), plan.getId(), b.getId()) > 0) continue;
            CrmFollowUpTask t = buildTask(plan, b.getId(), b.getBookingNo(),
                    "ORDER", b.getMemberId(), b.getMemberName(), b.getMemberPhone(),
                    b.getCompleteTime().plusDays(offset), slaMinutes, priority,
                    subjectT, contentT, scriptT,
                    "POST_TREATMENT");
            taskMapper.insert(t);
            out.add(t);
        }
        return out;
    }

    private List<CrmFollowUpTask> scanBirthday(CrmFollowUpPlan plan, int offset, Integer slaMinutes,
                                               Integer priority, String subjectT, String contentT, String scriptT) {
        List<CrmFollowUpTask> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = memberMapper.selectMaps(
                new QueryWrapper<Member>()
                        .select("id", "name", "phone", "birthday")
                        .isNotNull("birthday")
                        .eq("deleted", 0)
                        .last("LIMIT 200"));
        if (rows == null) return out;
        for (Map<String, Object> row : rows) {
            Object bdObj = row.get("birthday");
            if (bdObj == null) continue;
            LocalDate birthday;
            try {
                if (bdObj instanceof LocalDate) birthday = (LocalDate) bdObj;
                else birthday = LocalDate.parse(bdObj.toString().substring(0, 10));
            } catch (Exception e) { continue; }
            LocalDate thisYearBday = birthday.withYear(today.getYear());
            if (thisYearBday.isBefore(today)) thisYearBday = thisYearBday.plusYears(1);
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, thisYearBday);
            if (daysUntil < 0 || daysUntil > 3) continue;

            Long memberId = row.get("id") == null ? null : ((Number) row.get("id")).longValue();
            if (memberId == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(plan.getTenantId() == null ? tid() : plan.getTenantId(), plan.getId(), memberId) > 0) continue;

            LocalDateTime plannedAt = thisYearBday.atStartOfDay().plusDays(offset);
            CrmFollowUpTask t = buildTask(plan, memberId, "MB" + memberId,
                    "MEMBER", memberId, (String) row.get("name"), (String) row.get("phone"),
                    plannedAt, slaMinutes, priority,
                    subjectT, contentT, scriptT,
                    "BIRTHDAY");
            taskMapper.insert(t);
            out.add(t);
        }
        return out;
    }

    private List<CrmFollowUpTask> scanExpire(CrmFollowUpPlan plan, int offset, Integer slaMinutes,
                                             Integer priority, String subjectT, String contentT, String scriptT) {
        List<CrmFollowUpTask> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(30).atStartOfDay();
        LocalDateTime end = today.plusDays(60).atStartOfDay();
        List<PackageOrder> orders = packageOrderMapper.selectList(
                new QueryWrapper<PackageOrder>()
                        .between("expire_time", start, end)
                        .last("LIMIT 50"));
        if (orders == null) return out;
        for (PackageOrder o : orders) {
            if (o.getId() == null || o.getExpireTime() == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(plan.getTenantId() == null ? tid() : plan.getTenantId(), plan.getId(), o.getId()) > 0) continue;
            LocalDateTime plannedAt = o.getExpireTime().plusDays(offset);
            CrmFollowUpTask t = buildTask(plan, o.getId(), o.getOrderNo(),
                    "CARD", o.getMemberId(), null, null,
                    plannedAt, slaMinutes, priority,
                    subjectT, contentT, scriptT,
                    "EXPIRE");
            taskMapper.insert(t);
            out.add(t);
        }
        return out;
    }

    private CrmFollowUpTask buildTask(CrmFollowUpPlan plan, Long sourceId, String sourceNo,
                                       String sourceType, Long memberId, String memberName, String memberPhone,
                                       LocalDateTime plannedAt, Integer slaMinutes, Integer priority,
                                       String subjectT, String contentT, String scriptT,
                                       String taskType) {
        CrmFollowUpTask t = new CrmFollowUpTask();
        t.setTenantId(plan.getTenantId() == null ? 1L : plan.getTenantId());
        t.setBrandId(plan.getBrandId());
        Long firstStoreId = null;
        String storeIds = plan.getStoreIds();
        if (storeIds != null && !storeIds.isEmpty()) {
            try { firstStoreId = Long.parseLong(storeIds.split(",")[0].trim()); } catch (NumberFormatException ignored) {}
        }
        t.setStoreId(firstStoreId);
        t.setTaskNo("FU" + idGenerator.nextIdStr());
        t.setTaskType(taskType);
        t.setPlanId(plan.getId());
        t.setMemberId(memberId);
        t.setMemberName(memberName);
        t.setMemberPhone(memberPhone);
        t.setSourceType(sourceType);
        t.setSourceId(sourceId);
        t.setSourceNo(sourceNo);
        t.setPlannedAt(plannedAt);
        t.setSlaMinutes(slaMinutes);
        if (plannedAt != null && slaMinutes != null) {
            t.setSlaExpireAt(plannedAt.plusMinutes(slaMinutes));
        }
        t.setPriority(priority);
        t.setSubject(render(subjectT, plan, memberName));
        t.setContent(render(contentT, plan, memberName));
        t.setSuggestedScript(render(scriptT, plan, memberName));
        t.setOwnerId(plan.getDefaultOwnerId());
        t.setOwnerName(plan.getDefaultOwnerName());
        t.setStatus(1);
        return t;
    }

    private String render(String template, CrmFollowUpPlan plan, String memberName) {
        if (template == null || template.isEmpty()) return template;
        String s = template;
        if (memberName != null) s = s.replace("{memberName}", memberName);
        if (plan.getPlanName() != null) s = s.replace("{planName}", plan.getPlanName());
        return s;
    }

    @Override
    public Map<String, Object> previewPlan(Long planId) {
        CrmFollowUpPlan plan = planMapper.selectById(planId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (plan == null) {
            result.put("planName", "");
            result.put("planType", "");
            result.put("offsetDays", 0);
            result.put("note", "规则不存在");
            result.put("sample", new ArrayList<>());
            return result;
        }
        int offset = plan.getTriggerOffsetDays() == null ? 0 : plan.getTriggerOffsetDays();
        String planType = plan.getPlanType() == null ? "CUSTOM" : plan.getPlanType();
        Long firstStoreId = null;
        String storeIds = plan.getStoreIds();
        if (storeIds != null && !storeIds.isEmpty()) {
            try { firstStoreId = Long.parseLong(storeIds.split(",")[0].trim()); } catch (NumberFormatException ignored) {}
        }

        result.put("planName", plan.getPlanName());
        result.put("planType", planType);
        result.put("offsetDays", offset);
        List<Map<String, Object>> sample = new ArrayList<>();
        try {
            switch (planType) {
                case "POST_TREATMENT" -> sample.addAll(previewPostTreatment(plan, firstStoreId));
                case "BIRTHDAY" -> sample.addAll(previewBirthday(plan, firstStoreId));
                case "EXPIRE" -> sample.addAll(previewExpire(plan, firstStoreId));
                default -> { }
            }
        } catch (Exception ignore) { }
        result.put("sample", sample);
        result.put("totalCount", sample.size());
        result.put("sampleLimit", 50);
        result.put("truncated", false);
        result.put("note", "预览不写入任务，样本来自现有源数据扫描");
        return result;
    }

    private List<Map<String, Object>> previewPostTreatment(CrmFollowUpPlan plan, Long firstStoreId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Booking> bookings = bookingMapper.selectList(
                new QueryWrapper<Booking>()
                        .eq("status", 4)
                        .ge("complete_time", LocalDateTime.now().minusDays(90))
                        .orderByDesc("id")
                        .last("LIMIT 50"));
        if (bookings == null) return out;
        for (Booking b : bookings) {
            if (b.getId() == null || b.getCompleteTime() == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(
                    plan.getTenantId() == null ? tid() : plan.getTenantId(),
                    plan.getId(), b.getId()) > 0) continue;
            out.add(buildPreviewRow(b.getMemberName(), b.getMemberPhone(),
                    firstStoreId, b.getBookingNo()));
        }
        return out;
    }

    private List<Map<String, Object>> previewBirthday(CrmFollowUpPlan plan, Long firstStoreId) {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = memberMapper.selectMaps(
                new QueryWrapper<Member>()
                        .select("id", "name", "phone", "birthday")
                        .isNotNull("birthday")
                        .eq("deleted", 0)
                        .last("LIMIT 200"));
        if (rows == null) return out;
        for (Map<String, Object> row : rows) {
            Object bdObj = row.get("birthday");
            if (bdObj == null) continue;
            LocalDate birthday;
            try {
                if (bdObj instanceof LocalDate) birthday = (LocalDate) bdObj;
                else birthday = LocalDate.parse(bdObj.toString().substring(0, 10));
            } catch (Exception e) { continue; }
            LocalDate thisYearBday = birthday.withYear(today.getYear());
            if (thisYearBday.isBefore(today)) thisYearBday = thisYearBday.plusYears(1);
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, thisYearBday);
            if (daysUntil < 0 || daysUntil > 3) continue;
            Long memberId = row.get("id") == null ? null : ((Number) row.get("id")).longValue();
            if (memberId == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(
                    plan.getTenantId() == null ? tid() : plan.getTenantId(),
                    plan.getId(), memberId) > 0) continue;
            out.add(buildPreviewRow((String) row.get("name"), (String) row.get("phone"),
                    firstStoreId, "MB" + memberId));
        }
        return out;
    }

    private List<Map<String, Object>> previewExpire(CrmFollowUpPlan plan, Long firstStoreId) {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(30).atStartOfDay();
        LocalDateTime end = today.plusDays(60).atStartOfDay();
        List<PackageOrder> orders = packageOrderMapper.selectList(
                new QueryWrapper<PackageOrder>()
                        .between("expire_time", start, end)
                        .last("LIMIT 50"));
        if (orders == null) return out;
        for (PackageOrder o : orders) {
            if (o.getId() == null || o.getExpireTime() == null) continue;
            if (planMapper.countByPlanAndSourceInTenant(
                    plan.getTenantId() == null ? tid() : plan.getTenantId(),
                    plan.getId(), o.getId()) > 0) continue;
            out.add(buildPreviewRow(null, null, firstStoreId, o.getOrderNo()));
        }
        return out;
    }

    private Map<String, Object> buildPreviewRow(String name, String phone, Long storeId, String sourceNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("phone", phone);
        row.put("store_id", storeId);
        row.put("source_no", sourceNo);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public int seedDemo() {
        int inserted = 0;
        inserted += insertIfAbsent("PLN001", "疗程后 3 天回访", "POST_TREATMENT", 3, "疗程回访：术后恢复情况", 1);
        inserted += insertIfAbsent("PLN002", "生日关怀", "BIRTHDAY", 0, "生日快乐 · 专属优惠请查收", 1);
        inserted += insertIfAbsent("PLN003", "套餐到期提醒", "EXPIRE", -7, "您的套餐即将到期", 1);
        return inserted;
    }

    private int insertIfAbsent(String code, String name, String type, int offsetDays, String subject, int enabled) {
        Long exists = planMapper.selectCount(
                new QueryWrapper<CrmFollowUpPlan>().eq("plan_code", code).eq("tenant_id", 1L)
        );
        if (exists != null && exists > 0) return 0;
        CrmFollowUpPlan p = new CrmFollowUpPlan();
        p.setTenantId(1L);
        p.setBrandId(1L);
        p.setStoreIds("1");
        p.setPlanCode(code);
        p.setPlanName(name);
        p.setPlanType(type);
        p.setTriggerOffsetDays(offsetDays);
        p.setSubjectTemplate(subject);
        p.setContentTemplate("感谢您选择本店，期待您再次光临。");
        p.setScriptTemplate("您好，请问您最近一次疗程后感觉如何？");
        p.setPriority(1);
        p.setSlaMinutes(1440);
        p.setEnabled(enabled);
        p.setCreateBy("demo-seed");
        p.setUpdateBy("demo-seed");
        planMapper.insert(p);
        return 1;
    }
}
