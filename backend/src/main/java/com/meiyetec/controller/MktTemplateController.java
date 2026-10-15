package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketingAutomationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销渠道模板 controller（前端 /mkt/template/* 实际对应的端点）
 *  - template.vue 实际使用的后端
 *  - 对应 mkt_channel_template 表
 *
 * 路由清单（与前端 template.vue 完全对齐）：
 *  GET  /mkt/template/list
 *  POST /mkt/template
 *  PUT  /mkt/template/{id}/audit
 */
@RestController
@RequestMapping("/mkt/template")
@RequiredArgsConstructor
@SysOpLog(module = "营销模板")
public class MktTemplateController {

    private final MarketingAutomationService marketingAutomationService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String scene) {
        return marketingAutomationService.templateList(channelType, scene);
    }

    @PostMapping
    public Result<MktChannelTemplate> create(@RequestBody MktChannelTemplate t) {
        return marketingAutomationService.createTemplate(t);
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingAutomationService.auditTemplate(id, body);
    }
}
