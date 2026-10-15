package com.meiyetec.controller;

import com.meiyetec.entity.DcepConfig;
import com.meiyetec.service.DcepConfigService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * P1-DCEP-001: DCEP 模式查询接口（只读）。
 * 用于前端 dcep-advanced.vue 顶部 mock/dev 模式警告横幅。
 * 路径 /dcep/config/mode，避免与现有 /sys/dcep 管理接口冲突。
 */
@RestController
@RequestMapping("/dcep/config")
@RequiredArgsConstructor
public class DcepConfigModeController {

    private final DcepConfigService service;

    /**
     * 返回 DCEP 当前运行环境模式：
     * - "production": 真实网关
     * - "sandbox": 沙箱
     * - "dev": 开发/演示（默认，未配置时）
     */
    @GetMapping("/mode")
    public Result<Map<String, String>> getMode() {
        DcepConfig cfg = service.list().stream().findFirst().orElse(null);
        String mode = (cfg != null && cfg.getEnvironment() != null)
                ? cfg.getEnvironment()
                : "dev";
        Map<String, String> result = new HashMap<>();
        result.put("mode", mode);
        return Result.success(result);
    }
}