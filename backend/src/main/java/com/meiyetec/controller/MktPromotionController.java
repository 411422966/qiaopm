package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktPromotion;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MktPromotionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销促销 controller（/mkt/promotion/*）
 *  - promotion.vue 实际使用的后端
 *  - inventory 把这个 controller 写成了 MKT-001~005（MarketActivityController），但 MarketActivity 与 MktPromotion 是两套数据模型
 *  - 此 controller 对应 mkt_promotion 表（含 tenant_id）
 */
@RestController
@RequestMapping("/mkt/promotion")
@RequiredArgsConstructor
@SysOpLog(module = "营销促销")
public class MktPromotionController {

    private final MktPromotionMapper promotionMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/page")
    @RequirePermission("mkt:promotion")
    public Result<PageResult<MktPromotion>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String promotionType,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MktPromotion> w = new LambdaQueryWrapper<>();
        w.eq(MktPromotion::getTenantId, t);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(MktPromotion::getPromotionName, keyword)
                    .or().like(MktPromotion::getPromotionCode, keyword));
        }
        if (promotionType != null && !promotionType.isEmpty()) w.eq(MktPromotion::getPromotionType, promotionType);
        if (status != null) w.eq(MktPromotion::getStatus, status);
        w.orderByDesc(MktPromotion::getPriority).orderByDesc(MktPromotion::getId);
        return Result.success(PageResult.of(promotionMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @GetMapping({"/", ""})
    @RequirePermission("mkt:promotion")
    public Result<List<MktPromotion>> list(@RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MktPromotion> w = new LambdaQueryWrapper<>();
        w.eq(MktPromotion::getTenantId, t).eq(MktPromotion::getStatus, status == null ? 1 : status);
        w.orderByDesc(MktPromotion::getPriority);
        return Result.success(promotionMapper.selectList(w));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:promotion")
    public Result<MktPromotion> create(@RequestBody MktPromotion p) {
        Long t = tid();
        if (p.getPromotionName() == null || p.getPromotionName().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "promotionName 必填");
        }
        if (p.getPromotionCode() == null) p.setPromotionCode("P" + System.currentTimeMillis());
        if (p.getPromotionType() == null) p.setPromotionType("FULL_REDUCE");
        if (p.getStoreId() == null) p.setStoreId(1L);
        p.setId(null);
        p.setTenantId(t);
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getQuotaUsed() == null) p.setQuotaUsed(0);
        if (p.getStackable() == null) p.setStackable(0);
        if (p.getScopeType() == null) p.setScopeType("ALL");
        promotionMapper.insert(p);
        return Result.success("创建成功", p);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:promotion")
    public Result<MktPromotion> update(@PathVariable Long id, @RequestBody MktPromotion p) {
        Long t = tid();
        MktPromotion old = promotionMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        if (p.getPromotionName() != null) old.setPromotionName(p.getPromotionName());
        if (p.getPromotionCode() != null) old.setPromotionCode(p.getPromotionCode());
        if (p.getPromotionType() != null) old.setPromotionType(p.getPromotionType());
        if (p.getDescription() != null) old.setDescription(p.getDescription());
        if (p.getStartTime() != null) old.setStartTime(p.getStartTime());
        if (p.getEndTime() != null) old.setEndTime(p.getEndTime());
        if (p.getThresholdAmount() != null) old.setThresholdAmount(p.getThresholdAmount());
        if (p.getReduceAmount() != null) old.setReduceAmount(p.getReduceAmount());
        if (p.getDiscountRate() != null) old.setDiscountRate(p.getDiscountRate());
        if (p.getScopeType() != null) old.setScopeType(p.getScopeType());
        if (p.getScopeIds() != null) old.setScopeIds(p.getScopeIds());
        if (p.getMemberLevelIds() != null) old.setMemberLevelIds(p.getMemberLevelIds());
        if (p.getStackable() != null) old.setStackable(p.getStackable());
        if (p.getExclusiveCodes() != null) old.setExclusiveCodes(p.getExclusiveCodes());
        if (p.getQuotaTotal() != null) old.setQuotaTotal(p.getQuotaTotal());
        if (p.getPerMemberLimit() != null) old.setPerMemberLimit(p.getPerMemberLimit());
        if (p.getStatus() != null) old.setStatus(p.getStatus());
        if (p.getPriority() != null) old.setPriority(p.getPriority());
        if (old.getEndTime() != null && old.getStartTime() != null && old.getEndTime().isBefore(old.getStartTime())) {
            return Result.error(BizCode.BAD_REQUEST, "endTime 不能早于 startTime");
        }
        promotionMapper.updateById(old);
        return Result.success("更新成功", old);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:promotion")
    public Result<Void> delete(@PathVariable Long id) {
        Long t = tid();
        MktPromotion old = promotionMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "活动不存在");
        promotionMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    /**
     * 导出 CSV
     */
    @GetMapping("/export")
    @RequirePermission("mkt:promotion")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String promotionType,
                                            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MktPromotion> w = new LambdaQueryWrapper<>();
        w.eq(MktPromotion::getTenantId, t);
        if (keyword != null && !keyword.isEmpty()) w.like(MktPromotion::getPromotionName, keyword);
        if (promotionType != null && !promotionType.isEmpty()) w.eq(MktPromotion::getPromotionType, promotionType);
        if (status != null) w.eq(MktPromotion::getStatus, status);
        w.orderByDesc(MktPromotion::getId);
        List<MktPromotion> rows = promotionMapper.selectList(w);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter w2 = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            // UTF-8 BOM 让 Excel 正确识别中文
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            w2.write("ID,编码,名称,类型,状态,优先级,创建时间\n");
            for (MktPromotion p : rows) {
                w2.write(csv(p.getId()) + "," + csv(p.getPromotionCode()) + "," + csv(p.getPromotionName())
                        + "," + csv(p.getPromotionType()) + "," + (p.getStatus() != null ? p.getStatus() : "")
                        + "," + (p.getPriority() != null ? p.getPriority() : "")
                        + "," + (p.getCreateTime() == null ? "" : p.getCreateTime().toString()) + "\n");
            }
            w2.flush();
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mkt_promotion.csv");
            headers.add(HttpHeaders.CONTENT_TYPE, "text/csv;charset=utf-8");
            return new ResponseEntity<>(baos.toByteArray(), headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, "导出失败");
        }
    }

    private String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}