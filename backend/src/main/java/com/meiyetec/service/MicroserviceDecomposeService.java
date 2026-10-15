package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MicroserviceBoundary;
import com.meiyetec.entity.ServiceContract;
import com.meiyetec.entity.ServiceRouteRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MicroserviceBoundaryMapper;
import com.meiyetec.mapper.ServiceContractMapper;
import com.meiyetec.mapper.ServiceRouteRuleMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * R3.0-H P2.8：微服务拆分（边界 + 网关路由 + 契约）
 *
 * <p>提供：
 * <ul>
 *   <li>微服务边界定义（service_code / context / port / deploy）</li>
 *   <li>API 网关路由规则（pattern / target / 重试 / 超时 / 限流）</li>
 *   <li>服务契约（method / path / schema / 限流 / 超时）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/micro")
@RequiredArgsConstructor
public class MicroserviceDecomposeService {

    private final MicroserviceBoundaryMapper boundaryMapper;
    private final ServiceRouteRuleMapper routeMapper;
    private final ServiceContractMapper contractMapper;

    // ====================================================================
    // 1. 微服务边界
    // ====================================================================

    @PostMapping("/boundary")
    @Transactional(rollbackFor = Exception.class)
    public Result<MicroserviceBoundary> upsertBoundary(@RequestBody MicroserviceBoundary b) {
        if (b.getServiceCode() == null) return Result.error(BizCode.BAD_REQUEST, "serviceCode 必填");
        MicroserviceBoundary exist = boundaryMapper.selectOne(
                new LambdaQueryWrapper<MicroserviceBoundary>().eq(MicroserviceBoundary::getServiceCode, b.getServiceCode()));
        if (exist != null) {
            exist.setServiceName(b.getServiceName());
            exist.setContextCode(b.getContextCode());
            exist.setPort(b.getPort());
            exist.setBasePath(b.getBasePath());
            exist.setApiVersion(b.getApiVersion());
            exist.setResponsibility(b.getResponsibility());
            exist.setDependencies(b.getDependencies());
            exist.setDeployUnit(b.getDeployUnit());
            exist.setStatus(b.getStatus());
            boundaryMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (b.getPort() == null) b.setPort(8080);
        if (b.getBasePath() == null) b.setBasePath("/");
        if (b.getApiVersion() == null) b.setApiVersion("v1");
        if (b.getDeployUnit() == null) b.setDeployUnit("DOCKER");
        if (b.getStatus() == null) b.setStatus("PLANNED");
        boundaryMapper.insert(b);
        return Result.success("已创建", b);
    }

    @GetMapping("/boundary/list")
    public Result<List<MicroserviceBoundary>> listBoundaries(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<MicroserviceBoundary> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(MicroserviceBoundary::getStatus, status);
        qw.orderByAsc(MicroserviceBoundary::getServiceCode);
        return Result.success(boundaryMapper.selectList(qw));
    }

    @GetMapping("/boundary/topology")
    public Result<Map<String, Object>> topology() {
        List<MicroserviceBoundary> all = boundaryMapper.selectList(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("services", all);
        data.put("totalServices", all.size());
        data.put("plannedCount", all.stream().filter(b -> "PLANNED".equals(b.getStatus())).count());
        data.put("runningCount", all.stream().filter(b -> "RUNNING".equals(b.getStatus())).count());
        return Result.success(data);
    }

    // ====================================================================
    // 2. API 网关路由
    // ====================================================================

    @PostMapping("/route")
    @Transactional(rollbackFor = Exception.class)
    public Result<ServiceRouteRule> upsertRoute(@RequestBody ServiceRouteRule r) {
        if (r.getRoutePattern() == null) return Result.error(BizCode.BAD_REQUEST, "routePattern 必填");
        ServiceRouteRule exist = routeMapper.selectOne(
                new LambdaQueryWrapper<ServiceRouteRule>().eq(ServiceRouteRule::getRoutePattern, r.getRoutePattern()));
        if (exist != null) {
            exist.setTargetService(r.getTargetService());
            exist.setTargetPath(r.getTargetPath());
            exist.setStripPrefix(r.getStripPrefix());
            exist.setRetryCount(r.getRetryCount());
            exist.setTimeoutMs(r.getTimeoutMs());
            exist.setRateLimitQps(r.getRateLimitQps());
            exist.setAuthRequired(r.getAuthRequired());
            exist.setPriority(r.getPriority());
            exist.setEnabled(r.getEnabled());
            routeMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (r.getRetryCount() == null) r.setRetryCount(0);
        if (r.getTimeoutMs() == null) r.setTimeoutMs(5000);
        if (r.getRateLimitQps() == null) r.setRateLimitQps(1000);
        if (r.getAuthRequired() == null) r.setAuthRequired(1);
        if (r.getPriority() == null) r.setPriority(100);
        if (r.getEnabled() == null) r.setEnabled(1);
        routeMapper.insert(r);
        return Result.success("已创建", r);
    }

    @GetMapping("/route/list")
    public Result<List<ServiceRouteRule>> listRoutes() {
        return Result.success(routeMapper.selectList(
                new LambdaQueryWrapper<ServiceRouteRule>()
                        .eq(ServiceRouteRule::getEnabled, 1)
                        .orderByAsc(ServiceRouteRule::getPriority)));
    }

    /**
     * 匹配路由：给定 path，返回命中的路由规则
     */
    @PostMapping("/route/match")
    public Result<ServiceRouteRule> matchRoute(@RequestBody Map<String, Object> body) {
        String path = (String) body.get("path");
        if (path == null) return Result.error(BizCode.BAD_REQUEST, "path 必填");
        List<ServiceRouteRule> routes = routeMapper.selectList(
                new LambdaQueryWrapper<ServiceRouteRule>()
                        .eq(ServiceRouteRule::getEnabled, 1)
                        .orderByAsc(ServiceRouteRule::getPriority));
        for (ServiceRouteRule r : routes) {
            if (match(r.getRoutePattern(), path)) {
                return Result.success("命中", r);
            }
        }
        return Result.error(BizCode.NOT_FOUND, "无匹配路由");
    }

    private static boolean match(String pattern, String path) {
        // 简化匹配：* 替换为任意
        String regex = pattern.replace("*", ".*");
        return path.matches(regex);
    }

    // ====================================================================
    // 3. 服务契约
    // ====================================================================

    @PostMapping("/contract")
    @Transactional(rollbackFor = Exception.class)
    public Result<ServiceContract> upsertContract(@RequestBody ServiceContract c) {
        if (c.getServiceCode() == null || c.getMethodName() == null)
            return Result.error(BizCode.BAD_REQUEST, "serviceCode / methodName 必填");
        if (c.getHttpMethod() == null) c.setHttpMethod("GET");
        if (c.getPath() == null) return Result.error(BizCode.BAD_REQUEST, "path 必填");
        if (c.getAuthRequired() == null) c.setAuthRequired(1);
        if (c.getRateLimit() == null) c.setRateLimit(1000);
        if (c.getTimeoutMs() == null) c.setTimeoutMs(3000);
        if (c.getVersion() == null) c.setVersion("v1");
        if (c.getEnabled() == null) c.setEnabled(1);

        ServiceContract exist = contractMapper.selectOne(
                new LambdaQueryWrapper<ServiceContract>()
                        .eq(ServiceContract::getServiceCode, c.getServiceCode())
                        .eq(ServiceContract::getMethodName, c.getMethodName())
                        .eq(ServiceContract::getVersion, c.getVersion()));
        if (exist != null) {
            contractMapper.updateById(c);
            return Result.success("已更新", c);
        }
        contractMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/contract/list")
    public Result<List<ServiceContract>> listContracts(@RequestParam(required = false) String serviceCode) {
        LambdaQueryWrapper<ServiceContract> qw = new LambdaQueryWrapper<>();
        if (serviceCode != null) qw.eq(ServiceContract::getServiceCode, serviceCode);
        qw.eq(ServiceContract::getEnabled, 1);
        qw.orderByAsc(ServiceContract::getServiceCode);
        return Result.success(contractMapper.selectList(qw));
    }

    @GetMapping("/contract/{service}/{method}")
    public Result<ServiceContract> getContract(@PathVariable String service,
                                                  @PathVariable String method,
                                                  @RequestParam(required = false) String version) {
        if (version == null) version = "v1";
        return Result.success(contractMapper.selectOne(
                new LambdaQueryWrapper<ServiceContract>()
                        .eq(ServiceContract::getServiceCode, service)
                        .eq(ServiceContract::getMethodName, method)
                        .eq(ServiceContract::getVersion, version)));
    }
}