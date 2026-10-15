package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CareSuggestionTemplate;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionTemplateService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crm/care/suggestion/template")
@RequiredArgsConstructor
@SysOpLog(module = "Care Suggestion Template")
public class CareSuggestionTemplateController {

    private final CareSuggestionTemplateService careSuggestionTemplateService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<CareSuggestionTemplate>> list(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) Integer status) {
        return Result.success(careSuggestionTemplateService.list(templateType, status));
    }

    @GetMapping("/{id:\\d+}")
    public Result<CareSuggestionTemplate> getById(@PathVariable Long id) {
        CareSuggestionTemplate t = careSuggestionTemplateService.getById(id);
        if (t == null || !tid().equals(t.getTenantId())) return Result.error(BizCode.NOT_FOUND, "模板不存在");
        return Result.success(t);
    }

    @PostMapping
    public Result<CareSuggestionTemplate> create(@RequestBody CareSuggestionTemplate template) {
        template.setId(null);
        template.setTenantId(tid());
        careSuggestionTemplateService.save(template);
        return Result.success("创建成功", template);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CareSuggestionTemplate template) {
        CareSuggestionTemplate exist = careSuggestionTemplateService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "模板不存在");
        template.setId(id);
        template.setTenantId(tid());
        careSuggestionTemplateService.updateById(template);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        CareSuggestionTemplate exist = careSuggestionTemplateService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "模板不存在");
        careSuggestionTemplateService.removeById(id);
        return Result.success("删除成功", null);
    }
}
