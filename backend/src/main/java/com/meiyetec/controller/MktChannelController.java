package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketingAutomationService;
import com.meiyetec.service.channel.ChannelDispatcher;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销渠道配置 controller（前端 /mkt/channel/* 实际对应的端点）
 *  - channel.vue 实际使用的后端
 *  - 对应 mkt_channel_config 表（含 tenant_id）
 *
 * 路由清单（与前端 channel.vue 完全对齐）：
 *  GET    /mkt/channel/list
 *  POST   /mkt/channel
 *  PUT    /mkt/channel/{id}
 *  DELETE /mkt/channel/{id}
 *  POST   /mkt/channel/health-check
 */
@RestController
@RequestMapping("/mkt/channel")
@RequiredArgsConstructor
@SysOpLog(module = "营销渠道")
public class MktChannelController {

    private final MarketingAutomationService marketingAutomationService;
    private final ChannelDispatcher channelDispatcher;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/list")
    @RequirePermission("mkt:channel:list")
    public Result<List<Map<String, Object>>> list() {
        return marketingAutomationService.channelList();
    }

    @PostMapping
    @RequirePermission("mkt:channel:edit")
    public Result<MktChannelConfig> create(@RequestBody MktChannelConfig c) {
        return marketingAutomationService.createChannel(c);
    }

    @PutMapping("/{id}")
    @RequirePermission("mkt:channel:edit")
    public Result<MktChannelConfig> update(@PathVariable Long id, @RequestBody MktChannelConfig c) {
        return marketingAutomationService.updateChannel(id, c);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("mkt:channel:edit")
    public Result<Void> delete(@PathVariable Long id) {
        return marketingAutomationService.deleteChannel(id);
    }

    @PostMapping("/health-check")
    @RequirePermission("mkt:channel:list")
    public Result<Map<String, Object>> healthCheck() {
        return channelDispatcher.healthCheckAll();
    }
}