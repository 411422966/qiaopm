package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.9：推送 + 前端模板 + 小程序版本 + 路由审计
 */
@Slf4j
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushTemplateMapper templateMapper;
    private final PushRecordMapper recordMapper;
    private final DeviceBindingMapper bindingMapper;

    // ====================================================================
    // 1. 推送模板
    // ====================================================================

    @PostMapping("/template")
    @Transactional(rollbackFor = Exception.class)
    public Result<PushTemplate> upsertTemplate(@RequestBody PushTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        t.setTenantId(tenantId);
        if (t.getChannel() == null) t.setChannel("NOTICE");
        if (t.getSound() == null) t.setSound("default");
        if (t.getVibration() == null) t.setVibration(1);
        if (t.getEnabled() == null) t.setEnabled(1);

        PushTemplate exist = templateMapper.selectOne(
                new LambdaQueryWrapper<PushTemplate>()
                        .eq(PushTemplate::getTenantId, tenantId)
                        .eq(PushTemplate::getTemplateCode, t.getTemplateCode()));
        if (exist != null) {
            exist.setTitle(t.getTitle());
            exist.setContent(t.getContent());
            exist.setPlatformCodes(t.getPlatformCodes());
            exist.setChannel(t.getChannel());
            exist.setDeepLink(t.getDeepLink());
            exist.setIconUrl(t.getIconUrl());
            exist.setSound(t.getSound());
            exist.setVibration(t.getVibration());
            exist.setEnabled(t.getEnabled());
            templateMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        templateMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/template/list")
    public Result<List<PushTemplate>> listTemplates(@RequestParam(required = false) String channel) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PushTemplate> qw = new LambdaQueryWrapper<>();
        qw.eq(PushTemplate::getTenantId, tenantId);
        if (channel != null) qw.eq(PushTemplate::getChannel, channel);
        qw.eq(PushTemplate::getEnabled, 1);
        qw.orderByAsc(PushTemplate::getTemplateCode);
        return Result.success(templateMapper.selectList(qw));
    }

    // ====================================================================
    // 2. 推送发送
    // ====================================================================

    @PostMapping("/send")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<PushRecord>> send(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String templateCode = (String) body.get("templateCode");
        PushTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<PushTemplate>()
                        .eq(PushTemplate::getTenantId, tenantId)
                        .eq(PushTemplate::getTemplateCode, templateCode));
        if (tpl == null) return Result.error(BizCode.NOT_FOUND, "模板不存在");

        @SuppressWarnings("unchecked")
        List<Number> userIdsRaw = (List<Number>) body.getOrDefault("userIds", List.of());
        List<Long> userIds = new ArrayList<>();
        for (Number n : userIdsRaw) userIds.add(n.longValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) body.getOrDefault("variables", new HashMap<>());

        List<PushRecord> sent = new ArrayList<>();
        for (Long userId : userIds) {
            // 查找该用户的活跃设备
            List<DeviceBinding> devices = bindingMapper.selectList(
                    new LambdaQueryWrapper<DeviceBinding>()
                            .eq(DeviceBinding::getUserId, userId)
                            .eq(DeviceBinding::getStatus, "ACTIVE"));
            for (DeviceBinding d : devices) {
                PushRecord r = new PushRecord();
                r.setTenantId(tenantId);
                r.setTemplateCode(templateCode);
                r.setUserId(userId);
                r.setDeviceId(d.getDeviceId());
                r.setPlatformCode(d.getPlatformCode());
                r.setChannel(tpl.getChannel());
                r.setTitle(renderTemplate(tpl.getTitle(), vars));
                r.setContent(renderTemplate(tpl.getContent(), vars));
                r.setStatus("SENT");
                r.setSentAt(LocalDateTime.now());
                recordMapper.insert(r);
                sent.add(r);
            }
        }
        return Result.success("已发送 " + sent.size() + " 条", sent);
    }

    /**
     * 渲染模板变量：{varName}
     */
    public static String renderTemplate(String template, Map<String, Object> vars) {
        if (template == null || vars == null || vars.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return result;
    }

    @PostMapping("/record/{id}/read")
    @Transactional(rollbackFor = Exception.class)
    public Result<PushRecord> markRead(@PathVariable Long id) {
        PushRecord r = recordMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "记录不存在");
        r.setStatus("READ");
        r.setReadAt(LocalDateTime.now());
        recordMapper.updateById(r);
        return Result.success("已读", r);
    }

    @GetMapping("/record/list")
    public Result<List<PushRecord>> recordList(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "20") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(recordMapper.selectList(
                new LambdaQueryWrapper<PushRecord>()
                        .eq(PushRecord::getTenantId, tenantId)
                        .eq(PushRecord::getUserId, userId)
                        .orderByDesc(PushRecord::getId)
                        .last("LIMIT " + limit)));
    }
}