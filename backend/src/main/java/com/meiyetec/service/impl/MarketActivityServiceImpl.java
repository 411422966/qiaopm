package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.MarketActivity;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MarketActivityMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketActivityServiceImpl implements MarketActivityService {

    @Autowired
    private MarketActivityMapper activityMapper;
    @Autowired
    private PosOrderMapper orderMapper;

    @Override
    public IPage<Map<String, Object>> activityPage(int pageNum, int pageSize, String name, Integer status) {
        Page<MarketActivity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MarketActivity> q = new LambdaQueryWrapper<>();
        // 租户隔离
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null) q.eq(MarketActivity::getTenantId, tid);
        if (name != null && !name.isBlank()) q.like(MarketActivity::getName, name);
        if (status != null) q.eq(MarketActivity::getStatus, status);
        q.orderByDesc(MarketActivity::getCreatedAt);

        IPage<MarketActivity> result = activityMapper.selectPage(page, q);

        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("type", a.getType());
            m.put("typeText", typeText(a.getType()));
            m.put("ruleConfig", a.getRuleConfig());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("status", a.getStatus());
            m.put("statusText", statusText(a.getStatus()));
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList());
        return mapPage;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        return getActivity(id);
    }

    @Override
    public Map<String, Object> stats() {
        Long tid = TenantContextHolder.getTenantIdOrNull();
        LambdaQueryWrapper<MarketActivity> q = new LambdaQueryWrapper<>();
        if (tid != null) q.eq(MarketActivity::getTenantId, tid);
        long total = activityMapper.selectCount(q);
        q.eq(MarketActivity::getStatus, 1);
        long active = activityMapper.selectCount(q);
        Map<String, Object> r = new HashMap<>();
        r.put("total", total);
        r.put("active", active);
        return r;
    }

    @Override
    public Map<String, Object> getActivity(Long id) {
        MarketActivity a = requireActivity(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("type", a.getType());
        m.put("ruleConfig", a.getRuleConfig());
        m.put("startTime", a.getStartTime());
        m.put("endTime", a.getEndTime());
        m.put("status", a.getStatus());
        return m;
    }

    @Override
    public void createActivity(Map<String, Object> params) {
        if (params.get("name") == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "活动名称不能为空");
        }
        MarketActivity a = new MarketActivity();
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null) a.setTenantId(tid);
        a.setName((String) params.get("name"));
        a.setType(Integer.parseInt(params.get("type").toString()));
        if (params.get("ruleConfig") != null) a.setRuleConfig(params.get("ruleConfig").toString());
        a.setStartTime(LocalDateTime.parse(params.get("startTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        a.setEndTime(LocalDateTime.parse(params.get("endTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        a.setStatus(1);
        activityMapper.insert(a);
    }

    @Override
    public void updateActivity(Long id, Map<String, Object> params) {
        MarketActivity a = requireActivity(id);
        if (params.get("name") != null) a.setName((String) params.get("name"));
        if (params.get("type") != null) a.setType(Integer.parseInt(params.get("type").toString()));
        if (params.get("ruleConfig") != null) a.setRuleConfig(params.get("ruleConfig").toString());
        if (params.get("startTime") != null) a.setStartTime(LocalDateTime.parse(params.get("startTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (params.get("endTime") != null) a.setEndTime(LocalDateTime.parse(params.get("endTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (params.get("status") != null) a.setStatus(Integer.parseInt(params.get("status").toString()));
        activityMapper.updateById(a);
    }

    @Override
    public void deleteActivity(Long id) {
        requireActivity(id);
        activityMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> toggleStatus(Long id, Map<String, Object> params) {
        MarketActivity a = requireActivity(id);
        Integer target = params.get("status") == null ? null :
                Integer.parseInt(params.get("status").toString());
        if (target == null) {
            target = (a.getStatus() == null || a.getStatus() == 0) ? 1 : 0;
        }
        a.setStatus(target);
        activityMapper.updateById(a);
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("status", target);
        r.put("statusText", statusText(target));
        return r;
    }

    @Override
    public Map<String, Object> copyActivity(Long id, Map<String, Object> params) {
        MarketActivity src = requireActivity(id);
        MarketActivity copy = new MarketActivity();
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null) copy.setTenantId(tid);
        copy.setStoreId(src.getStoreId());
        // 新活动名：原名 + "副本-yyyyMMddHHmmss"（避免重复）
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        copy.setName((src.getName() == null ? "活动" : src.getName()) + "-副本-" + suffix);
        copy.setType(src.getType());
        copy.setRuleConfig(src.getRuleConfig());
        // 时间窗口：默认平移 +1 月，可被入参覆盖
        LocalDateTime now = LocalDateTime.now();
        copy.setStartTime(params != null && params.get("startTime") != null
                ? LocalDateTime.parse(params.get("startTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : now.plusDays(1));
        copy.setEndTime(params != null && params.get("endTime") != null
                ? LocalDateTime.parse(params.get("endTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : now.plusDays(31));
        copy.setStatus(0); // 副本默认禁用，需手动启用
        activityMapper.insert(copy);
        Map<String, Object> r = new HashMap<>();
        r.put("id", copy.getId());
        r.put("name", copy.getName());
        r.put("sourceId", id);
        return r;
    }

    @Override
    public IPage<Map<String, Object>> participants(Long id, int pageNum, int pageSize) {
        requireActivity(id);
        // 简化实现：从 pos_order 拉时间窗口内的订单作为参与者占位
        MarketActivity a = activityMapper.selectById(id);
        Page<PosOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PosOrder> q = new LambdaQueryWrapper<>();
        if (a != null && a.getStartTime() != null) q.ge(PosOrder::getCreateTime, a.getStartTime());
        if (a != null && a.getEndTime() != null) q.le(PosOrder::getCreateTime, a.getEndTime());
        q.orderByDesc(PosOrder::getCreateTime);
        IPage<PosOrder> result = orderMapper.selectPage(page, q);

        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("amount", o.getFinalAmount());
            m.put("createTime", o.getCreateTime());
            return m;
        }).toList());
        return mapPage;
    }

    private String typeText(Integer type) {
        return switch (type) {
            case 1 -> "满减";
            case 2 -> "折扣";
            case 3 -> "赠礼";
            case 4 -> "双倍积分";
            default -> "未知";
        };
    }

    private String statusText(Integer status) {
        return switch (status) {
            case 0 -> "禁用";
            case 1 -> "启用";
            case 2 -> "已结束";
            default -> "未知";
        };
    }

    /**
     * 租户隔离的活动读取
     */
    private MarketActivity requireActivity(Long id) {
        MarketActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(BizCode.NOT_FOUND, "活动不存在");
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null && a.getTenantId() != null && !tid.equals(a.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权访问该活动（跨租户）");
        }
        return a;
    }
}
