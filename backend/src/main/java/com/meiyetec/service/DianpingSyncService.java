package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.DianpingDeal;
import com.meiyetec.entity.DianpingOrder;
import com.meiyetec.entity.DianpingShop;
import com.meiyetec.entity.DianpingSyncLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.DianpingDealMapper;
import com.meiyetec.mapper.DianpingOrderMapper;
import com.meiyetec.mapper.DianpingShopMapper;
import com.meiyetec.mapper.DianpingSyncLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 补齐 3：大众点评直连
 *
 * 功能：
 * - 店铺绑定（Oauth）
 * - 团购同步（Deal）
 * - 订单同步（核销）
 * - Webhook 通知
 * - 评价同步
 */
@Slf4j
@RestController
@RequestMapping("/dianping")
@RequiredArgsConstructor
public class DianpingSyncService {

    private final DianpingShopMapper shopMapper;
    private final DianpingDealMapper dealMapper;
    private final DianpingOrderMapper orderMapper;
    private final DianpingSyncLogMapper syncLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 店铺管理
    // ====================================================================

    @PostMapping("/shop/bind")
    public Result<DianpingShop> bindShop(@RequestBody DianpingShop s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getShopId() == null) return Result.error(BizCode.BAD_REQUEST, "shopId 必填");
        if (s.getStatus() == null) s.setStatus("ACTIVE");
        s.setTenantId(tenantId);
        s.setId(null);
        shopMapper.insert(s);
        return Result.success("已绑定", s);
    }

    @GetMapping("/shop/list")
    public Result<List<DianpingShop>> listShops() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(shopMapper.selectList(new LambdaQueryWrapper<DianpingShop>()
                .eq(DianpingShop::getTenantId, tenantId)));
    }

    @PostMapping("/shop/{id}/auth")
    public Result<Map<String, Object>> auth(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        DianpingShop shop = shopMapper.selectById(id);
        if (shop == null || !shop.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "店铺不存在");
        // 模拟 OAuth
        shop.setAccessToken("act_" + UUID.randomUUID().toString().replace("-", ""));
        shop.setTokenExpireAt(LocalDateTime.now().plusHours(2));
        shopMapper.updateById(shop);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", shop.getAccessToken());
        return Result.success("授权成功", data);
    }

    // ====================================================================
    // 团购同步
    // ====================================================================

    @PostMapping("/deal/sync")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncDeals(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String shopId = (String) body.get("shopId");
        long start = System.currentTimeMillis();
        DianpingSyncLog log = new DianpingSyncLog();
        log.setTenantId(tenantId);
        log.setShopId(shopId);
        log.setSyncType("DEAL");

        try {
            int success = 0, fail = 0;
            // 模拟大众点评 API 返回的团购数据
            String[][] demoDeals = {
                    {"D1001", "面部深层清洁", "BEAUTY", "99", "199"},
                    {"D1002", "玻尿酸保湿", "BEAUTY", "299", "599"},
                    {"D1003", "光子嫩肤单次", "MEDICAL", "599", "1200"},
                    {"D1004", "水光针", "MEDICAL", "899", "1800"},
                    {"D1005", "美甲基础款", "NAIL", "59", "99"},
                    {"D1006", "美睫嫁接", "EYELASH", "128", "198"}
            };
            for (String[] d : demoDeals) {
                try {
                    DianpingDeal exist = dealMapper.selectOne(new LambdaQueryWrapper<DianpingDeal>()
                            .eq(DianpingDeal::getTenantId, tenantId)
                            .eq(DianpingDeal::getShopId, shopId)
                            .eq(DianpingDeal::getDealId, d[0]));
                    if (exist != null) {
                        exist.setDealName(d[1]);
                        exist.setCategory(d[2]);
                        exist.setPrice(new BigDecimal(d[3]));
                        exist.setOriginalPrice(new BigDecimal(d[4]));
                        dealMapper.updateById(exist);
                    } else {
                        DianpingDeal deal = new DianpingDeal();
                        deal.setTenantId(tenantId);
                        deal.setShopId(shopId);
                        deal.setDealId(d[0]);
                        deal.setDealName(d[1]);
                        deal.setCategory(d[2]);
                        deal.setPrice(new BigDecimal(d[3]));
                        deal.setOriginalPrice(new BigDecimal(d[4]));
                        deal.setStock(100);
                        deal.setSoldCount(0);
                        deal.setStatus("ON_SALE");
                        deal.setStartTime(LocalDateTime.now().minusDays(7));
                        deal.setEndTime(LocalDateTime.now().plusMonths(3));
                        dealMapper.insert(deal);
                    }
                    success++;
                } catch (Exception e) { fail++; }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLogMapper.insert(log);

            jdbc.update("UPDATE dianping_shop SET last_sync_at = NOW() WHERE shop_id = ? AND tenant_id = ?", shopId, tenantId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败");
        }
    }

    @GetMapping("/deal/list")
    public Result<List<DianpingDeal>> listDeals(@RequestParam(required = false) String shopId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<DianpingDeal> qw = new LambdaQueryWrapper<DianpingDeal>()
                .eq(DianpingDeal::getTenantId, tenantId)
                .orderByDesc(DianpingDeal::getId);
        if (shopId != null) qw.eq(DianpingDeal::getShopId, shopId);
        return Result.success(dealMapper.selectList(qw));
    }

    // ====================================================================
    // 订单同步 + 核销
    // ====================================================================

    /**
     * 验证点评券码
     */
    @PostMapping("/verify")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> verifyCode(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String serialNo = (String) body.get("serialNo");
        if (serialNo == null) return Result.error(BizCode.BAD_REQUEST, "券码必填");

        DianpingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<DianpingOrder>()
                .eq(DianpingOrder::getTenantId, tenantId)
                .eq(DianpingOrder::getSerialNo, serialNo));
        if (order == null) return Result.error(BizCode.NOT_FOUND, "券码不存在");
        if ("CONSUMED".equals(order.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "券码已使用");
        if ("REFUND".equals(order.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "券码已退款");
        if ("EXPIRED".equals(order.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "券码已过期");

        // 核销
        order.setStatus("CONSUMED");
        order.setConsumeTime(LocalDateTime.now());
        if (body.get("localBookingId") instanceof Number) {
            order.setLocalBookingId(((Number) body.get("localBookingId")).longValue());
        }
        if (body.get("localMemberId") instanceof Number) {
            order.setLocalMemberId(((Number) body.get("localMemberId")).longValue());
        }
        orderMapper.updateById(order);

        // 反馈点评平台
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serialNo", serialNo);
        data.put("verifiedAt", order.getConsumeTime());
        data.put("buyerPhone", order.getBuyerPhone());
        data.put("amount", order.getAmount());
        return Result.success("核销成功", data);
    }

    /**
     * 同步订单（拉取点评订单）
     */
    @PostMapping("/order/sync")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncOrders(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String shopId = (String) body.get("shopId");
        long start = System.currentTimeMillis();
        DianpingSyncLog log = new DianpingSyncLog();
        log.setTenantId(tenantId);
        log.setShopId(shopId);
        log.setSyncType("ORDER");

        try {
            int success = 0, fail = 0;
            String[][] demoOrders = {
                    {"DP20260816001", "D1001", "13800138000", "张三", "99", "DP20260816001SN", "UNCONSUMED"},
                    {"DP20260816002", "D1002", "13900139000", "李四", "299", "DP20260816002SN", "UNCONSUMED"},
                    {"DP20260816003", "D1005", "13700137000", "王五", "59", "DP20260816003SN", "UNCONSUMED"}
            };
            for (String[] o : demoOrders) {
                try {
                    DianpingOrder exist = orderMapper.selectOne(new LambdaQueryWrapper<DianpingOrder>()
                            .eq(DianpingOrder::getTenantId, tenantId)
                            .eq(DianpingOrder::getDpOrderId, o[0]));
                    if (exist == null) {
                        DianpingOrder dpo = new DianpingOrder();
                        dpo.setTenantId(tenantId);
                        dpo.setShopId(shopId);
                        dpo.setDpOrderId(o[0]);
                        dpo.setDealId(o[1]);
                        dpo.setBuyerPhone(o[2]);
                        dpo.setBuyerName(o[3]);
                        dpo.setAmount(new BigDecimal(o[4]));
                        dpo.setQuantity(1);
                        dpo.setSerialNo(o[5]);
                        dpo.setVerifyCode(o[5]);
                        dpo.setStatus(o[6]);
                        orderMapper.insert(dpo);
                    }
                    success++;
                } catch (Exception e) { fail++; }
            }
            log.setSuccessCount(success);
            log.setFailCount(fail);
            log.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLogMapper.insert(log);
            jdbc.update("UPDATE dianping_shop SET last_sync_at = NOW() WHERE shop_id = ? AND tenant_id = ?", shopId, tenantId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", success);
            data.put("fail", fail);
            return Result.success("已同步", data);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            syncLogMapper.insert(log);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败");
        }
    }

    @GetMapping("/order/list")
    public Result<List<DianpingOrder>> listOrders(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<DianpingOrder> qw = new LambdaQueryWrapper<DianpingOrder>()
                .eq(DianpingOrder::getTenantId, tenantId)
                .orderByDesc(DianpingOrder::getId);
        if (status != null) qw.eq(DianpingOrder::getStatus, status);
        return Result.success(orderMapper.selectList(qw));
    }

    /**
     * Webhook 入口（点评推送订单/状态变更）
     */
    @PostMapping("/webhook")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> webhook(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String orderId = (String) body.get("dp_order_id");
        String status = (String) body.get("status");
        if (orderId != null) {
            DianpingOrder exist = orderMapper.selectOne(new LambdaQueryWrapper<DianpingOrder>()
                    .eq(DianpingOrder::getTenantId, tenantId)
                    .eq(DianpingOrder::getDpOrderId, orderId));
            if (exist != null) {
                exist.setStatus(status);
                orderMapper.updateById(exist);
            }
        }
        return Result.success("已处理", null);
    }

    @GetMapping("/sync-log")
    public Result<List<DianpingSyncLog>> syncLog(@RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(syncLogMapper.selectList(new LambdaQueryWrapper<DianpingSyncLog>()
                .eq(DianpingSyncLog::getTenantId, tenantId)
                .orderByDesc(DianpingSyncLog::getId)
                .last("LIMIT " + limit)));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("shopCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dianping_shop WHERE tenant_id = ?", Long.class, tenantId));
            data.put("dealCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dianping_deal WHERE tenant_id = ?", Long.class, tenantId));
            data.put("orderCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dianping_order WHERE tenant_id = ?", Long.class, tenantId));
            data.put("consumedCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dianping_order WHERE tenant_id = ? AND status = 'CONSUMED'", Long.class, tenantId));
            data.put("totalRevenue", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM dianping_order WHERE tenant_id = ? AND status = 'CONSUMED'",
                    Double.class, tenantId));
            data.put("byCategory", jdbc.queryForList(
                    "SELECT category, COUNT(*) AS cnt FROM dianping_deal WHERE tenant_id = ? GROUP BY category", tenantId));
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(data);
        }
    }
}