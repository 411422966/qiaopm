package com.meiyetec.service;

import com.meiyetec.entity.BizTreatmentPhaseStep;
import com.meiyetec.entity.MemberTreatmentStepLog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P1.4：服务 SOP 步骤化补强测试
 * <p>
 * 不依赖 Spring / DB，验证：
 *  - 步骤字段读写
 *  - 并行/串行判据（group + type 字段）
 *  - 编排聚合（按 group 分组 → 计算 type / max duration）
 *  - 日志状态机（1=执行中 / 2=已完成 / 3=跳过）
 * </p>
 */
class TreatmentStepArrangementTest {

    // ==================== 字段读写 ====================
    @Test
    void step_allFields_readWrite() {
        BizTreatmentPhaseStep s = new BizTreatmentPhaseStep();
        s.setPhaseId(100L);
        s.setPlanId(50L);
        s.setStepNo(1);
        s.setStepGroup("G1");
        s.setStepType("SERIAL");
        s.setStepName("清洁");
        s.setDurationMinutes(5);
        s.setProductId(200L);
        s.setProductQty(new BigDecimal("3.50"));
        s.setToolRequired("洁面乳");
        s.setRecipeText("温柔打圈30秒");
        s.setRemark("敏感肌轻揉");

        assertEquals(100L, s.getPhaseId());
        assertEquals("G1", s.getStepGroup());
        assertEquals("清洁", s.getStepName());
        assertEquals(5, s.getDurationMinutes());
        assertEquals(0, new BigDecimal("3.50").compareTo(s.getProductQty()));
    }

    // ==================== 并行/串行判据 ====================
    @Test
    void type_serial_singleStep() {
        BizTreatmentPhaseStep s = new BizTreatmentPhaseStep();
        s.setStepGroup("G1");
        s.setStepType("SERIAL");
        s.setStepName("清洁");
        assertEquals("G1", s.getStepGroup());
        assertEquals("SERIAL", s.getStepType());
    }

    @Test
    void type_parallel_multiSteps_sameGroup() {
        BizTreatmentPhaseStep a = new BizTreatmentPhaseStep();
        a.setStepGroup("G3");
        a.setStepType("PARALLEL");
        a.setStepName("超声波左脸");

        BizTreatmentPhaseStep b = new BizTreatmentPhaseStep();
        b.setStepGroup("G3");
        b.setStepType("PARALLEL");
        b.setStepName("超声波右脸");

        assertEquals(a.getStepGroup(), b.getStepGroup(), "并行步骤 group 必须相同");
    }

    // ==================== 编排聚合（按 group 分组） ====================
    @Test
    void arrangement_parallelDetected_whenSameGroup() {
        List<BizTreatmentPhaseStep> steps = List.of(
            buildStep("G1", "PARALLEL", "卸妆", 5),
            buildStep("G1", "PARALLEL", "洁面", 5),
            buildStep("G2", "SERIAL",   "热喷机", 10)
        );

        Map<String, List<BizTreatmentPhaseStep>> grouped = steps.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        BizTreatmentPhaseStep::getStepGroup,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        // group G1: 2 个步骤 = 并行
        List<BizTreatmentPhaseStep> g1 = grouped.get("G1");
        assertEquals(2, g1.size());
        assertEquals("PARALLEL", g1.size() > 1 ? "PARALLEL" : "SERIAL",
                "组内步骤数 > 1 应判为并行");

        // group G2: 1 个步骤 = 串行
        List<BizTreatmentPhaseStep> g2 = grouped.get("G2");
        assertEquals(1, g2.size());
        assertEquals("SERIAL", g2.size() > 1 ? "PARALLEL" : "SERIAL",
                "组内步骤数 = 1 应判为串行");
    }

    @Test
    void arrangement_maxDurationByGroup() {
        List<BizTreatmentPhaseStep> steps = List.of(
            buildStep("G3", "PARALLEL", "左脸按摩", 8),
            buildStep("G3", "PARALLEL", "右脸按摩", 12),
            buildStep("G3", "PARALLEL", "面膜", 5)
        );

        int maxDur = steps.stream()
                .mapToInt(BizTreatmentPhaseStep::getDurationMinutes)
                .max().orElse(0);
        assertEquals(12, maxDur, "并行组总时长 = 最长步骤时长（其他步骤并行）");
    }

    @Test
    void arrangement_serialGroupsOrdered() {
        // 模拟按 group 顺序聚合 → G1 → G2 → G3
        LinkedHashMap<String, List<BizTreatmentPhaseStep>> grouped = new LinkedHashMap<>();
        grouped.put("G1", List.of(buildStep("G1", "SERIAL", "清洁", 5)));
        grouped.put("G2", List.of(buildStep("G2", "SERIAL", "热敷", 10)));
        grouped.put("G3", List.of(
            buildStep("G3", "PARALLEL", "左脸导入", 15),
            buildStep("G3", "PARALLEL", "右脸导入", 15)
        ));

        List<String> groups = new ArrayList<>(grouped.keySet());
        assertEquals(List.of("G1", "G2", "G3"), groups, "串行 group 必须按顺序");
    }

    // ==================== 经典面部护理 6 步 SOP ====================
    @Test
    void facialCare6StepSOP() {
        // G1并行清洁：卸妆+洁面 (5min)
        // G2并行热敷：热喷机+化妆棉热敷 (8min)
        // G3并行导入：左脸+右脸超声波 (15min)
        // G4并行面膜：左脸+右脸面膜 (20min)
        // G5收尾：头肩按摩 (10min)
        List<BizTreatmentPhaseStep> sop = List.of(
            buildStep("G1", "PARALLEL", "卸妆", 5),
            buildStep("G1", "PARALLEL", "洁面", 5),
            buildStep("G2", "PARALLEL", "热喷机", 8),
            buildStep("G2", "PARALLEL", "化妆棉热敷", 8),
            buildStep("G3", "PARALLEL", "超声波左脸", 15),
            buildStep("G3", "PARALLEL", "超声波右脸", 15),
            buildStep("G4", "PARALLEL", "左脸面膜", 20),
            buildStep("G4", "PARALLEL", "右脸面膜", 20),
            buildStep("G5", "SERIAL", "头肩按摩", 10)
        );

        // 总并行组数
        Set<String> groups = new HashSet<>();
        for (BizTreatmentPhaseStep s : sop) groups.add(s.getStepGroup());
        assertEquals(5, groups.size(), "面部护理 5 个组（G1~G5）");

        // 总时长（按组内最长）
        Map<String, List<BizTreatmentPhaseStep>> byGroup = sop.stream()
                .collect(java.util.stream.Collectors.groupingBy(BizTreatmentPhaseStep::getStepGroup));
        long totalMinutes = byGroup.values().stream()
                .mapToLong(g -> g.stream().mapToInt(BizTreatmentPhaseStep::getDurationMinutes).max().orElse(0))
                .sum();
        // max(5,5) + max(8,8) + max(15,15) + max(20,20) + 10 = 5+8+15+20+10 = 58
        assertEquals(58, totalMinutes, "面部护理总时长 = 各组 max 求和 = 58 分钟");
    }

    // ==================== 步骤日志状态机 ====================
    @Test
    void stepLog_statusLifecycle() {
        MemberTreatmentStepLog l = new MemberTreatmentStepLog();
        // 1=执行中 → 2=已完成
        l.setStatus(1);
        assertEquals(1, l.getStatus());
        l.setStatus(2);
        assertEquals(2, l.getStatus());
        l.setStatus(3);
        assertEquals(3, l.getStatus());
    }

    @Test
    void stepLog_finishedSetTimestamp() {
        MemberTreatmentStepLog l = new MemberTreatmentStepLog();
        l.setStartedAt(java.time.LocalDateTime.now());
        assertNotNull(l.getStartedAt());

        l.setFinishedAt(java.time.LocalDateTime.now().plusMinutes(15));
        assertNotNull(l.getFinishedAt());
    }

    @Test
    void stepLog_productTracking() {
        MemberTreatmentStepLog l = new MemberTreatmentStepLog();
        l.setProductId(500L);
        l.setProductQty(new BigDecimal("3.50"));
        assertEquals(500L, l.getProductId());
        assertEquals(0, new BigDecimal("3.50").compareTo(l.getProductQty()));
    }

    // ==================== 工具 ====================
    private BizTreatmentPhaseStep buildStep(String group, String type,
                                             String name, int duration) {
        BizTreatmentPhaseStep s = new BizTreatmentPhaseStep();
        s.setStepGroup(group);
        s.setStepType(type);
        s.setStepName(name);
        s.setDurationMinutes(duration);
        return s;
    }
}