package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.IndustryServiceRelation;
import com.meiyetec.entity.IndustryTemplate;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.IndustryServiceRelationMapper;
import com.meiyetec.mapper.IndustryTemplateMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * R3.0-H P2.5：行业 SaaS 模板
 *
 * <p>提供 5 个行业模板（美业/医美/口腔/美发/月子），含默认服务/商品/表单。
 */
@Slf4j
@RestController
@RequestMapping("/industry")
@RequiredArgsConstructor
public class IndustryService {

    private final IndustryTemplateMapper templateMapper;
    private final IndustryServiceRelationMapper relationMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping("/template/list")
    public Result<List<IndustryTemplate>> listTemplates() {
        return Result.success(templateMapper.selectList(
                new LambdaQueryWrapper<IndustryTemplate>()
                        .eq(IndustryTemplate::getEnabled, 1)
                        .orderByAsc(IndustryTemplate::getSortOrder)));
    }

    @GetMapping("/template/{code}")
    public Result<Map<String, Object>> getTemplate(@PathVariable String code) {
        IndustryTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<IndustryTemplate>()
                        .eq(IndustryTemplate::getIndustryCode, code));
        if (tpl == null) return Result.error(BizCode.NOT_FOUND, "行业不存在");
        List<IndustryServiceRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<IndustryServiceRelation>()
                        .eq(IndustryServiceRelation::getIndustryCode, code)
                        .orderByAsc(IndustryServiceRelation::getSortOrder));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("template", tpl);
        data.put("relations", relations);
        return Result.success(data);
    }

    @PostMapping("/template")
    public Result<IndustryTemplate> createTemplate(@RequestBody IndustryTemplate t) {
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getSortOrder() == null) t.setSortOrder(99);
        IndustryTemplate exist = templateMapper.selectOne(
                new LambdaQueryWrapper<IndustryTemplate>()
                        .eq(IndustryTemplate::getIndustryCode, t.getIndustryCode()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "industryCode 已存在");
        templateMapper.insert(t);
        return Result.success("已创建", t);
    }

    @PostMapping("/relation")
    public Result<IndustryServiceRelation> addRelation(@RequestBody IndustryServiceRelation r) {
        IndustryServiceRelation exist = relationMapper.selectOne(
                new LambdaQueryWrapper<IndustryServiceRelation>()
                        .eq(IndustryServiceRelation::getIndustryCode, r.getIndustryCode())
                        .eq(IndustryServiceRelation::getTargetType, r.getTargetType())
                        .eq(IndustryServiceRelation::getTargetCode, r.getTargetCode()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "关联已存在");
        relationMapper.insert(r);
        return Result.success("已添加", r);
    }

    /**
     * 一键应用模板到租户（复制默认服务/商品/表单）
     */
    @PostMapping("/template/{code}/apply")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> applyTemplate(@PathVariable String code,
                                                       @RequestBody Map<String, Object> body) {
        Long tenantId = ((Number) body.getOrDefault("tenantId", 1)).longValue();
        IndustryTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<IndustryTemplate>()
                        .eq(IndustryTemplate::getIndustryCode, code));
        if (tpl == null) return Result.error(BizCode.NOT_FOUND, "模板不存在");

        // 简单模拟：写 tenant_industry 标记
        int applied = 0;
        try {
            jdbc.update("INSERT IGNORE INTO tenant_industry (tenant_id, industry_code, applied_at) VALUES (?, ?, NOW())",
                    tenantId, code);
            applied++;
        } catch (Exception e) {
            log.warn("应用模板失败：{}", e.getMessage());
            throw new RuntimeException("应用模板失败: " + e.getMessage(), e);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateCode", code);
        data.put("tenantId", tenantId);
        data.put("applied", applied);
        return Result.success("模板已应用", data);
    }
}