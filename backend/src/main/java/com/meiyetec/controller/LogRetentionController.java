package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.SysConfigService;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/sys/log-retention")
@RequiredArgsConstructor
@SysOpLog(module = "系统")
public class LogRetentionController {

    private static final String CONFIG_KEY = "log.retention-days";
    private static final int DEFAULT_DAYS = 90;
    private static final String DESCRIPTION = "操作日志保留天数";

    private final SysConfigService configService;

    @GetMapping
    public Result<Map<String, Object>> get() {
        Map<String, Object> r = new HashMap<>();
        r.put("key", CONFIG_KEY);
        r.put("value", configService.getInt(CONFIG_KEY, DEFAULT_DAYS));
        r.put("defaultValue", DEFAULT_DAYS);
        r.put("description", DESCRIPTION);
        return Result.success(r);
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody RetentionDTO dto) {
        configService.putInt(CONFIG_KEY, dto.getDays(), DESCRIPTION);
        return Result.success("保存成功，下次清理生效", null);
    }

    @Data
    public static class RetentionDTO {
        @Min(value = 1, message = "保留天数不能小于 1")
        @Max(value = 3650, message = "保留天数不能大于 3650")
        private Integer days;
    }
}