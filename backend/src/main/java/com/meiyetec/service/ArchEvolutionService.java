package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.ArchDecision;
import com.meiyetec.entity.ArchDependency;
import com.meiyetec.entity.ArchRoadmap;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.ArchDecisionMapper;
import com.meiyetec.mapper.ArchDependencyMapper;
import com.meiyetec.mapper.ArchRoadmapMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * R3.0-H P2.8：架构演进（ADR + Roadmap）
 *
 * <p>提供：
 * <ul>
 *   <li>架构决策记录（ADR）：背景 / 决策 / 后果 / 状态机</li>
 *   <li>架构演进路线图（Milestone）</li>
 *   <li>服务依赖图管理</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/arch")
@RequiredArgsConstructor
public class ArchEvolutionService {

    private final ArchDecisionMapper decisionMapper;
    private final ArchRoadmapMapper roadmapMapper;
    private final ArchDependencyMapper dependencyMapper;

    // ====================================================================
    // 1. ADR 架构决策记录
    // ====================================================================

    @PostMapping("/adr")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDecision> createAdr(@RequestBody ArchDecision d) {
        if (d.getAdrNo() == null) return Result.error(BizCode.BAD_REQUEST, "adrNo 必填");
        ArchDecision exist = decisionMapper.selectOne(
                new LambdaQueryWrapper<ArchDecision>().eq(ArchDecision::getAdrNo, d.getAdrNo()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "ADR 编号已存在");
        if (d.getStatus() == null) d.setStatus("PROPOSED");
        if (d.getDecisionDate() == null) d.setDecisionDate(LocalDate.now());
        decisionMapper.insert(d);
        return Result.success("已创建", d);
    }

    @PostMapping("/adr/{no}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDecision> acceptAdr(@PathVariable String no) {
        ArchDecision d = decisionMapper.selectOne(
                new LambdaQueryWrapper<ArchDecision>().eq(ArchDecision::getAdrNo, no));
        if (d == null) return Result.error(BizCode.NOT_FOUND, "ADR 不存在");
        d.setStatus("ACCEPTED");
        decisionMapper.updateById(d);
        return Result.success("已接受", d);
    }

    @PostMapping("/adr/{no}/deprecate")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDecision> deprecateAdr(@PathVariable String no,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        ArchDecision d = decisionMapper.selectOne(
                new LambdaQueryWrapper<ArchDecision>().eq(ArchDecision::getAdrNo, no));
        if (d == null) return Result.error(BizCode.NOT_FOUND, "ADR 不存在");
        d.setStatus("DEPRECATED");
        if (body != null && body.get("supersededBy") != null) {
            d.setSupersededBy((String) body.get("supersededBy"));
        }
        decisionMapper.updateById(d);
        return Result.success("已废弃", d);
    }

    @GetMapping("/adr/list")
    public Result<List<ArchDecision>> listAdrs(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String tag) {
        LambdaQueryWrapper<ArchDecision> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(ArchDecision::getStatus, status);
        if (tag != null) qw.like(ArchDecision::getTags, tag);
        qw.orderByDesc(ArchDecision::getId);
        return Result.success(decisionMapper.selectList(qw));
    }

    @GetMapping("/adr/{no}")
    public Result<ArchDecision> getAdr(@PathVariable String no) {
        return Result.success(decisionMapper.selectOne(
                new LambdaQueryWrapper<ArchDecision>().eq(ArchDecision::getAdrNo, no)));
    }

    // ====================================================================
    // 2. 架构演进路线图
    // ====================================================================

    @PostMapping("/roadmap")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchRoadmap> upsertMilestone(@RequestBody ArchRoadmap m) {
        if (m.getMilestoneCode() == null) return Result.error(BizCode.BAD_REQUEST, "milestoneCode 必填");
        ArchRoadmap exist = roadmapMapper.selectOne(
                new LambdaQueryWrapper<ArchRoadmap>().eq(ArchRoadmap::getMilestoneCode, m.getMilestoneCode()));
        if (exist != null) {
            exist.setTitle(m.getTitle());
            exist.setPhase(m.getPhase());
            exist.setDescription(m.getDescription());
            exist.setDeliverables(m.getDeliverables());
            exist.setTargetDate(m.getTargetDate());
            exist.setStatus(m.getStatus());
            exist.setOwner(m.getOwner());
            exist.setProgress(m.getProgress());
            roadmapMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (m.getStatus() == null) m.setStatus("PLANNED");
        if (m.getProgress() == null) m.setProgress(0);
        roadmapMapper.insert(m);
        return Result.success("已创建", m);
    }

    @PostMapping("/roadmap/{code}/progress")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchRoadmap> updateProgress(@PathVariable String code,
                                                   @RequestBody Map<String, Object> body) {
        ArchRoadmap m = roadmapMapper.selectOne(
                new LambdaQueryWrapper<ArchRoadmap>().eq(ArchRoadmap::getMilestoneCode, code));
        if (m == null) return Result.error(BizCode.NOT_FOUND, "里程碑不存在");
        if (body.get("progress") != null) m.setProgress(((Number) body.get("progress")).intValue());
        if (body.get("status") != null) m.setStatus((String) body.get("status"));
        if (m.getProgress() != null && m.getProgress() >= 100) {
            m.setStatus("DONE");
            m.setActualDate(LocalDate.now());
        }
        roadmapMapper.updateById(m);
        return Result.success("已更新", m);
    }

    @GetMapping("/roadmap/list")
    public Result<List<ArchRoadmap>> listMilestones(@RequestParam(required = false) String phase,
                                                       @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ArchRoadmap> qw = new LambdaQueryWrapper<>();
        if (phase != null) qw.eq(ArchRoadmap::getPhase, phase);
        if (status != null) qw.eq(ArchRoadmap::getStatus, status);
        qw.orderByAsc(ArchRoadmap::getPhase);
        return Result.success(roadmapMapper.selectList(qw));
    }

    // ====================================================================
    // 3. 服务依赖图
    // ====================================================================

    @PostMapping("/dependency")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDependency> addDependency(@RequestBody ArchDependency dep) {
        if (dep.getDependencyType() == null) dep.setDependencyType("SYNC_CALL");
        if (dep.getProtocol() == null) dep.setProtocol("HTTP");
        if (dep.getCriticality() == null) dep.setCriticality("NORMAL");
        dependencyMapper.insert(dep);
        return Result.success("已添加", dep);
    }

    @GetMapping("/dependency/graph")
    public Result<Map<String, Object>> dependencyGraph(@RequestParam(required = false) String serviceCode) {
        List<ArchDependency> all = dependencyMapper.selectList(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", extractNodes(all));
        data.put("edges", all);
        if (serviceCode != null) {
            List<ArchDependency> incoming = all.stream()
                    .filter(d -> serviceCode.equals(d.getToService())).toList();
            List<ArchDependency> outgoing = all.stream()
                    .filter(d -> serviceCode.equals(d.getFromService())).toList();
            data.put("incoming", incoming);
            data.put("outgoing", outgoing);
        }
        return Result.success(data);
    }

    private static Set<String> extractNodes(List<ArchDependency> deps) {
        Set<String> nodes = new LinkedHashSet<>();
        for (ArchDependency d : deps) {
            nodes.add(d.getFromService());
            nodes.add(d.getToService());
        }
        return nodes;
    }

    @GetMapping("/dependency/blockers")
    public Result<List<ArchDependency>> blockerDependencies() {
        return Result.success(dependencyMapper.selectList(
                new LambdaQueryWrapper<ArchDependency>()
                        .eq(ArchDependency::getCriticality, "BLOCKER")));
    }

    // ====================================================================
    // 4. 现状摘要
    // ====================================================================

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAdr", decisionMapper.selectCount(null));
        data.put("acceptedAdr", decisionMapper.selectCount(
                new LambdaQueryWrapper<ArchDecision>().eq(ArchDecision::getStatus, "ACCEPTED")));
        data.put("totalMilestone", roadmapMapper.selectCount(null));
        data.put("doneMilestone", roadmapMapper.selectCount(
                new LambdaQueryWrapper<ArchRoadmap>().eq(ArchRoadmap::getStatus, "DONE")));
        data.put("inProgressMilestone", roadmapMapper.selectCount(
                new LambdaQueryWrapper<ArchRoadmap>().eq(ArchRoadmap::getStatus, "IN_PROGRESS")));
        data.put("totalDependency", dependencyMapper.selectCount(null));
        data.put("blockerDependency", dependencyMapper.selectCount(
                new LambdaQueryWrapper<ArchDependency>().eq(ArchDependency::getCriticality, "BLOCKER")));
        return Result.success(data);
    }
}