package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmCrossStoreRule;
import com.meiyetec.entity.CrmCrossStoreRedemption;
import com.meiyetec.entity.Member;
import com.meiyetec.mapper.CrmCrossStoreRuleMapper;
import com.meiyetec.mapper.CrmCrossStoreRedemptionMapper;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨店规则 / 跨店核销 controller（/crm/cross/*）
 *
 * <p>前端路由：
 * <ul>
 *   <li>cross-rule.vue（/crm/cross-rule）→ /rule/*</li>
 *   <li>cross-record.vue（/crm/cross-record）→ /redemption/* + /member/lookup</li>
 * </ul>
 *
 * <p>数据源：crm_cross_store_rule + crm_cross_store_redemption（均含 tenant_id）
 *
 * <p>整合点：
 * <ul>
 *   <li>规则 CRUD（MyBatis-Plus）+ 回收站/恢复/批量/克隆/测试/预览</li>
 *   <li>核销记录 CRUD（含统计 + 撤销）</li>
 *   <li>跨店识别 / 规则匹配 / 手续费计算 / 单笔限额校验（引擎方法）</li>
 *   <li>提供 {@link #checkCrossStoreAndQuote} 给 PackageController.doRedeem 等主链路拦截</li>
 * </ul>
 *
 * <p>迁移说明：R3.0-B 原放在 service/CrossStoreService.java（@RestController 误放 service 包），
 * 与 disabled 控制器路径冲突。本 controller 是合并 A1 后的唯一跨店入口。
 */
@Slf4j
@RestController
@RequestMapping("/crm/cross")
@RequiredArgsConstructor
@SysOpLog(module = "CRM 跨店")
public class CrossStoreController {

    private final CrmCrossStoreRuleMapper ruleMapper;
    private final CrmCrossStoreRedemptionMapper redemptionMapper;
    private final MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    private String username() {
        String u = TenantContextHolder.get().getUsername();
        return u != null ? u : "system";
    }

    // ====================================================================
    // 1. 跨店规则 CRUD（MyBatis-Plus + 回收站/批量/克隆）
    // ====================================================================

    /**
     * 规则统计卡（前端 cross-rule.vue 期望字段）
     * 字段对齐：totalAmount / totalFee / lastWeekAmount / lastWeekFee /
     *          topRuleName / topHitCount / totalRules / enabledRules
     */
    @GetMapping("/rule/stats")
    public Result<Map<String, Object>> ruleStats() {
        Long t = tid();

        // ----- 规则侧 -----
        long totalRules = ruleMapper.selectCount(new LambdaQueryWrapper<CrmCrossStoreRule>()
                .eq(CrmCrossStoreRule::getTenantId, t)
                .eq(CrmCrossStoreRule::getDeleted, 0));
        long enabledRules = ruleMapper.selectCount(new LambdaQueryWrapper<CrmCrossStoreRule>()
                .eq(CrmCrossStoreRule::getTenantId, t)
                .eq(CrmCrossStoreRule::getDeleted, 0)
                .eq(CrmCrossStoreRule::getEnabled, 1));

        // TOP 规则（按 hit_count 降序，NULL 排后面）
        List<CrmCrossStoreRule> topCandidates = ruleMapper.selectList(new LambdaQueryWrapper<CrmCrossStoreRule>()
                .eq(CrmCrossStoreRule::getTenantId, t)
                .eq(CrmCrossStoreRule::getDeleted, 0)
                .isNotNull(CrmCrossStoreRule::getHitCount)
                .gt(CrmCrossStoreRule::getHitCount, 0)
                .orderByDesc(CrmCrossStoreRule::getHitCount)
                .orderByDesc(CrmCrossStoreRule::getId)
                .last("LIMIT 1"));
        String topRuleName = topCandidates.isEmpty() ? null : topCandidates.get(0).getRuleName();
        Long topHitCount = topCandidates.isEmpty() ? 0L : topCandidates.get(0).getHitCount();

        // ----- 核销侧（本月 + 上周）-----
        // 本月 = 当月 1 号 00:00:00 起；上周 = 上一个完整自然周（8-14 天前）
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime lastWeekStart = LocalDateTime.now().minusDays(14);
        LocalDateTime lastWeekEnd = LocalDateTime.now().minusDays(8);

        Map<String, Object> monthAgg = jdbc.queryForMap(
                "SELECT COALESCE(SUM(net_amount),0)  AS amount, " +
                "       COALESCE(SUM(cross_fee),0)   AS fee " +
                "FROM crm_cross_store_redemption " +
                "WHERE deleted = 0 AND tenant_id = ? AND status = 1 AND create_time >= ?",
                t, monthStart);
        Map<String, Object> lastWeekAgg = jdbc.queryForMap(
                "SELECT COALESCE(SUM(net_amount),0)  AS amount, " +
                "       COALESCE(SUM(cross_fee),0)   AS fee " +
                "FROM crm_cross_store_redemption " +
                "WHERE deleted = 0 AND tenant_id = ? AND status = 1 " +
                "  AND create_time >= ? AND create_time < ?",
                t, lastWeekStart, lastWeekEnd);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRules", totalRules);
        out.put("enabledRules", enabledRules);
        out.put("topRuleName", topRuleName);
        out.put("topHitCount", topHitCount);
        out.put("totalAmount", monthAgg.get("amount"));
        out.put("totalFee", monthAgg.get("fee"));
        out.put("lastWeekAmount", lastWeekAgg.get("amount"));
        out.put("lastWeekFee", lastWeekAgg.get("fee"));
        return Result.success(out);
    }

    @GetMapping("/rule/page")
    public Result<Page<CrmCrossStoreRule>> rulePage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Boolean includeDeleted) {
        Long t = tid();
        LambdaQueryWrapper<CrmCrossStoreRule> qw = new LambdaQueryWrapper<>();
        qw.eq(CrmCrossStoreRule::getTenantId, t);
        if (!Boolean.TRUE.equals(includeDeleted)) {
            qw.eq(CrmCrossStoreRule::getDeleted, 0);
        }
        if (enabled != null) qw.eq(CrmCrossStoreRule::getEnabled, enabled);
        if (brandId != null) {
            // brandId 过滤：brand 匹配 OR 租户通用（brand_id IS NULL）
            qw.and(w -> w.eq(CrmCrossStoreRule::getBrandId, brandId)
                    .or().isNull(CrmCrossStoreRule::getBrandId));
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(CrmCrossStoreRule::getRuleName, keyword)
                    .or().like(CrmCrossStoreRule::getRemark, keyword));
        }
        qw.orderByDesc(CrmCrossStoreRule::getPriority).orderByDesc(CrmCrossStoreRule::getId);
        return Result.success(ruleMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    @GetMapping("/rule/trash")
    public Result<Page<CrmCrossStoreRule>> ruleTrash(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long t = tid();
        LambdaQueryWrapper<CrmCrossStoreRule> qw = new LambdaQueryWrapper<>();
        qw.eq(CrmCrossStoreRule::getTenantId, t)
          .gt(CrmCrossStoreRule::getDeleted, 0)
          .orderByDesc(CrmCrossStoreRule::getUpdateTime);
        return Result.success(ruleMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    @GetMapping("/rule/{id}")
    public Result<CrmCrossStoreRule> ruleDetail(@PathVariable Long id) {
        Long t = tid();
        CrmCrossStoreRule r = ruleMapper.selectById(id);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        return Result.success(r);
    }

    @PostMapping("/rule")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmCrossStoreRule> createRule(@RequestBody CrmCrossStoreRule r) {
        Long t = tid();
        if (r.getRuleName() == null || r.getRuleName().isEmpty()) {
            return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "ruleName 必填");
        }
        r.setId(null);
        r.setTenantId(t);
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getCrossFeeType() == null) r.setCrossFeeType("NONE");
        if (r.getCrossFeeRate() == null) r.setCrossFeeRate(BigDecimal.ZERO);
        if (r.getCrossMaxAmount() == null) r.setCrossMaxAmount(BigDecimal.ZERO);
        if (r.getHitCount() == null) r.setHitCount(0L);
        r.setOperatorName(username());
        ruleMapper.insert(r);
        return Result.success("已创建", r);
    }

    @PutMapping("/rule/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmCrossStoreRule> updateRule(@PathVariable Long id, @RequestBody CrmCrossStoreRule patch) {
        Long t = tid();
        CrmCrossStoreRule old = ruleMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        if (patch.getRuleName() != null) old.setRuleName(patch.getRuleName());
        if (patch.getRemark() != null) old.setRemark(patch.getRemark());
        if (patch.getStartTime() != null) old.setStartTime(patch.getStartTime());
        if (patch.getEndTime() != null) old.setEndTime(patch.getEndTime());
        if (patch.getMemberCrossEnabled() != null) old.setMemberCrossEnabled(patch.getMemberCrossEnabled());
        if (patch.getPackageCrossEnabled() != null) old.setPackageCrossEnabled(patch.getPackageCrossEnabled());
        if (patch.getPackageCrossMode() != null) old.setPackageCrossMode(patch.getPackageCrossMode());
        if (patch.getPointsCrossEnabled() != null) old.setPointsCrossEnabled(patch.getPointsCrossEnabled());
        if (patch.getBalanceCrossEnabled() != null) old.setBalanceCrossEnabled(patch.getBalanceCrossEnabled());
        if (patch.getBalanceCrossMode() != null) old.setBalanceCrossMode(patch.getBalanceCrossMode());
        if (patch.getDiscountCrossEnabled() != null) old.setDiscountCrossEnabled(patch.getDiscountCrossEnabled());
        if (patch.getCouponCrossEnabled() != null) old.setCouponCrossEnabled(patch.getCouponCrossEnabled());
        if (patch.getCrossFeeRate() != null) old.setCrossFeeRate(patch.getCrossFeeRate());
        if (patch.getCrossFeeType() != null) old.setCrossFeeType(patch.getCrossFeeType());
        if (patch.getCrossMaxAmount() != null) old.setCrossMaxAmount(patch.getCrossMaxAmount());
        if (patch.getEnabled() != null) old.setEnabled(patch.getEnabled());
        if (patch.getPriority() != null) old.setPriority(patch.getPriority());
        ruleMapper.updateById(old);
        return Result.success("已更新", old);
    }

    @DeleteMapping("/rule/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteRule(@PathVariable Long id) {
        Long t = tid();
        CrmCrossStoreRule old = ruleMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        ruleMapper.deleteById(id);
        return Result.success("已删除（逻辑删除，可从回收站恢复）", null);
    }

    @PutMapping("/rule/{id}/toggle")
    public Result<Map<String, Object>> toggle(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long t = tid();
        CrmCrossStoreRule old = ruleMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        Integer target = body == null || body.get("enabled") == null
                ? (old.getEnabled() != null && old.getEnabled() == 1 ? 0 : 1)
                : ((Number) body.get("enabled")).intValue();
        old.setEnabled(target);
        ruleMapper.updateById(old);
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("enabled", target);
        return Result.success("已切换", out);
    }

    @PutMapping("/rule/batch-toggle")
    public Result<Map<String, Object>> batchToggle(@RequestBody Map<String, Object> body) {
        Long t = tid();
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        Integer enabled = body.get("enabled") == null ? 1 : ((Number) body.get("enabled")).intValue();
        if (ids == null || ids.isEmpty()) return Result.success(new HashMap<>());
        int affected = 0;
        for (Number n : ids) {
            CrmCrossStoreRule r = ruleMapper.selectById(n.longValue());
            if (r != null && t.equals(r.getTenantId())) {
                r.setEnabled(enabled);
                ruleMapper.updateById(r);
                affected++;
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("affected", affected);
        return Result.success("已批量切换", out);
    }

    @PutMapping("/rule/{id}/trash")
    public Result<Void> trash(@PathVariable Long id) {
        Long t = tid();
        CrmCrossStoreRule r = ruleMapper.selectById(id);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        r.setEnabled(0);
        ruleMapper.updateById(r);
        return Result.success("已移入回收站", null);
    }

    @PutMapping("/rule/batch-trash")
    public Result<Map<String, Object>> batchTrash(@RequestBody Map<String, Object> body) {
        Long t = tid();
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        int affected = 0;
        if (ids != null) for (Number n : ids) {
            CrmCrossStoreRule r = ruleMapper.selectById(n.longValue());
            if (r != null && t.equals(r.getTenantId())) {
                r.setEnabled(0);
                ruleMapper.updateById(r);
                affected++;
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("affected", affected);
        return Result.success("已批量移入回收站", out);
    }

    @PutMapping("/rule/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        Long t = tid();
        CrmCrossStoreRule r = ruleMapper.selectById(id);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        r.setDeleted(0);
        r.setEnabled(1);
        ruleMapper.updateById(r);
        return Result.success("已恢复", null);
    }

    @PostMapping("/rule/{id}/clone")
    public Result<CrmCrossStoreRule> cloneRule(@PathVariable Long id) {
        Long t = tid();
        CrmCrossStoreRule old = ruleMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        CrmCrossStoreRule c = new CrmCrossStoreRule();
        c.setTenantId(t);
        c.setBrandId(old.getBrandId());
        c.setRuleName(old.getRuleName() + " (副本)");
        c.setRemark(old.getRemark());
        c.setStartTime(old.getStartTime());
        c.setEndTime(old.getEndTime());
        c.setMemberCrossEnabled(old.getMemberCrossEnabled());
        c.setPackageCrossEnabled(old.getPackageCrossEnabled());
        c.setPackageCrossMode(old.getPackageCrossMode());
        c.setPointsCrossEnabled(old.getPointsCrossEnabled());
        c.setBalanceCrossEnabled(old.getBalanceCrossEnabled());
        c.setBalanceCrossMode(old.getBalanceCrossMode());
        c.setDiscountCrossEnabled(old.getDiscountCrossEnabled());
        c.setCouponCrossEnabled(old.getCouponCrossEnabled());
        c.setCrossFeeRate(old.getCrossFeeRate());
        c.setCrossFeeType(old.getCrossFeeType());
        c.setCrossMaxAmount(old.getCrossMaxAmount());
        c.setPriority(old.getPriority());
        c.setEnabled(0);
        c.setHitCount(0L);
        c.setOperatorName(username());
        ruleMapper.insert(c);
        return Result.success("已克隆（默认禁用，请检查后启用）", c);
    }

    @PostMapping("/rule/batch-copy")
    public Result<Map<String, Object>> batchCopy(@RequestBody Map<String, Object> body) {
        Long t = tid();
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        Long targetBrandId = body.get("targetBrandId") == null ? null : ((Number) body.get("targetBrandId")).longValue();
        int affected = 0;
        if (ids != null) for (Number n : ids) {
            CrmCrossStoreRule src = ruleMapper.selectById(n.longValue());
            if (src == null || !t.equals(src.getTenantId())) continue;
            src.setId(null);
            src.setBrandId(targetBrandId);
            src.setRuleName(src.getRuleName() + " (跨品牌副本)");
            src.setEnabled(0);
            src.setHitCount(0L);
            ruleMapper.insert(src);
            affected++;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("affected", affected);
        return Result.success("已批量克隆", out);
    }

    // ====================================================================
    // 2. 规则匹配 + 预览（前端 rule.vue "测试规则" + "预览匹配"）
    // ====================================================================

    @GetMapping("/rule/test")
    public Result<Map<String, Object>> ruleTest(@RequestParam Long ruleId,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) BigDecimal amount,
                                                @RequestParam(required = false) Long sourceStoreId,
                                                @RequestParam(required = false) Long targetStoreId) {
        Long t = tid();
        CrmCrossStoreRule r = ruleMapper.selectById(ruleId);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleId", r.getId());
        out.put("ruleName", r.getRuleName());
        out.put("enabled", r.getEnabled());

        boolean hit = true;
        // 启停校验
        if (r.getEnabled() != null && r.getEnabled() != 1) hit = false;
        // 时段校验
        LocalDateTime now = LocalDateTime.now();
        if (r.getStartTime() != null && now.isBefore(r.getStartTime())) hit = false;
        if (r.getEndTime() != null && now.isAfter(r.getEndTime())) hit = false;

        // 类型允许性
        String rt = type == null ? "PACKAGE" : type.toUpperCase();
        boolean typeAllowed;
        switch (rt) {
            case "PACKAGE":
                typeAllowed = r.getPackageCrossEnabled() != null && r.getPackageCrossEnabled() == 1;
                break;
            case "POINTS":
                typeAllowed = r.getPointsCrossEnabled() != null && r.getPointsCrossEnabled() == 1;
                break;
            case "BALANCE":
                typeAllowed = r.getBalanceCrossEnabled() != null && r.getBalanceCrossEnabled() == 1;
                break;
            case "DISCOUNT":
                typeAllowed = r.getDiscountCrossEnabled() != null && r.getDiscountCrossEnabled() == 1;
                break;
            case "COUPON":
                typeAllowed = r.getCouponCrossEnabled() != null && r.getCouponCrossEnabled() == 1;
                break;
            default:
                typeAllowed = false;
        }
        if (!typeAllowed) hit = false;

        BigDecimal fee = BigDecimal.ZERO;
        if (hit && amount != null && amount.signum() > 0) {
            String feeType = r.getCrossFeeType() == null ? "NONE" : r.getCrossFeeType();
            if ("PERCENT".equals(feeType) && r.getCrossFeeRate() != null) {
                fee = amount.multiply(r.getCrossFeeRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if ("FIXED".equals(feeType)) {
                fee = r.getCrossMaxAmount() == null ? BigDecimal.ZERO : r.getCrossMaxAmount();
            }
            if (r.getCrossMaxAmount() != null && r.getCrossMaxAmount().signum() > 0 && fee.compareTo(r.getCrossMaxAmount()) > 0) {
                fee = r.getCrossMaxAmount();
            }
        }
        BigDecimal net = amount == null ? null : amount.subtract(fee);
        out.put("hit", hit);
        out.put("typeAllowed", typeAllowed);
        out.put("crossFee", fee);
        out.put("netAmount", net);
        out.put("sourceStoreId", sourceStoreId);
        out.put("targetStoreId", targetStoreId);
        return Result.success("模拟完成", out);
    }

    @GetMapping("/rule/preview-match")
    public Result<List<Map<String, Object>>> previewMatch(@RequestParam Long ruleId) {
        Long t = tid();
        CrmCrossStoreRule r = ruleMapper.selectById(ruleId);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "规则不存在");
        // 取最近 5 条匹配此规则的核销（rule_id = ruleId），作为预览样例
        List<CrmCrossStoreRedemption> samples = redemptionMapper.selectList(
                new LambdaQueryWrapper<CrmCrossStoreRedemption>()
                        .eq(CrmCrossStoreRedemption::getTenantId, t)
                        .eq(CrmCrossStoreRedemption::getRuleId, ruleId)
                        .orderByDesc(CrmCrossStoreRedemption::getId)
                        .last("LIMIT 5"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (CrmCrossStoreRedemption s : samples) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("orderNo", s.getOrderNo());
            m.put("memberName", s.getMemberName());
            m.put("sourceStoreId", s.getSourceStoreId());
            m.put("targetStoreId", s.getTargetStoreId());
            m.put("originalAmount", s.getOriginalAmount());
            m.put("crossFee", s.getCrossFee());
            m.put("netAmount", s.getNetAmount());
            m.put("status", s.getStatus());
            m.put("createTime", s.getCreateTime());
            out.add(m);
        }
        return Result.success(out);
    }

    // ====================================================================
    // 3. 跨店核销 record（CRUD + 撤销 + 统计）
    // ====================================================================

    @GetMapping("/redemption/page")
    public Result<Page<CrmCrossStoreRedemption>> redemptionPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String redemptionType,
            @RequestParam(required = false) Long sourceStoreId,
            @RequestParam(required = false) Long targetStoreId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long t = tid();
        LambdaQueryWrapper<CrmCrossStoreRedemption> qw = new LambdaQueryWrapper<>();
        qw.eq(CrmCrossStoreRedemption::getTenantId, t)
          .eq(CrmCrossStoreRedemption::getDeleted, 0);
        if (status != null) qw.eq(CrmCrossStoreRedemption::getStatus, status);
        if (sourceStoreId != null) qw.eq(CrmCrossStoreRedemption::getSourceStoreId, sourceStoreId);
        if (targetStoreId != null) qw.eq(CrmCrossStoreRedemption::getTargetStoreId, targetStoreId);
        if (redemptionType != null && !redemptionType.isEmpty()) qw.eq(CrmCrossStoreRedemption::getRedemptionType, redemptionType);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(CrmCrossStoreRedemption::getOrderNo, keyword)
                    .or().like(CrmCrossStoreRedemption::getMemberName, keyword)
                    .or().like(CrmCrossStoreRedemption::getMemberPhone, keyword));
        }
        if (dateFrom != null && !dateFrom.isEmpty()) {
            qw.ge(CrmCrossStoreRedemption::getCreateTime, LocalDateTime.parse(dateFrom + "T00:00:00"));
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            qw.le(CrmCrossStoreRedemption::getCreateTime, LocalDateTime.parse(dateTo + "T23:59:59"));
        }
        qw.orderByDesc(CrmCrossStoreRedemption::getId);
        return Result.success(redemptionMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    /**
     * 核销统计（cross-record.vue 顶部统计卡）
     * 字段：total / today / originalSum / feeSum / netSum / typeDist
     */
    @GetMapping("/redemption/stats")
    public Result<Map<String, Object>> redemptionStats(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long t = tid();
        StringBuilder where = new StringBuilder(" AND r.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(t);
        if (dateFrom != null && !dateFrom.isEmpty()) {
            where.append("AND r.create_time >= ? ");
            args.add(dateFrom + " 00:00:00");
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            where.append("AND r.create_time <= ? ");
            args.add(dateTo + " 23:59:59");
        }

        Map<String, Object> total = jdbc.queryForMap(
            "SELECT COUNT(*) AS cnt, " +
            "       COALESCE(SUM(r.original_amount),0) AS original_sum, " +
            "       COALESCE(SUM(r.cross_fee),0) AS fee_sum, " +
            "       COALESCE(SUM(r.net_amount),0) AS net_sum " +
            "FROM crm_cross_store_redemption r WHERE r.deleted = 0 " + where,
            args.toArray());

        Map<String, Object> today = jdbc.queryForMap(
            "SELECT COUNT(*) AS cnt " +
            "FROM crm_cross_store_redemption r " +
            "WHERE r.deleted = 0 AND r.tenant_id = ? AND DATE(r.create_time) = CURDATE()",
            t);

        List<Map<String, Object>> typeRows = jdbc.queryForList(
            "SELECT r.redemption_type AS type, COUNT(*) AS cnt " +
            "FROM crm_cross_store_redemption r WHERE r.deleted = 0 " + where +
            "GROUP BY r.redemption_type",
            args.toArray());
        Map<String, Long> typeDist = new LinkedHashMap<>();
        for (Map<String, Object> row : typeRows) {
            Object rt = row.get("type"); Object c = row.get("cnt");
            typeDist.put(rt == null ? "OTHER" : rt.toString(), ((Number) c).longValue());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("total", ((Number) total.get("cnt")).longValue());
        out.put("today", ((Number) today.get("cnt")).longValue());
        out.put("originalSum", total.get("original_sum"));
        out.put("feeSum", total.get("fee_sum"));
        out.put("netSum", total.get("net_sum"));
        out.put("typeDist", typeDist);
        return Result.success(out);
    }

    @PostMapping("/redemption/{id}/revoke")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> revoke(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long t = tid();
        CrmCrossStoreRedemption r = redemptionMapper.selectById(id);
        if (r == null || !t.equals(r.getTenantId())) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "核销记录不存在");
        if (r.getStatus() != null && r.getStatus() == 2) return Result.error(com.meiyetec.exception.BizCode.CONFLICT, "已撤销，不可重复撤销");
        r.setStatus(2);
        redemptionMapper.updateById(r);
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("status", 2);
        out.put("remark", body == null ? null : body.get("remark"));
        return Result.success("已撤销", out);
    }

    // ====================================================================
    // 4. 会员识别 + 跨店规则匹配（前端跨店识别场景）
    // ====================================================================

    @GetMapping("/member/lookup")
    public Result<Map<String, Object>> memberLookup(@RequestParam String phone) {
        Long t = tid();
        if (phone == null || phone.isBlank()) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "phone 必填");

        // 会员主档
        List<Member> memberList = memberMapper.selectList(new QueryWrapper<Member>()
                .eq("tenant_id", t).eq("deleted", 0).eq("phone", phone).last("LIMIT 1"));
        if (memberList == null || memberList.isEmpty()) return Result.success(null);
        Member m = memberList.get(0);

        Long memberId = m.getId();
        Long currentStoreId = TenantContextHolder.get().getStoreId();
        Long sourceStoreId = m.getStoreId();

        // 命中判断
        boolean isCross = currentStoreId != null && !currentStoreId.equals(sourceStoreId);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("name", m.getName());
        data.put("phone", m.getPhone());
        data.put("storeId", sourceStoreId);
        data.put("memberNo", m.getMemberNo());
        data.put("currentStoreId", currentStoreId);
        data.put("isCross", isCross);

        if (isCross) {
            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("id", memberId);
            memberMap.put("name", m.getName());
            memberMap.put("phone", m.getPhone());
            memberMap.put("store_id", sourceStoreId);
            CrmCrossStoreRule rule = matchRule(t, memberMap);
            data.put("matchedRule", rule);
            data.put("crossAvailable", rule != null);
            data.put("rulePreview", buildRulePreview(rule));
        }

        return Result.success(data);
    }

    // ====================================================================
    // 5. 跨店核销提交（主链路）+ 引擎方法（被 PackageController 调用）
    // ====================================================================

    /**
     * 提交跨店核销（落库 + 触发规则匹配 + 写 crm_member_store）
     */
    @PostMapping("/redemption")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> submitRedemption(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        String orderNo = (String) body.get("orderNo");
        String redemptionType = (String) body.get("redemptionType");
        Long sourceId = body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue();
        String sourceName = (String) body.get("sourceName");
        BigDecimal originalAmount = body.get("originalAmount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("originalAmount").toString());
        if (memberId == null || orderId == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "memberId / orderId 必填");
        if (redemptionType == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "redemptionType 必填");

        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name, phone, store_id FROM crm_member WHERE id = ? AND deleted = 0",
            memberId).stream().findFirst().orElse(null);
        if (member == null) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "会员不存在");

        Long sourceStoreId = member.get("store_id") == null ? null
                : ((Number) member.get("store_id")).longValue();
        Long currentStoreId = TenantContextHolder.get().getStoreId();
        if (currentStoreId == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "无当前门店");

        boolean isCross = sourceStoreId != null && !sourceStoreId.equals(currentStoreId);
        Long brandId = jdbc.queryForObject(
            "SELECT brand_id FROM pos_store WHERE id = ?", Long.class, currentStoreId);

        BigDecimal crossFee = BigDecimal.ZERO;
        BigDecimal netAmount = originalAmount;
        Long ruleId = null;
        String ruleSnapshot = null;

        if (isCross) {
            CrmCrossStoreRule rule = matchRule(TenantContextHolder.get().getTenantId(), member);
            if (rule == null) return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "无跨店规则");
            ruleId = rule.getId();
            ruleSnapshot = String.format("mode=%s,fee=%s%s,max=%s",
                    rule.getPackageCrossMode(), rule.getCrossFeeRate(), "%", rule.getCrossMaxAmount());

            if ("PACKAGE".equals(redemptionType) || "PROJECT".equals(redemptionType)) {
                if (rule.getPackageCrossEnabled() == null || rule.getPackageCrossEnabled() == 0
                        || "DISABLED".equals(rule.getPackageCrossMode())) {
                    return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "套餐/项目禁止跨店核销");
                }
            }
            if ("BALANCE".equals(redemptionType)) {
                if (rule.getBalanceCrossEnabled() == null || rule.getBalanceCrossEnabled() == 0
                        || "DISABLED".equals(rule.getBalanceCrossMode())) {
                    return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "储值禁止跨店使用");
                }
            }
            if ("POINTS".equals(redemptionType) && (rule.getPointsCrossEnabled() == null
                    || rule.getPointsCrossEnabled() == 0)) {
                return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "积分禁止跨店使用");
            }
            if ("COUPON".equals(redemptionType) && (rule.getCouponCrossEnabled() == null
                    || rule.getCouponCrossEnabled() == 0)) {
                return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "优惠券禁止跨店使用");
            }
            if ("DISCOUNT".equals(redemptionType) && (rule.getDiscountCrossEnabled() == null
                    || rule.getDiscountCrossEnabled() == 0)) {
                return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN, "折扣禁止跨店使用");
            }

            if ("PERCENT".equals(rule.getCrossFeeType()) && rule.getCrossFeeRate() != null
                    && rule.getCrossFeeRate().signum() > 0) {
                crossFee = originalAmount.multiply(rule.getCrossFeeRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            if (rule.getCrossMaxAmount() != null && rule.getCrossMaxAmount().signum() > 0
                    && originalAmount.compareTo(rule.getCrossMaxAmount()) > 0) {
                return Result.error(com.meiyetec.exception.BizCode.FORBIDDEN,
                        "超出跨店单笔限额：" + rule.getCrossMaxAmount());
            }
            netAmount = originalAmount.subtract(crossFee);
        }

        CrmCrossStoreRedemption r = new CrmCrossStoreRedemption();
        r.setTenantId(TenantContextHolder.get().getTenantId());
        r.setBrandId(brandId);
        r.setOrderId(orderId);
        r.setOrderNo(orderNo);
        r.setMemberId(memberId);
        r.setMemberName((String) member.get("name"));
        r.setMemberPhone((String) member.get("phone"));
        r.setSourceStoreId(sourceStoreId);
        r.setTargetStoreId(currentStoreId);
        r.setRedemptionType(redemptionType);
        r.setSourceId(sourceId);
        r.setSourceName(sourceName);
        r.setOriginalAmount(originalAmount);
        r.setCrossFee(crossFee);
        r.setNetAmount(netAmount);
        r.setStatus(1);
        r.setRuleId(ruleId);
        r.setRuleSnapshot(ruleSnapshot);
        r.setOperatorName(username());
        r.setRemark((String) body.get("remark"));
        redemptionMapper.insert(r);

        // 同步 crm_member_store 记录（如果是新门店）
        if (isCross) {
            jdbc.update(
                "INSERT INTO crm_member_store (tenant_id, member_id, store_id, brand_id, " +
                "  relation_type, bind_type, first_active_at, last_active_at, visit_count, status, deleted, create_time) " +
                "VALUES (?, ?, ?, ?, 'CROSS', 'PHONE', NOW(), NOW(), 1, 1, 0, NOW()) " +
                "ON DUPLICATE KEY UPDATE last_active_at = NOW(), visit_count = visit_count + 1",
                r.getTenantId(), memberId, currentStoreId, brandId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", r.getId());
        data.put("isCross", isCross);
        data.put("originalAmount", originalAmount);
        data.put("crossFee", crossFee);
        data.put("netAmount", netAmount);
        data.put("ruleId", ruleId);
        return Result.success(isCross ? "跨店核销已记录" : "已记录", data);
    }

    /**
     * 会员-门店关联查询
     */
    @GetMapping("/member-store")
    public Result<List<Map<String, Object>>> memberStores(@RequestParam(required = false) Long memberId,
                                                          @RequestParam(required = false) Long storeId) {
        StringBuilder sql = new StringBuilder(
            "SELECT ms.id, ms.member_id, m.member_no, m.name AS member_name, m.phone, " +
            "       ms.store_id, s.store_name, ms.brand_id, b.brand_name, " +
            "       ms.relation_type, ms.bind_type, ms.visit_count, " +
            "       ms.first_active_at, ms.last_active_at, ms.status " +
            "FROM crm_member_store ms " +
            "JOIN crm_member m ON m.id = ms.member_id " +
            "JOIN pos_store s ON s.id = ms.store_id " +
            "LEFT JOIN mkt_brand b ON b.id = ms.brand_id " +
            "WHERE ms.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (memberId != null) { sql.append("AND ms.member_id = ? "); args.add(memberId); }
        if (storeId != null) { sql.append("AND ms.store_id = ? "); args.add(storeId); }
        sql.append("ORDER BY ms.last_active_at DESC");
        return Result.success(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // ====================================================================
    // 6. 跨店规则匹配引擎（供主链路调用，无 HTTP 入口）
    // ====================================================================

    /**
     * 跨店规则匹配：brand_id 优先 > 租户通用
     */
    private CrmCrossStoreRule matchRule(Long tenantId, Map<String, Object> member) {
        Long memberId = ((Number) member.get("id")).longValue();
        Long memberBrandId = null;
        try {
            memberBrandId = jdbc.queryForObject(
                "SELECT s.brand_id FROM crm_member m " +
                "LEFT JOIN pos_store s ON s.id = m.store_id WHERE m.id = ?",
                Long.class, memberId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            memberBrandId = null;
        }

        // brand 级优先
        List<CrmCrossStoreRule> brandRule = ruleMapper.selectList(new LambdaQueryWrapper<CrmCrossStoreRule>()
                .eq(CrmCrossStoreRule::getTenantId, tenantId)
                .eq(CrmCrossStoreRule::getBrandId, memberBrandId)
                .eq(CrmCrossStoreRule::getEnabled, 1)
                .orderByDesc(CrmCrossStoreRule::getPriority)
                .last("LIMIT 1"));
        if (!brandRule.isEmpty()) return brandRule.get(0);

        // 兜底：租户级通用
        List<CrmCrossStoreRule> tenantRule = ruleMapper.selectList(new LambdaQueryWrapper<CrmCrossStoreRule>()
                .eq(CrmCrossStoreRule::getTenantId, tenantId)
                .isNull(CrmCrossStoreRule::getBrandId)
                .eq(CrmCrossStoreRule::getEnabled, 1)
                .orderByDesc(CrmCrossStoreRule::getPriority)
                .last("LIMIT 1"));
        return tenantRule.isEmpty() ? null : tenantRule.get(0);
    }

    private Map<String, Object> buildRulePreview(CrmCrossStoreRule rule) {
        Map<String, Object> preview = new HashMap<>();
        if (rule == null) {
            preview.put("packageAllowed", false);
            preview.put("pointsAllowed", false);
            preview.put("balanceAllowed", false);
            preview.put("couponAllowed", false);
            preview.put("discountAllowed", false);
            preview.put("message", "无规则，禁止跨店");
            return preview;
        }
        preview.put("packageAllowed", rule.getPackageCrossEnabled() != null && rule.getPackageCrossEnabled() == 1
                && !"DISABLED".equals(rule.getPackageCrossMode()));
        preview.put("pointsAllowed", rule.getPointsCrossEnabled() != null && rule.getPointsCrossEnabled() == 1);
        preview.put("balanceAllowed", rule.getBalanceCrossEnabled() != null && rule.getBalanceCrossEnabled() == 1
                && !"DISABLED".equals(rule.getBalanceCrossMode()));
        preview.put("couponAllowed", rule.getCouponCrossEnabled() != null && rule.getCouponCrossEnabled() == 1);
        preview.put("discountAllowed", rule.getDiscountCrossEnabled() != null && rule.getDiscountCrossEnabled() == 1);
        preview.put("crossFeeRate", rule.getCrossFeeRate());
        preview.put("crossFeeType", rule.getCrossFeeType());
        preview.put("crossMaxAmount", rule.getCrossMaxAmount());
        return preview;
    }

    /**
     * 跨店检查结果（被 PackageController.doRedeem 等调用）
     */
    public static class CrossStoreCheckResult {
        public boolean isCross = false;
        public boolean allowed = true;
        public BigDecimal crossFee = BigDecimal.ZERO;
        public Long ruleId = null;
        public String message = null;

        public static CrossStoreCheckResult ok() {
            return new CrossStoreCheckResult();
        }

        public static CrossStoreCheckResult reject(String msg) {
            CrossStoreCheckResult r = new CrossStoreCheckResult();
            r.allowed = false;
            r.message = msg;
            return r;
        }

        public static CrossStoreCheckResult cross(Long ruleId, BigDecimal fee) {
            CrossStoreCheckResult r = new CrossStoreCheckResult();
            r.isCross = true;
            r.allowed = true;
            r.ruleId = ruleId;
            r.crossFee = fee;
            return r;
        }
    }

    /**
     * 跨店核销前置校验（被 PackageController.doRedeem / WalletController.doConsume 等调用）
     */
    public CrossStoreCheckResult checkCrossStoreAndQuote(Long memberId, Long sourceStoreId,
                                                         String redemptionType, BigDecimal amount) {
        Long currentStoreId = TenantContextHolder.get().getStoreId();
        if (currentStoreId == null) {
            return CrossStoreCheckResult.ok();
        }
        if (sourceStoreId == null || sourceStoreId.equals(currentStoreId)) {
            return CrossStoreCheckResult.ok();
        }

        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name, phone, store_id FROM crm_member WHERE id = ? AND deleted = 0",
            memberId).stream().findFirst().orElse(null);
        if (member == null) {
            return CrossStoreCheckResult.reject("会员不存在");
        }

        CrmCrossStoreRule rule = matchRule(tenantId, member);
        if (rule == null) {
            return CrossStoreCheckResult.reject("无跨店规则，禁止跨店核销");
        }

        if ("PACKAGE".equals(redemptionType) || "PROJECT".equals(redemptionType)) {
            if (rule.getPackageCrossEnabled() == null || rule.getPackageCrossEnabled() == 0
                    || "DISABLED".equals(rule.getPackageCrossMode())) {
                return CrossStoreCheckResult.reject("套餐/项目禁止跨店核销");
            }
        }
        if ("BALANCE".equals(redemptionType)) {
            if (rule.getBalanceCrossEnabled() == null || rule.getBalanceCrossEnabled() == 0
                    || "DISABLED".equals(rule.getBalanceCrossMode())) {
                return CrossStoreCheckResult.reject("储值禁止跨店使用");
            }
        }

        BigDecimal crossFee = BigDecimal.ZERO;
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        if ("PERCENT".equals(rule.getCrossFeeType()) && rule.getCrossFeeRate() != null
                && rule.getCrossFeeRate().signum() > 0) {
            crossFee = amt.multiply(rule.getCrossFeeRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (rule.getCrossMaxAmount() != null && rule.getCrossMaxAmount().signum() > 0
                && amt.compareTo(rule.getCrossMaxAmount()) > 0) {
            return CrossStoreCheckResult.reject("超出跨店单笔限额：" + rule.getCrossMaxAmount());
        }

        log.info("[crossStoreCheck] memberId={} sourceStoreId={} currentStoreId={} ruleId={} fee={}",
                memberId, sourceStoreId, currentStoreId, rule.getId(), crossFee);
        return CrossStoreCheckResult.cross(rule.getId(), crossFee);
    }
}