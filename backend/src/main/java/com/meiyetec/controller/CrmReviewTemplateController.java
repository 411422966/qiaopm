package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.CrmReviewTemplate;
import com.meiyetec.mapper.CrmReviewTemplateMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/crm/care/review/tpl")
@RequiredArgsConstructor
public class CrmReviewTemplateController {

    private final CrmReviewTemplateMapper tplMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t != null ? t : 1L;
    }

    private String username() {
        String u = TenantContextHolder.get().getUsername();
        return u != null ? u : "system";
    }

    // ===================================================================
    // 0. 工具：按筛选条件查全集（分页前用）
    // ===================================================================
    private List<CrmReviewTemplate> all(String keyword, Integer enabled) {
        LambdaQueryWrapper<CrmReviewTemplate> q = new LambdaQueryWrapper<CrmReviewTemplate>()
                .eq(tid() != null, CrmReviewTemplate::getTenantId, tid())
                .eq(CrmReviewTemplate::getDeleted, 0)
                .orderByDesc(CrmReviewTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CrmReviewTemplate::getTemplateCode, keyword)
                    .or().like(CrmReviewTemplate::getTemplateName, keyword));
        }
        if (enabled != null) q.eq(CrmReviewTemplate::getEnabled, enabled);
        return tplMapper.selectList(q);
    }

    // ===================================================================
    // 1. 分页列表
    // ===================================================================
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled) {
        LambdaQueryWrapper<CrmReviewTemplate> q = new LambdaQueryWrapper<CrmReviewTemplate>()
                .eq(tid() != null, CrmReviewTemplate::getTenantId, tid())
                .eq(CrmReviewTemplate::getDeleted, 0)
                .orderByDesc(CrmReviewTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CrmReviewTemplate::getTemplateCode, keyword)
                    .or().like(CrmReviewTemplate::getTemplateName, keyword));
        }
        if (enabled != null) q.eq(CrmReviewTemplate::getEnabled, enabled);
        Page<CrmReviewTemplate> p = tplMapper.selectPage(new Page<>(pageNum, pageSize), q);
        Map<String, Object> out = new HashMap<>();
        out.put("records", p.getRecords());
        out.put("total", p.getTotal());
        out.put("pageNum", p.getCurrent());
        out.put("pageSize", p.getSize());
        out.put("pages", p.getPages());
        return Result.success(out);
    }

    // ===================================================================
    // 2. 统计：总数 / 启用 / 平均延迟 / 触发事件分布 / 渠道分布
    // ===================================================================
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<CrmReviewTemplate> all = all(null, null);
        long total = all.size();
        long enabled = all.stream().filter(t -> t.getEnabled() != null && t.getEnabled() == 1).count();
        long disabled = total - enabled;
        double avgDelay = 0;
        if (!all.isEmpty()) {
            int sum = 0, cnt = 0;
            for (CrmReviewTemplate t : all) {
                if (t.getTouchDelayMinutes() != null) { sum += t.getTouchDelayMinutes(); cnt++; }
            }
            avgDelay = cnt == 0 ? 0 : Math.round(sum * 10.0 / cnt) / 10.0;
        }
        Map<String, Long> triggerDist = new LinkedHashMap<>();
        for (CrmReviewTemplate t : all) {
            String key = t.getTriggerEvent() != null ? t.getTriggerEvent() : "OTHER";
            triggerDist.merge(key, 1L, Long::sum);
        }
        Map<String, Long> channelDist = new LinkedHashMap<>();
        for (CrmReviewTemplate t : all) {
            String key = t.getTouchChannel() != null ? t.getTouchChannel() : "OTHER";
            channelDist.merge(key, 1L, Long::sum);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("total", total);
        out.put("enabled", enabled);
        out.put("disabled", disabled);
        out.put("avgDelay", avgDelay);
        out.put("triggerDist", triggerDist);
        out.put("channelDist", channelDist);
        return Result.success(out);
    }

    // ===================================================================
    // 3. 详情
    // ===================================================================
    @GetMapping("/{id:\\d+}")
    public Result<CrmReviewTemplate> get(@PathVariable Long id) {
        return Result.success(tplMapper.selectById(id));
    }

    // ===================================================================
    // 4. 保存（新增/更新）
    // ===================================================================
    @PostMapping
    public Result<Map<String, Object>> save(@RequestBody CrmReviewTemplate tpl) {
        if (tpl.getTenantId() == null) tpl.setTenantId(tid());
        if (tpl.getEnabled() == null) tpl.setEnabled(1);
        tpl.setOperatorName(tpl.getOperatorName() != null ? tpl.getOperatorName() : username());
        if (tpl.getId() == null) tplMapper.insert(tpl);
        else tplMapper.updateById(tpl);
        Map<String, Object> out = new HashMap<>();
        out.put("id", tpl.getId());
        return Result.success(out);
    }

    // ===================================================================
    // 5. 启停
    // ===================================================================
    @PostMapping("/{id:\\d+}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmReviewTemplate t = tplMapper.selectById(id);
        if (t == null) return Result.success(null);
        Object v = body.get("enabled");
        if (v != null) t.setEnabled(v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString()));
        tplMapper.updateById(t);
        return Result.success(null);
    }

    // ===================================================================
    // 6. 删除
    // ===================================================================
    @PostMapping("/{id:\\d+}/delete")
    public Result<Void> remove(@PathVariable Long id) {
        CrmReviewTemplate t = tplMapper.selectById(id);
        if (t != null) { t.setDeleted(1); tplMapper.updateById(t); }
        return Result.success(null);
    }

    // ===================================================================
    // 7. 克隆：基于 id 复制一份新模板，code 加 -COPY-<时间戳>
    // ===================================================================
    @PostMapping("/{id:\\d+}/clone")
    public Result<Map<String, Object>> clone(@PathVariable Long id) {
        CrmReviewTemplate src = tplMapper.selectById(id);
        if (src == null) return Result.success(new HashMap<>());
        CrmReviewTemplate c = new CrmReviewTemplate();
        c.setTenantId(src.getTenantId());
        c.setBrandId(src.getBrandId());
        c.setTemplateCode(src.getTemplateCode() + "-COPY-" + System.currentTimeMillis());
        c.setTemplateName(src.getTemplateName() + " (副本)");
        c.setTriggerEvent(src.getTriggerEvent());
        c.setDimensions(src.getDimensions());
        c.setTagsPool(src.getTagsPool());
        c.setIntroText(src.getIntroText());
        c.setShareImage(src.getShareImage());
        c.setTouchChannel(src.getTouchChannel());
        c.setTouchDelayMinutes(src.getTouchDelayMinutes());
        c.setEnabled(0); // 副本默认停用，避免重复触发
        c.setOperatorName(username());
        tplMapper.insert(c);
        Map<String, Object> out = new HashMap<>();
        out.put("id", c.getId());
        out.put("templateCode", c.getTemplateCode());
        return Result.success(out);
    }

    // ===================================================================
    // 8. 模拟推送：返回触达渠道 + 延迟 + 模拟触发时间
    // ===================================================================
    @PostMapping("/{id:\\d+}/simulate")
    public Result<Map<String, Object>> simulate(@PathVariable Long id) {
        CrmReviewTemplate t = tplMapper.selectById(id);
        if (t == null) return Result.success(new HashMap<>());
        Map<String, Object> out = new HashMap<>();
        out.put("templateId", t.getId());
        out.put("templateCode", t.getTemplateCode());
        out.put("templateName", t.getTemplateName());
        out.put("channel", t.getTouchChannel());
        out.put("delayMinutes", t.getTouchDelayMinutes());
        out.put("triggerEvent", t.getTriggerEvent());
        out.put("simulateAt", LocalDateTime.now());
        out.put("willSendAt", LocalDateTime.now().plusMinutes(t.getTouchDelayMinutes() == null ? 0 : t.getTouchDelayMinutes()));
        out.put("previewUrl", "https://example.com/mp/review/" + t.getId());
        out.put("note", "DEMO 模拟推送（dev 环境不会真正下发）");
        return Result.success(out);
    }

    // ===================================================================
    // 9. demo-seed：灌入 5 条不同触发事件模板
    // ===================================================================
    @PostMapping("/demo-seed")
    public Result<Map<String, Object>> demoSeed() {
        // 清旧
        tplMapper.delete(new LambdaQueryWrapper<CrmReviewTemplate>()
                .eq(CrmReviewTemplate::getTenantId, tid())
                .eq(CrmReviewTemplate::getOperatorName, "demo-seed"));
        // 用时间戳避免唯一键冲突
        long stamp = System.currentTimeMillis() % 100000;
        String[][] presets = {
            {"TPL-ORDER-001-" + stamp, "服务订单评价", "ORDER_COMPLETED", "服务,技术,环境,价格", "专业,细致,热情,手法好,环境好",
                "感谢您选择美业，您的反馈是我们前进的动力！", "MINI_PROGRAM", "60"},
            {"TPL-CARD-001-" + stamp, "套餐交付评价", "CARD_DELIVERED", "服务,效果,性价比", "效果好,值得推荐,会再来",
                "您购买的套餐已交付，期待您的使用反馈~", "MINI_PROGRAM", "30"},
            {"TPL-SVC-001-" + stamp, "单次服务评价", "SERVICE_COMPLETED", "服务,技师,环境", "手法好,态度好,耐心",
                "本次服务已完成，期待您给本次体验打分！", "MINI_PROGRAM", "15"},
            {"TPL-BIRTHDAY-001-" + stamp, "生日关怀评价", "BIRTHDAY", "关怀,惊喜,服务", "贴心,有仪式感,感动",
                "美业祝您生日快乐！您的感受对我们很重要", "SMS", "0"},
            {"TPL-EXPIRE-001-" + stamp, "到期关怀评价", "EXPIRE", "续卡意愿,服务", "想继续,会有优惠吗",
                "您的权益即将到期，希望听到您的反馈~", "SMS", "1440"}
        };
        int n = presets.length;
        for (int i = 0; i < n; i++) {
            CrmReviewTemplate t = new CrmReviewTemplate();
            t.setTenantId(tid());
            t.setBrandId(1L);
            t.setTemplateCode(presets[i][0]);
            t.setTemplateName(presets[i][1]);
            t.setTriggerEvent(presets[i][2]);
            t.setDimensions(presets[i][3]);
            t.setTagsPool(presets[i][4]);
            t.setIntroText(presets[i][5]);
            t.setTouchChannel(presets[i][6]);
            t.setTouchDelayMinutes(Integer.parseInt(presets[i][7]));
            t.setEnabled(i % 2 == 0 ? 1 : 0);
            t.setOperatorName("demo-seed");
            tplMapper.insert(t);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("inserted", n);
        return Result.success(out);
    }
}