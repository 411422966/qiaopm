package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ResourceConflictLog;
import com.meiyetec.service.ResourceConflictLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/resource/conflict")
@RequiredArgsConstructor
@SysOpLog(module = "Resource Conflict")
public class ResourceConflictLogController {

    private final ResourceConflictLogService resourceConflictLogService;

    @GetMapping("/list")
    public Result<List<ResourceConflictLog>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String conflictType) {
        return Result.success(resourceConflictLogService.queryList(storeId, conflictType));
    }
}
