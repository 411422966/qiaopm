package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CardPackageTemplate;
import com.meiyetec.entity.PriceList;
import com.meiyetec.entity.PriceListItem;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CardPackageTemplateMapper;
import com.meiyetec.mapper.PriceListItemMapper;
import com.meiyetec.mapper.PriceListMapper;
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
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P4.3 电子价目表 + 卡项套餐可视化
 *
 * 功能：
 * 1. 价目表管理（多版本/按门店/按会员等级）
 * 2. 价目表项编辑（拖拽排序、标签、卖点）
 * 3. 卡项套餐模板（封面/主题色/包含内容）
 * 4. 浏览统计与热销排行
 * 5. 价格对比工具
 */
@Slf4j
@RestController
@RequestMapping("/intel/pricelist")
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListMapper priceListMapper;
    private final PriceListItemMapper itemMapper;
    private final CardPackageTemplateMapper templateMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T1. 价目表管理
    // ====================================================================

    @PostMapping("/list")
    @Transactional(rollbackFor = Exception.class)
    public Result<PriceList> createList(@RequestBody PriceList list) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (list.getListCode() == null || list.getListName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (list.getListType() == null) list.setListType("STANDARD");
        if (list.getStatus() == null) list.setStatus("DRAFT");
        if (list.getVersion() == null) list.setVersion(1);
        list.setTenantId(tenantId);
        list.setId(null);

        // 默认价目表唯一性
        if (Integer.valueOf(1).equals(list.getIsDefault())) {
            jdbc.update("UPDATE price_list SET is_default = 0 WHERE tenant_id = ?", tenantId);
        }
        priceListMapper.insert(list);
        return Result.success("已创建", list);
    }

    @GetMapping("/list")
    public Result<List<PriceList>> listLists() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(priceListMapper.selectList(new LambdaQueryWrapper<PriceList>()
                .eq(PriceList::getTenantId, tenantId)
                .orderByDesc(PriceList::getIsDefault)
                .orderByDesc(PriceList::getId)));
    }

    @PostMapping("/list/{id}/activate")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> activate(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PriceList pl = priceListMapper.selectById(id);
        if (pl == null || !pl.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "价目表不存在");
        // 同租户只能有一个 ACTIVE
        jdbc.update("UPDATE price_list SET status = 'ARCHIVED' WHERE tenant_id = ? AND id != ? AND status = 'ACTIVE'", tenantId, id);
        pl.setStatus("ACTIVE");
        priceListMapper.updateById(pl);
        return Result.success("已激活", null);
    }

    @PostMapping("/list/{id}/set-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setDefault(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PriceList pl = priceListMapper.selectById(id);
        if (pl == null || !pl.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "价目表不存在");
        jdbc.update("UPDATE price_list SET is_default = 0 WHERE tenant_id = ?", tenantId);
        pl.setIsDefault(1);
        priceListMapper.updateById(pl);
        return Result.success("已设为默认", null);
    }

    // ====================================================================
    // T2. 价目表项（商品/服务）
    // ====================================================================

    @PostMapping("/item")
    public Result<PriceListItem> addItem(@RequestBody PriceListItem item) {
        if (item.getPriceListId() == null)
            return Result.error(BizCode.BAD_REQUEST, "priceListId 必填");
        if (item.getListPrice() == null) item.setListPrice(BigDecimal.ZERO);
        item.setId(null);
        if (item.getDiscountRate() == null) item.setDiscountRate(BigDecimal.ZERO);
        itemMapper.insert(item);
        return Result.success("已添加", item);
    }

    @PostMapping("/item/batch")
    public Result<Map<String, Object>> batchAddItems(@RequestBody Map<String, Object> body) {
        Long priceListId = body.get("priceListId") == null ? null
                : ((Number) body.get("priceListId")).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (priceListId == null || items == null)
            return Result.error(BizCode.BAD_REQUEST, "priceListId/items 必填");

        int success = 0, fail = 0;
        for (Map<String, Object> m : items) {
            try {
                PriceListItem item = new PriceListItem();
                item.setPriceListId(priceListId);
                item.setServiceId(m.get("serviceId") == null ? null : ((Number) m.get("serviceId")).longValue());
                item.setCategoryId(m.get("categoryId") == null ? null : ((Number) m.get("categoryId")).longValue());
                item.setListPrice(toBD(m.get("listPrice")));
                item.setMemberPrice(toBD(m.get("memberPrice")));
                item.setVipPrice(toBD(m.get("vipPrice")));
                item.setDiscountRate(toBD(m.get("discountRate")));
                item.setImageUrl((String) m.get("imageUrl"));
                item.setTag((String) m.get("tag"));
                item.setSortOrder(m.get("sortOrder") == null ? 0 : ((Number) m.get("sortOrder")).intValue());
                item.setIsFeatured(m.get("isFeatured") == null ? 0 : ((Number) m.get("isFeatured")).intValue());
                item.setRemark((String) m.get("remark"));
                itemMapper.insert(item);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", success);
        data.put("fail", fail);
        return Result.success("导入完成", data);
    }

    @GetMapping("/item/by-list/{priceListId}")
    public Result<List<PriceListItem>> itemsByList(@PathVariable Long priceListId) {
        return Result.success(itemMapper.selectList(new LambdaQueryWrapper<PriceListItem>()
                .eq(PriceListItem::getPriceListId, priceListId)
                .orderByDesc(PriceListItem::getIsFeatured)
                .orderByAsc(PriceListItem::getSortOrder)
                .orderByAsc(PriceListItem::getId)));
    }

    @PostMapping("/item/{id}/delete")
    public Result<Void> deleteItem(@PathVariable Long id) {
        itemMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    @PostMapping("/item/reorder")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reorder(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) body.get("orders");
        if (orders == null) return Result.error(BizCode.BAD_REQUEST, "orders 必填");
        for (Map<String, Object> m : orders) {
            Long id = ((Number) m.get("id")).longValue();
            Integer sort = ((Number) m.get("sortOrder")).intValue();
            jdbc.update("UPDATE price_list_item SET sort_order = ? WHERE id = ?", sort, id);
        }
        return Result.success("已排序", null);
    }

    // ====================================================================
    // T3. 价格对比工具（同一服务在不同价目表中的价格）
    // ====================================================================

    @PostMapping("/compare")
    public Result<List<Map<String, Object>>> compare(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        @SuppressWarnings("unchecked")
        List<Number> serviceIds = (List<Number>) body.get("serviceIds");
        if (serviceIds == null || serviceIds.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "serviceIds 必填");

        String ids = serviceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = """
            SELECT pl.id AS price_list_id, pl.list_name, pl.list_type,
                   pli.service_id, s.service_name AS service_name,
                   pli.list_price, pli.member_price, pli.vip_price, pli.discount_rate
            FROM price_list pl
            JOIN price_list_item pli ON pli.price_list_id = pl.id
            LEFT JOIN biz_service_item s ON s.id = pli.service_id
            WHERE pl.deleted = 0 AND pl.tenant_id = ? AND pl.status = 'ACTIVE'
              AND pli.service_id IN (%s)
            ORDER BY pl.id DESC, pli.service_id, pli.sort_order
            """.formatted(ids);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId);
        return Result.success(rows);
    }

    // ====================================================================
    // T4. 热销统计
    // ====================================================================

    @GetMapping("/hot-items")
    public Result<List<Map<String, Object>>> hotItems(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id, s.service_name AS name, s.member_price AS price,
                       c.category_name AS category,
                       COALESCE(SUM(CASE WHEN o.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                                     THEN 1 ELSE 0 END), 0) AS recent_sales,
                       COALESCE(COUNT(o.id), 0) AS total_sales
                FROM biz_service_item s
                LEFT JOIN biz_service_category c ON c.id = s.category_id
                LEFT JOIN crm_order_item oi ON oi.service_id = s.id
                LEFT JOIN crm_order o ON o.id = oi.order_id AND o.status = 3 AND o.deleted = 0
                WHERE s.deleted = 0 AND s.tenant_id = ? AND s.status = 1
                GROUP BY s.id, s.service_name, s.member_price, c.category_name
                ORDER BY recent_sales DESC, total_sales DESC
                LIMIT ?
                """, tenantId, limit);
            return Result.success(rows);
        } catch (Exception e) {
            log.warn("热销降级: {}", e.getMessage());
            return Result.success(new ArrayList<>());
        }
    }

    // ====================================================================
    // T5. 卡项套餐模板管理
    // ====================================================================

    @PostMapping("/template")
    @Transactional(rollbackFor = Exception.class)
    public Result<CardPackageTemplate> createTemplate(@RequestBody CardPackageTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getTemplateCode() == null || t.getTemplateName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (t.getPackageType() == null) t.setPackageType("TIMES");
        if (t.getStatus() == null) t.setStatus("ACTIVE");
        if (t.getThemeColor() == null) t.setThemeColor("#FF6B35");
        if (t.getDiscountRate() == null && t.getOriginalPrice() != null && t.getPackagePrice() != null
                && t.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = BigDecimal.ONE.subtract(
                    t.getPackagePrice().divide(t.getOriginalPrice(), 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            t.setDiscountRate(rate);
        }
        t.setTenantId(tenantId);
        t.setId(null);
        t.setSoldCount(0);
        t.setViewCount(0);
        templateMapper.insert(t);
        return Result.success("已创建", t);
    }

    @PostMapping("/template/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<CardPackageTemplate> updateTemplate(@PathVariable Long id, @RequestBody CardPackageTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CardPackageTemplate exist = templateMapper.selectById(id);
        if (exist == null || !exist.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "模板不存在");
        t.setId(id);
        t.setTenantId(tenantId);
        templateMapper.updateById(t);
        return Result.success("已更新", templateMapper.selectById(id));
    }

    @GetMapping("/template/list")
    public Result<List<CardPackageTemplate>> listTemplates() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(templateMapper.selectList(new LambdaQueryWrapper<CardPackageTemplate>()
                .eq(CardPackageTemplate::getTenantId, tenantId)
                .orderByDesc(CardPackageTemplate::getSoldCount)
                .orderByDesc(CardPackageTemplate::getId)));
    }

    @GetMapping("/template/{id}")
    public Result<CardPackageTemplate> getTemplate(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CardPackageTemplate t = templateMapper.selectById(id);
        if (t == null || !t.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "模板不存在");
        // 自增 view_count
        jdbc.update("UPDATE card_package_template SET view_count = view_count + 1 WHERE id = ?", id);
        return Result.success(t);
    }

    @PostMapping("/template/{id}/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CardPackageTemplate t = templateMapper.selectById(id);
        if (t == null || !t.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "模板不存在");
        t.setStatus("ARCHIVED");
        templateMapper.updateById(t);
        return Result.success("已归档", null);
    }

    @PostMapping("/template/{id}/duplicate")
    @Transactional(rollbackFor = Exception.class)
    public Result<CardPackageTemplate> duplicate(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CardPackageTemplate src = templateMapper.selectById(id);
        if (src == null || !src.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "模板不存在");
        CardPackageTemplate dup = new CardPackageTemplate();
        dup.setTenantId(tenantId);
        dup.setTemplateCode(src.getTemplateCode() + "_COPY_" + System.currentTimeMillis() / 1000);
        dup.setTemplateName(src.getTemplateName() + " (副本)");
        dup.setPackageType(src.getPackageType());
        dup.setCardLevel(src.getCardLevel());
        dup.setTotalSessions(src.getTotalSessions());
        dup.setTotalAmount(src.getTotalAmount());
        dup.setOriginalPrice(src.getOriginalPrice());
        dup.setPackagePrice(src.getPackagePrice());
        dup.setDiscountRate(src.getDiscountRate());
        dup.setCoverImage(src.getCoverImage());
        dup.setThemeColor(src.getThemeColor());
        dup.setAccentColor(src.getAccentColor());
        dup.setDescription(src.getDescription());
        dup.setHighlights(src.getHighlights());
        dup.setTag(src.getTag());
        dup.setStoreIds(src.getStoreIds());
        dup.setIncludedServices(src.getIncludedServices());
        dup.setIncludedProducts(src.getIncludedProducts());
        dup.setStatus("ACTIVE");
        dup.setSoldCount(0);
        dup.setViewCount(0);
        templateMapper.insert(dup);
        return Result.success("已复制", dup);
    }

    // ====================================================================
    // T6. 浏览/购买统计（真实更新 sold_count）
    // ====================================================================

    @PostMapping("/template/{id}/mark-sold")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markSold(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Integer count = body == null || body.get("count") == null ? 1
                : ((Number) body.get("count")).intValue();
        jdbc.update("UPDATE card_package_template SET sold_count = sold_count + ? WHERE id = ?", count, id);
        return Result.success("已记录", null);
    }

    @GetMapping("/template/top")
    public Result<List<CardPackageTemplate>> topTemplates(
            @RequestParam(defaultValue = "5") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(templateMapper.selectList(new LambdaQueryWrapper<CardPackageTemplate>()
                .eq(CardPackageTemplate::getTenantId, tenantId)
                .eq(CardPackageTemplate::getStatus, "ACTIVE")
                .orderByDesc(CardPackageTemplate::getSoldCount)
                .last("LIMIT " + limit)));
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}