package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.PointGoods;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PointGoodsMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PointService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.meiyetec.util.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分商城 controller（/market/point/goods/*）
 */
@RestController
@RequestMapping("/market/point/goods")
@RequiredArgsConstructor
@SysOpLog(module = "积分商城")
public class PointGoodsController {

    private final PointGoodsMapper goodsMapper;
    private final PointService pointService;
    private final JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/page")
    public Result<PageResult<PointGoods>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<PointGoods> w = new LambdaQueryWrapper<>();
        w.eq(PointGoods::getTenantId, t);
        if (status != null) w.eq(PointGoods::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) w.like(PointGoods::getName, keyword);
        w.orderByDesc(PointGoods::getId);
        Page<PointGoods> page = goodsMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/list")
    public Result<List<PointGoods>> list(@RequestParam(required = false) String keyword) {
        Long t = tid();
        LambdaQueryWrapper<PointGoods> w = new LambdaQueryWrapper<>();
        w.eq(PointGoods::getTenantId, t).eq(PointGoods::getStatus, 1);
        if (keyword != null && !keyword.isEmpty()) w.like(PointGoods::getName, keyword);
        w.orderByDesc(PointGoods::getId).last("LIMIT 100");
        return Result.success(goodsMapper.selectList(w));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<PointGoods> create(@RequestBody PointGoods g) {
        Long t = tid();
        if (g.getName() == null || g.getName().isEmpty()) return Result.error(BizCode.BAD_REQUEST, "name 必填");
        if (g.getPointsPrice() == null || g.getPointsPrice() <= 0) return Result.error(BizCode.BAD_REQUEST, "pointsPrice 必填且 > 0");
        g.setId(null);
        g.setTenantId(t);
        if (g.getStoreId() == null) g.setStoreId(1L);
        if (g.getCategory() == null) g.setCategory("GENERAL");
        if (g.getStock() == null) g.setStock(0);
        if (g.getSoldCount() == null) g.setSoldCount(0);
        if (g.getCashPrice() == null) g.setCashPrice(java.math.BigDecimal.ZERO);
        if (g.getStatus() == null) g.setStatus(1);
        goodsMapper.insert(g);
        return Result.success("创建成功", g);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<PointGoods> update(@PathVariable Long id, @RequestBody PointGoods g) {
        Long t = tid();
        PointGoods old = goodsMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "商品不存在");
        if (g.getName() != null) old.setName(g.getName());
        if (g.getCategory() != null) old.setCategory(g.getCategory());
        if (g.getImageUrl() != null) old.setImageUrl(g.getImageUrl());
        if (g.getPointsPrice() != null) old.setPointsPrice(g.getPointsPrice());
        if (g.getCashPrice() != null) old.setCashPrice(g.getCashPrice());
        if (g.getStock() != null) old.setStock(g.getStock());
        if (g.getDescription() != null) old.setDescription(g.getDescription());
        if (g.getStatus() != null) old.setStatus(g.getStatus());
        goodsMapper.updateById(old);
        return Result.success("更新成功", old);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> remove(@PathVariable Long id) {
        Long t = tid();
        PointGoods old = goodsMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "商品不存在");
        goodsMapper.deleteById(id);
        return Result.success("下架成功", null);
    }

    /**
     * 兑换商品（POINTS-006 核心闭环）
     * - 校验库存 / 积分是否够 / 商品上架
     * - 扣减积分（带租户隔离）
     * - 扣减库存 + 增加 sold_count
     * - 写入 point_order
     */
    @PostMapping("/{id}/redeem")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> redeem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long t = tid();
        if (body == null || body.get("memberId") == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        Long memberId = ((Number) body.get("memberId")).longValue();

        PointGoods g = goodsMapper.selectById(id);
        if (g == null || !t.equals(g.getTenantId())) return Result.error(BizCode.NOT_FOUND, "商品不存在");
        if (g.getStatus() == null || g.getStatus() != 1) return Result.error(BizCode.CONFLICT, "商品已下架");
        if (g.getStock() != null && g.getStock() == 0) return Result.error(BizCode.CONFLICT, "库存不足");

        int pointsCost = g.getPointsPrice();
        pointService.consumePoints(memberId, "redeem", pointsCost, id, "积分兑换-" + g.getName());

        g.setSoldCount((g.getSoldCount() == null ? 0 : g.getSoldCount()) + 1);
        if (g.getStock() != null && g.getStock() > 0) g.setStock(g.getStock() - 1);
        goodsMapper.updateById(g);

        String orderNo = "PO" + System.currentTimeMillis() + Keys.randomCode(4);
        jdbc.update(
                "INSERT INTO point_order (tenant_id, store_id, order_no, member_id, goods_id, goods_name, points_cost, status, remark) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t, g.getStoreId(), orderNo, memberId, g.getId(), g.getName(), pointsCost, "PENDING",
                body.get("remark") == null ? "" : body.get("remark").toString());

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("pointsCost", pointsCost);
        return Result.success("兑换成功", result);
    }

    @GetMapping("/{id}")
    public Result<PointGoods> detail(@PathVariable Long id) {
        Long t = tid();
        PointGoods g = goodsMapper.selectById(id);
        if (g == null || !t.equals(g.getTenantId())) return Result.error(BizCode.NOT_FOUND, "商品不存在");
        return Result.success(g);
    }
}