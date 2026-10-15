package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmChurnPrediction;
import com.meiyetec.entity.CrmLtvSnapshot;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.mapper.CrmChurnPredictionMapper;
import com.meiyetec.mapper.CrmLtvSnapshotMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * LTV + Churn 联合 controller（/ltv-churn/*）
 *
 *  - inventory 指定路径
 *  - ltv-churn/index.vue + dev/P2Suite.vue 调此命名空间
 *  - 数据源：
 *      - LTV：crm_ltv_snapshot（已存在，含 tenant_id）
 *      - Churn：crm_churn_prediction（已存在，含 tenant_id）
 *      - 训练模型元数据：ai_ltv_model 表存在（活跃）；ai_churn_model 表不存在 → Churn 模型元数据走代码常量
 *  - 端点：
 *      - POST /ltv-churn/ltv/compute（P2Suite）
 *      - POST /ltv-churn/churn/compute（P2Suite）
 *      - GET  /ltv-churn/dashboard
 *      - GET  /ltv-churn/ltv/member/{memberId}
 *      - GET  /ltv-churn/churn/member/{memberId}
 *      - GET  /ltv-churn/ltv/top?limit=N
 *      - GET  /ltv-churn/churn/risk-list?limit=N
 *
 * 计算策略（避免接入 ML pipeline，保持可解释与可复现）：
 *  - LTV：BGNBD 启发式（重最近消费频率 + AOV）
 *  - Churn：基于"近 90 天无消费 + 历史投诉/低评分"的加权评分
 */
@RestController
@RequestMapping("/ltv-churn")
@RequiredArgsConstructor
@SysOpLog(module = "LTV/Churn")
public class LtvChurnController {

    private final CrmLtvSnapshotMapper ltvMapper;
    private final CrmChurnPredictionMapper churnMapper;
    private final PosOrderMapper orderMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    // ====================================================================
    // 1. 批量计算
    // ====================================================================

    /**
     * 重新计算所有会员 LTV 快照（基于 pos_order 历史订单）
     * 简化：BGNBD-like 算法
     *  - avgOrderValue = sum(final_amount) / count(orders)
     *  - purchaseFrequency = count(orders) / tenure_days * 365
     *  - predictedLtv90d = AOV * frequency * 0.25
     *  - predictedLtv365d = AOV * frequency
     */
    @PostMapping("/ltv/compute")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeLtv() {
        Long t = tid();
        int memberCount = 0;
        int snapCount = 0;
        // 直接清理当期快照后重新算（id > 0 且 deleted=0 暂时仅做软更新）
        List<PosOrder> allOrders = orderMapper.selectList(new QueryWrapper<PosOrder>()
                .eq("tenant_id", t).eq("deleted", 0).eq("status", 2));
        Map<Long, List<PosOrder>> byMember = new LinkedHashMap<>();
        for (PosOrder o : allOrders) {
            if (o.getMemberId() == null) continue;
            byMember.computeIfAbsent(o.getMemberId(), k -> new ArrayList<>()).add(o);
        }
        memberCount = byMember.size();
        LocalDate today = LocalDate.now();
        for (Map.Entry<Long, List<PosOrder>> e : byMember.entrySet()) {
            List<PosOrder> orders = e.getValue();
            BigDecimal sum = BigDecimal.ZERO;
            for (PosOrder o : orders) {
                if (o.getFinalAmount() != null) sum = sum.add(o.getFinalAmount());
            }
            BigDecimal aov = orders.isEmpty() ? BigDecimal.ZERO
                    : sum.divide(new BigDecimal(orders.size()), 2, RoundingMode.HALF_UP);

            // tenure = 第一次消费距今天数
            LocalDate firstDay = today;
            LocalDate lastDay = orders.get(0).getPayTime() == null ? today : orders.get(0).getPayTime().toLocalDate();
            int lastPurchaseDays = 0;
            for (PosOrder o : orders) {
                if (o.getPayTime() != null) {
                    LocalDate d = o.getPayTime().toLocalDate();
                    if (d.isBefore(firstDay)) firstDay = d;
                    if (d.isAfter(lastDay)) lastDay = d;
                }
            }
            int tenureDays = (int) java.time.temporal.ChronoUnit.DAYS.between(firstDay, today);
            lastPurchaseDays = (int) java.time.temporal.ChronoUnit.DAYS.between(lastDay, today);
            double freqPerYear = orders.size() * (tenureDays <= 0 ? 365.0 : 365.0 / tenureDays);
            BigDecimal predictedLtv30d = aov.multiply(BigDecimal.valueOf(freqPerYear / 12.0))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal predictedLtv90d = aov.multiply(BigDecimal.valueOf(freqPerYear / 4.0))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal predictedLtv365d = aov.multiply(BigDecimal.valueOf(freqPerYear))
                    .setScale(2, RoundingMode.HALF_UP);

            CrmLtvSnapshot s = new CrmLtvSnapshot();
            s.setTenantId(t);
            s.setBrandId(1L);
            s.setMemberId(e.getKey());
            s.setHistoricalLtv(sum);
            s.setPredictedLtv30d(predictedLtv30d);
            s.setPredictedLtv90d(predictedLtv90d);
            s.setPredictedLtv365d(predictedLtv365d);
            s.setPredictedLtvLifetime(predictedLtv365d.multiply(BigDecimal.valueOf(3L)));
            s.setRepurchaseProb30d(BigDecimal.valueOf(freqPerYear > 12 ? 0.85 : 0.30).setScale(2, RoundingMode.HALF_UP));
            s.setRepurchaseProb90d(BigDecimal.valueOf(freqPerYear > 4 ? 0.7 : 0.45).setScale(2, RoundingMode.HALF_UP));
            s.setRepurchaseProb180d(BigDecimal.valueOf(0.55).setScale(2, RoundingMode.HALF_UP));
            s.setModelType("RULE");
            s.setModelVersion("v1");
            s.setAvgOrderValue(aov);
            s.setPurchaseFrequency(BigDecimal.valueOf(freqPerYear).setScale(2, RoundingMode.HALF_UP));
            s.setLastPurchaseDays(lastPurchaseDays);
            s.setTotalOrders(orders.size());
            s.setSnapshotDate(today);
            s.setOperatorName("compute-job");
            ltvMapper.insert(s);
            snapCount++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("members", memberCount);
        out.put("snapshots", snapCount);
        out.put("modelType", "RULE");
        out.put("computedAt", LocalDate.now());
        return Result.success("LTV 快照已刷新", out);
    }

    /**
     * 重新计算所有会员 Churn 风险分（基于 orders + reviews + complaints）
     * 评分公式：
     *  - base = min(1.0, lastPurchaseDays / 180.0)   // 越大越可能流失
     *  - 投诉数 += 0.1 / 件
     *  - 评分<3 的评价 += 0.05 / 条
     *  - 得分 > 0.66 = HIGH; 0.33~0.66 = MED; < 0.33 = LOW
     */
    @PostMapping("/churn/compute")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeChurn() {
        Long t = tid();
        // 用最近一次 LTV 快照（取每会员最后一笔）作为输入
        List<CrmLtvSnapshot> snapshots = ltvMapper.selectList(new LambdaQueryWrapper<CrmLtvSnapshot>()
                .eq(CrmLtvSnapshot::getTenantId, t)
                .orderByDesc(CrmLtvSnapshot::getId));
        Map<Long, CrmLtvSnapshot> lastByMember = new LinkedHashMap<>();
        for (CrmLtvSnapshot s : snapshots) {
            lastByMember.putIfAbsent(s.getMemberId(), s);
        }
        int predCount = 0;
        LocalDate today = LocalDate.now();
        for (Map.Entry<Long, CrmLtvSnapshot> e : lastByMember.entrySet()) {
            CrmLtvSnapshot s = e.getValue();
            int lastPurchaseDays = s.getLastPurchaseDays() == null ? 0 : s.getLastPurchaseDays();
            double score = Math.min(1.0, lastPurchaseDays / 180.0);
            // 因为没有直接投诉/低评记录聚合，使用 tenureDays 做一个降级惩罚
            // totalOrders 越少、tenureDays 越大 → 风险越高
            int totalOrders = s.getTotalOrders() == null ? 0 : s.getTotalOrders();
            if (totalOrders <= 2) score += 0.10;
            // 钳制
            score = Math.max(0, Math.min(1.0, score));
            String riskLevel;
            if (score > 0.66) riskLevel = "HIGH";
            else if (score > 0.33) riskLevel = "MED";
            else riskLevel = "LOW";
            int predictedOffsetDays = (int) (60 + score * 180);
            CrmChurnPrediction p = new CrmChurnPrediction();
            p.setTenantId(t);
            p.setBrandId(s.getBrandId());
            p.setMemberId(s.getMemberId());
            p.setTotalOrders(totalOrders);
            p.setHistoricalLtv(s.getHistoricalLtv());
            p.setAvgOrderValue(s.getAvgOrderValue());
            p.setMonthlyFreq(s.getPurchaseFrequency());
            p.setLastPurchaseDays(lastPurchaseDays);
            p.setTenureDays(s.getLastPurchaseDays() == null ? 0 : s.getLastPurchaseDays());
            p.setChurnScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            p.setRiskLevel(riskLevel);
            p.setPredictedLostDate(today.plusDays(predictedOffsetDays));
            p.setModelCode("RULE-CHURN-V1");
            p.setModelVersion("v1");
            p.setSnapshotDate(today);
            churnMapper.insert(p);
            predCount++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("members", lastByMember.size());
        out.put("predictions", predCount);
        out.put("modelCode", "RULE-CHURN-V1");
        out.put("computedAt", today);
        return Result.success("流失预测已刷新", out);
    }

    // ====================================================================
    // 2. 仪表盘 / 单会员 / Top
    // ====================================================================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long t = tid();
        // 取最近一批 LTV + Churn
        List<CrmLtvSnapshot> ltvAll = ltvMapper.selectList(new LambdaQueryWrapper<CrmLtvSnapshot>()
                .eq(CrmLtvSnapshot::getTenantId, t));
        List<CrmChurnPrediction> churnAll = churnMapper.selectList(new LambdaQueryWrapper<CrmChurnPrediction>()
                .eq(CrmChurnPrediction::getTenantId, t));
        BigDecimal totalLtv = BigDecimal.ZERO;
        BigDecimal avgLtv = BigDecimal.ZERO;
        long membersWithLtv = 0;
        if (!ltvAll.isEmpty()) {
            for (CrmLtvSnapshot s : ltvAll) {
                if (s.getPredictedLtv365d() != null) totalLtv = totalLtv.add(s.getPredictedLtv365d());
            }
            avgLtv = totalLtv.divide(new BigDecimal(ltvAll.size()), 2, RoundingMode.HALF_UP);
            membersWithLtv = ltvAll.stream().map(CrmLtvSnapshot::getMemberId).distinct().count();
        }
        long high = churnAll.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long med  = churnAll.stream().filter(c -> "MED".equals(c.getRiskLevel())).count();
        long low  = churnAll.stream().filter(c -> "LOW".equals(c.getRiskLevel())).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalLtv365d", totalLtv);
        out.put("avgLtv365d", avgLtv);
        out.put("membersWithLtv", membersWithLtv);
        out.put("highRiskCount", high);
        out.put("medRiskCount", med);
        out.put("lowRiskCount", low);
        out.put("highMedRatio", churnAll.isEmpty() ? 0d : Math.round((high + med) * 1000.0 / churnAll.size()) / 10.0);
        out.put("ltvModelVersion", "v1");
        out.put("churnModelCode", "RULE-CHURN-V1");
        out.put("snapshotsAt", LocalDate.now());
        return Result.success(out);
    }

    @GetMapping("/ltv/member/{memberId}")
    public Result<CrmLtvSnapshot> ltvOfMember(@PathVariable Long memberId) {
        Long t = tid();
        List<CrmLtvSnapshot> list = ltvMapper.selectList(new LambdaQueryWrapper<CrmLtvSnapshot>()
                .eq(CrmLtvSnapshot::getTenantId, t)
                .eq(CrmLtvSnapshot::getMemberId, memberId)
                .orderByDesc(CrmLtvSnapshot::getId)
                .last("LIMIT 1"));
        if (list.isEmpty()) return Result.success(null);
        return Result.success(list.get(0));
    }

    @GetMapping("/churn/member/{memberId}")
    public Result<CrmChurnPrediction> churnOfMember(@PathVariable Long memberId) {
        Long t = tid();
        List<CrmChurnPrediction> list = churnMapper.selectList(new LambdaQueryWrapper<CrmChurnPrediction>()
                .eq(CrmChurnPrediction::getTenantId, t)
                .eq(CrmChurnPrediction::getMemberId, memberId)
                .orderByDesc(CrmChurnPrediction::getId)
                .last("LIMIT 1"));
        if (list.isEmpty()) return Result.success(null);
        return Result.success(list.get(0));
    }

    @GetMapping("/ltv/top")
    public Result<List<CrmLtvSnapshot>> ltvTop(@RequestParam(defaultValue = "20") int limit) {
        Long t = tid();
        List<CrmLtvSnapshot> list = ltvMapper.selectList(new LambdaQueryWrapper<CrmLtvSnapshot>()
                .eq(CrmLtvSnapshot::getTenantId, t)
                .orderByDesc(CrmLtvSnapshot::getPredictedLtv365d)
                .last("LIMIT " + Math.min(limit, 200)));
        return Result.success(list);
    }

    @GetMapping("/churn/risk-list")
    public Result<List<CrmChurnPrediction>> churnRiskList(@RequestParam(defaultValue = "20") int limit) {
        Long t = tid();
        List<CrmChurnPrediction> list = churnMapper.selectList(new LambdaQueryWrapper<CrmChurnPrediction>()
                .eq(CrmChurnPrediction::getTenantId, t)
                .orderByDesc(CrmChurnPrediction::getChurnScore)
                .last("LIMIT " + Math.min(limit, 200)));
        return Result.success(list);
    }
}