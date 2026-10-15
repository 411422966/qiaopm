package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.ComplicationEvent;
import com.meiyetec.entity.ComplicationSop;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.ComplicationEventMapper;
import com.meiyetec.mapper.ComplicationSopMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 医美并发症 SOP（FEATURE_COMPARISON_MATRIX.md 253-264 P1-12）
 */
@Slf4j
@RestController
@RequestMapping("/complication")
@RequiredArgsConstructor
public class ComplicationService {

    private final ComplicationSopMapper sopMapper;
    private final ComplicationEventMapper eventMapper;

    // ====================================================================
    // SOP 模板
    // ====================================================================

    @PostMapping("/sop")
    public Result<ComplicationSop> createSop(@RequestBody ComplicationSop s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getSopCode() == null || s.getSopName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (s.getSeverity() == null) s.setSeverity("MEDIUM");
        if (s.getStatus() == null) s.setStatus("ACTIVE");
        s.setTenantId(tenantId);
        s.setId(null);
        sopMapper.insert(s);
        return Result.success("已创建", s);
    }

    @GetMapping("/sop/list")
    public Result<List<ComplicationSop>> listSops() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(sopMapper.selectList(new LambdaQueryWrapper<ComplicationSop>()
                .eq(ComplicationSop::getTenantId, tenantId)
                .orderByAsc(ComplicationSop::getSeverity)));
    }

    @PostMapping("/sop/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefault() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.of("code", "RED_SWELL", "name", "红肿反应", "severity", "LOW", "category", "INJECTION",
                        "window", "24小时内", "steps", "冰敷 15 分钟 + 涂抹抗炎药膏 + 观察 24h", "reporter", "前台"),
                Map.of("code", "BRUISE", "name", "瘀青", "severity", "LOW", "category", "INJECTION",
                        "window", "3-7 天", "steps", "热敷 + 涂抹喜疗妥 + 避免剧烈运动 7 天", "reporter", "前台"),
                Map.of("code", "ALLERGY", "name", "过敏反应", "severity", "HIGH", "category", "GENERAL",
                        "window", "立即", "steps", "立即停用 + 口服抗组胺 + 就医 + 报院长", "reporter", "执业医师"),
                Map.of("code", "INFECTION", "name", "感染", "severity", "HIGH", "category", "GENERAL",
                        "window", "3-7 天", "steps", "清创 + 抗生素 + 24h 跟进 + 上报主管部门", "reporter", "执业医师"),
                Map.of("code", "VASCULAR", "name", "血管栓塞（玻尿酸）", "severity", "CRITICAL", "category", "FILLER",
                        "window", "立即", "steps", "立即拨打 120 + 玻尿酸酶注射 + 转入急诊", "reporter", "执业医师")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            ComplicationSop exist = sopMapper.selectOne(new LambdaQueryWrapper<ComplicationSop>()
                    .eq(ComplicationSop::getTenantId, tenantId)
                    .eq(ComplicationSop::getSopCode, d.get("code")));
            if (exist != null) continue;
            ComplicationSop s = new ComplicationSop();
            s.setTenantId(tenantId);
            s.setSopCode((String) d.get("code"));
            s.setSopName((String) d.get("name"));
            s.setSeverity((String) d.get("severity"));
            s.setCategory((String) d.get("category"));
            s.setTriggerTiming((String) d.get("window"));
            s.setStepsJson((String) d.get("steps"));
            s.setResponsibleRole((String) d.get("reporter"));
            s.setStatus("ACTIVE");
            sopMapper.insert(s);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }

    // ====================================================================
    // 事件
    // ====================================================================

    @PostMapping("/event")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplicationEvent> createEvent(@RequestBody ComplicationEvent e) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (e.getMemberId() == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (e.getSopCode() == null) return Result.error(BizCode.BAD_REQUEST, "sopCode 必填");
        if (e.getSeverity() == null) e.setSeverity("MEDIUM");
        e.setTenantId(tenantId);
        e.setId(null);
        e.setTriggerTime(LocalDateTime.now());
        e.setStatus("OPEN");
        eventMapper.insert(e);
        return Result.success("已上报", e);
    }

    @GetMapping("/event/list")
    public Result<List<ComplicationEvent>> listEvents(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String severity) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<ComplicationEvent> qw = new LambdaQueryWrapper<ComplicationEvent>()
                .eq(ComplicationEvent::getTenantId, tenantId)
                .orderByDesc(ComplicationEvent::getTriggerTime);
        if (status != null && !status.isEmpty()) qw.eq(ComplicationEvent::getStatus, status);
        if (severity != null && !severity.isEmpty()) qw.eq(ComplicationEvent::getSeverity, severity);
        return Result.success(eventMapper.selectList(qw));
    }

    @PostMapping("/event/{id}/status")
    public Result<Void> updateEventStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ComplicationEvent e = eventMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        String status = (String) body.get("status");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "status 必填");
        e.setStatus(status);
        if ("RESOLVED".equals(status)) {
            e.setResolvedTime(LocalDateTime.now());
            if (body.get("resolution") != null) e.setOutcome((String) body.get("resolution"));
        }
        eventMapper.updateById(e);
        return Result.success("已更新", null);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalOpen", eventMapper.selectCount(new LambdaQueryWrapper<ComplicationEvent>()
                    .eq(ComplicationEvent::getTenantId, tenantId)
                    .eq(ComplicationEvent::getStatus, "OPEN")));
            data.put("totalResolved", eventMapper.selectCount(new LambdaQueryWrapper<ComplicationEvent>()
                    .eq(ComplicationEvent::getTenantId, tenantId)
                    .eq(ComplicationEvent::getStatus, "RESOLVED")));
            data.put("bySeverity", eventMapper.selectList(new LambdaQueryWrapper<ComplicationEvent>()
                    .eq(ComplicationEvent::getTenantId, tenantId)));
        } catch (Exception ignore) {}
        return Result.success(data);
    }
}