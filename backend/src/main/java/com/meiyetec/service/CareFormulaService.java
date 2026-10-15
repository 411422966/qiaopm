package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CareFormula;
import com.meiyetec.entity.CareFormulaApply;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CareFormulaApplyMapper;
import com.meiyetec.mapper.CareFormulaMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 补齐 4：独立配方表（CareFormula）
 *
 * CareSuggestionService 已含推荐逻辑，但无独立配方表
 * 配方维度：
 * - 染发配方（HAIR_DYE）
 * - 护肤配方（SKINCARE）
 * - 美甲配方（NAIL）
 * - 美容仪器（BODY）
 * - 医美（MEDICAL）
 *
 * 字段：原料比例 + 步骤 + 适用肤质/发质 + 注意事项
 */
@Slf4j
@RestController
@RequestMapping("/care/formula")
@RequiredArgsConstructor
public class CareFormulaService {

    private final CareFormulaMapper formulaMapper;
    private final CareFormulaApplyMapper applyMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 配方管理
    // ====================================================================

    @PostMapping("/create")
    public Result<CareFormula> create(@RequestBody CareFormula f) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (f.getFormulaCode() == null || f.getFormulaName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (f.getFormulaType() == null) f.setFormulaType("SKINCARE");
        if (f.getVersion() == null) f.setVersion("1.0");
        if (f.getIsPremium() == null) f.setIsPremium(0);
        if (f.getUseCount() == null) f.setUseCount(0);
        f.setTenantId(tenantId);
        f.setId(null);
        formulaMapper.insert(f);
        return Result.success("已创建", f);
    }

    @GetMapping("/list")
    public Result<List<CareFormula>> list(@RequestParam(required = false) String formulaType,
                                          @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CareFormula> qw = new LambdaQueryWrapper<CareFormula>()
                .eq(CareFormula::getTenantId, tenantId)
                .orderByDesc(CareFormula::getId);
        if (formulaType != null) qw.eq(CareFormula::getFormulaType, formulaType);
        if (keyword != null && !keyword.isEmpty()) qw.like(CareFormula::getFormulaName, keyword);
        return Result.success(formulaMapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public Result<CareFormula> get(@PathVariable Long id) {
        return Result.success(formulaMapper.selectById(id));
    }

    @PostMapping("/{id}/update")
    public Result<CareFormula> update(@PathVariable Long id, @RequestBody CareFormula f) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CareFormula exist = formulaMapper.selectById(id);
        if (exist == null || !exist.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        f.setId(id);
        f.setTenantId(tenantId);
        formulaMapper.updateById(f);
        return Result.success("已更新", f);
    }

    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        formulaMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    // ====================================================================
    // 智能推荐（根据肤质/发质 + 服务）
    // ====================================================================

    @PostMapping("/recommend")
    public Result<List<CareFormula>> recommend(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String formulaType = (String) body.getOrDefault("formulaType", "SKINCARE");
        String skinType = (String) body.get("skinType");
        String hairType = (String) body.get("hairType");
        String service = (String) body.get("service");

        List<CareFormula> all = formulaMapper.selectList(new LambdaQueryWrapper<CareFormula>()
                .eq(CareFormula::getTenantId, tenantId)
                .eq(CareFormula::getFormulaType, formulaType));

        // 简单规则匹配
        List<CareFormula> recommended = all.stream()
                .filter(f -> skinType == null || skinType.equals(f.getSkinType()) || "NORMAL".equals(f.getSkinType()) || f.getSkinType() == null)
                .filter(f -> hairType == null || hairType.equals(f.getHairType()) || f.getHairType() == null)
                .sorted(Comparator.comparing(CareFormula::getUseCount).reversed())
                .limit(20)
                .collect(Collectors.toList());
        return Result.success(recommended);
    }

    // ====================================================================
    // 应用记录
    // ====================================================================

    /**
     * 应用配方（技师给会员做护理时记录）
     */
    @PostMapping("/apply")
    @Transactional(rollbackFor = Exception.class)
    public Result<CareFormulaApply> apply(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CareFormulaApply a = new CareFormulaApply();
        a.setTenantId(tenantId);
        a.setFormulaId(((Number) body.get("formulaId")).longValue());
        a.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        a.setBookingId(body.get("bookingId") == null ? null : ((Number) body.get("bookingId")).longValue());
        a.setServiceId(body.get("serviceId") == null ? null : ((Number) body.get("serviceId")).longValue());
        a.setTechnicianId(body.get("technicianId") == null ? null : ((Number) body.get("technicianId")).longValue());
        a.setFeedback((String) body.get("feedback"));
        a.setRating((Integer) body.get("rating"));
        a.setEffectJson((String) body.get("effectJson"));
        applyMapper.insert(a);

        // use_count +1
        jdbc.update("UPDATE care_formula SET use_count = use_count + 1 WHERE id = ?", a.getFormulaId());
        return Result.success("已记录", a);
    }

    @GetMapping("/apply/log")
    public Result<List<CareFormulaApply>> applyLog(@RequestParam(required = false) Long formulaId,
                                                   @RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CareFormulaApply> qw = new LambdaQueryWrapper<CareFormulaApply>()
                .eq(CareFormulaApply::getTenantId, tenantId)
                .orderByDesc(CareFormulaApply::getId)
                .last("LIMIT " + limit);
        if (formulaId != null) qw.eq(CareFormulaApply::getFormulaId, formulaId);
        return Result.success(applyMapper.selectList(qw));
    }

    // ====================================================================
    // 初始化示例数据
    // ====================================================================

    @PostMapping("/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefault() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int created = 0;
        List<CareFormula> defaults = new ArrayList<>();

        // 护肤
        defaults.add(formula("SKN_HYDRATE_001", "玻尿酸补水护理", "SKINCARE", "DRY", null, 45, 0,
                "[{\"name\":\"玻尿酸原液\",\"ratio\":30,\"unit\":\"ml\"},{\"name\":\"化妆水\",\"ratio\":50,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"清洁\",\"duration\":5},{\"order\":2,\"action\":\"玻尿酸导入\",\"duration\":15},{\"order\":3,\"action\":\"面膜\",\"duration\":20},{\"order\":4,\"action\":\"面霜\",\"duration\":5}]",
                "敏感肌先小范围测试", "HYDRATE,DRY"));

        defaults.add(formula("SKN_ANTIAGE_002", "抗皱焕颜护理", "SKINCARE", "NORMAL", null, 60, 1,
                "[{\"name\":\"胶原蛋白\",\"ratio\":20,\"unit\":\"ml\"},{\"name\":\"精华\",\"ratio\":30,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"清洁\",\"duration\":5},{\"order\":2,\"action\":\"仪器按摩\",\"duration\":25},{\"order\":3,\"action\":\"抗皱面膜\",\"duration\":25},{\"order\":4,\"action\":\"面霜\",\"duration\":5}]",
                "孕妇慎用", "ANTIAGE,LIFTING"));

        defaults.add(formula("SKN_WHITEN_003", "美白淡斑护理", "SKINCARE", "MIXED", null, 50, 1,
                "[{\"name\":\"维C精华\",\"ratio\":15,\"unit\":\"ml\"},{\"name\":\"美白霜\",\"ratio\":25,\"unit\":\"g\"}]",
                "[{\"order\":1,\"action\":\"清洁\",\"duration\":5},{\"order\":2,\"action\":\"美白导入\",\"duration\":20},{\"order\":3,\"action\":\"美白面膜\",\"duration\":20},{\"order\":4,\"action\":\"防晒\",\"duration\":5}]",
                "使用后避免强光", "WHITEN,BRIGHTEN"));

        // 染发
        defaults.add(formula("HRD_DYE_BROWN_001", "栗棕色染发", "HAIR_DYE", null, "DRIED", 90, 0,
                "[{\"name\":\"染膏\",\"ratio\":60,\"unit\":\"g\"},{\"name\":\"双氧奶\",\"ratio\":60,\"unit\":\"ml\"},{\"name\":\"护色精华\",\"ratio\":5,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"洗发\",\"duration\":5},{\"order\":2,\"action\":\"调配\",\"duration\":10},{\"order\":3,\"action\":\"涂抹\",\"duration\":20},{\"order\":4,\"action\":\"等待\",\"duration\":30},{\"order\":5,\"action\":\"冲洗\",\"duration\":15},{\"order\":6,\"action\":\"护发\",\"duration\":10}]",
                "皮肤敏感者先测试", "HAIR_DYE,BROWN"));

        defaults.add(formula("HRD_DYE_RED_002", "酒红色染发", "HAIR_DYE", null, "NORMAL", 120, 1,
                "[{\"name\":\"红色染膏\",\"ratio\":60,\"unit\":\"g\"},{\"name\":\"6%双氧奶\",\"ratio\":60,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"洗发\",\"duration\":5},{\"order\":2,\"action\":\"调配\",\"duration\":10},{\"order\":3,\"action\":\"分层涂抹\",\"duration\":30},{\"order\":4,\"action\":\"加热\",\"duration\":40},{\"order\":5,\"action\":\"冲洗\",\"duration\":15},{\"order\":6,\"action\":\"护色\",\"duration\":15}]",
                "颜色持久但易掉色，染前3天不洗头", "HAIR_DYE,RED"));

        // 烫发
        defaults.add(formula("HRD_PERM_001", "韩式大波浪", "HAIR_DYE", null, "STRAIGHT", 180, 1,
                "[{\"name\":\"冷烫液\",\"ratio\":80,\"unit\":\"ml\"},{\"name\":\"定型水\",\"ratio\":80,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"洗发\",\"duration\":10},{\"order\":2,\"action\":\"上杠\",\"duration\":40},{\"order\":3,\"action\":\"冷烫液\",\"duration\":30},{\"order\":4,\"action\":\"定型\",\"duration\":30},{\"order\":5,\"action\":\"拆杠\",\"duration\":20},{\"order\":6,\"action\":\"造型\",\"duration\":30}]",
                "卷度保持3-6个月", "PERM,CURLY"));

        // 美甲
        defaults.add(formula("NAIL_GEL_001", "法式凝胶甲", "NAIL", null, null, 60, 0,
                "[{\"name\":\"底胶\",\"ratio\":5,\"unit\":\"ml\"},{\"name\":\"色胶\",\"ratio\":10,\"unit\":\"ml\"},{\"name\":\"顶胶\",\"ratio\":5,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"修甲\",\"duration\":10},{\"order\":2,\"action\":\"底胶\",\"duration\":3},{\"order\":3,\"action\":\"照灯\",\"duration\":2},{\"order\":4,\"action\":\"色胶\",\"duration\":5},{\"order\":5,\"action\":\"照灯\",\"duration\":2},{\"order\":6,\"action\":\"顶胶\",\"duration\":3},{\"order\":7,\"action\":\"照灯\",\"duration\":2}]",
                "保持28天不掉", "NAIL,GEL,FRENCH"));

        defaults.add(formula("NAIL_ART_002", "手绘艺术甲", "NAIL", null, null, 90, 1,
                "[{\"name\":\"底胶\",\"ratio\":5,\"unit\":\"ml\"},{\"name\":\"丙烯颜料\",\"ratio\":3,\"unit\":\"g\"},{\"name\":\"顶胶\",\"ratio\":5,\"unit\":\"ml\"}]",
                "[{\"order\":1,\"action\":\"修甲\",\"duration\":10},{\"order\":2,\"action\":\"底胶\",\"duration\":3},{\"order\":3,\"action\":\"照灯\",\"duration\":2},{\"order\":4,\"action\":\"底色\",\"duration\":5},{\"order\":5,\"action\":\"手绘\",\"duration\":30},{\"order\":6,\"action\":\"照灯\",\"duration\":2},{\"order\":7,\"action\":\"顶胶\",\"duration\":3}]",
                "每款独一无二", "NAIL,ART,CUSTOM"));

        // 医美
        defaults.add(formula("MED_HYDRA_001", "水光针方案", "MEDICAL", "NORMAL", null, 45, 1,
                "[{\"name\":\"玻尿酸\",\"ratio\":2.5,\"unit\":\"ml\"},{\"name\":\"麻醉膏\",\"ratio\":5,\"unit\":\"g\"}]",
                "[{\"order\":1,\"action\":\"清洁\",\"duration\":5},{\"order\":2,\"action\":\"麻醉\",\"duration\":20},{\"order\":3,\"action\":\"水光枪导入\",\"duration\":15},{\"order\":4,\"action\":\"医用面膜\",\"duration\":5}]",
                "需有医美资质医师操作，术后注意防晒", "MEDICAL,HYDRA,WHITEN"));

        defaults.add(formula("MED_PRF_002", "PRP 自体血清", "MEDICAL", "NORMAL", null, 60, 1,
                "[{\"name\":\"自体血清\",\"ratio\":10,\"unit\":\"ml\"},{\"name\":\"麻醉膏\",\"ratio\":5,\"unit\":\"g\"}]",
                "[{\"order\":1,\"action\":\"抽血\",\"duration\":10},{\"order\":2,\"action\":\"离心\",\"duration\":15},{\"order\":3,\"action\":\"麻醉\",\"duration\":15},{\"order\":4,\"action\":\"导入\",\"duration\":15},{\"order\":5,\"action\":\"医用面膜\",\"duration\":5}]",
                "需有医美资质，3 次一疗程", "MEDICAL,PRP,REGENERATE"));

        for (CareFormula f : defaults) {
            CareFormula exist = formulaMapper.selectOne(new LambdaQueryWrapper<CareFormula>()
                    .eq(CareFormula::getTenantId, tenantId)
                    .eq(CareFormula::getFormulaCode, f.getFormulaCode()));
            if (exist != null) continue;
            f.setTenantId(tenantId);
            f.setId(null);
            formulaMapper.insert(f);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("totalAvailable", defaults.size());
        return Result.success("已初始化", data);
    }

    private CareFormula formula(String code, String name, String type, String skin, String hair,
                                int duration, int premium, String ingredients, String steps,
                                String caution, String tags) {
        CareFormula f = new CareFormula();
        f.setFormulaCode(code);
        f.setFormulaName(name);
        f.setFormulaType(type);
        f.setSkinType(skin);
        f.setHairType(hair);
        f.setDurationMin(duration);
        f.setIsPremium(premium);
        f.setIngredientsJson(ingredients);
        f.setStepsJson(steps);
        f.setCaution(caution);
        f.setTags(tags);
        f.setVersion("1.0");
        f.setUseCount(0);
        f.setDescription(name + " - 标准方案");
        return f;
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("total", formulaMapper.selectCount(new LambdaQueryWrapper<CareFormula>().eq(CareFormula::getTenantId, tenantId)));
            data.put("premium", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM care_formula WHERE tenant_id = ? AND is_premium = 1", Long.class, tenantId));
            data.put("byType", jdbc.queryForList(
                    "SELECT formula_type, COUNT(*) AS cnt FROM care_formula WHERE tenant_id = ? GROUP BY formula_type", tenantId));
            data.put("applyCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM care_formula_apply WHERE tenant_id = ?", Long.class, tenantId));
            data.put("topFormulas", jdbc.queryForList(
                    "SELECT formula_id, COUNT(*) AS cnt FROM care_formula_apply WHERE tenant_id = ? GROUP BY formula_id ORDER BY cnt DESC LIMIT 5", tenantId));
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(data);
        }
    }
}