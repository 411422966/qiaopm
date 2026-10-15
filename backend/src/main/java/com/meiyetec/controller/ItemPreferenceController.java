package com.meiyetec.controller;

import com.meiyetec.entity.CrmMemberItemPreference;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ItemPreferenceService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M15 项目偏好 HTTP 层
 *
 * <p>路由：/mkt/preference/*
 *
 * <p>核心端点：
 * <ul>
 *   <li>GET  /top — 会员 Top N 偏好</li>
 *   <li>GET  /by-type — 按类型查偏好</li>
 *   <li>POST /compute — 触发偏好计算</li>
 *   <li>GET  /recommend — 推荐服务 ID 列表</li>
 *   <li>GET  /summary — 偏好摘要（dashboard）</li>
 * </ul>
 *
 * <p>计算触发点（推荐在 POS 支付成功后异步调用）：
 * {@code POST /mkt/preference/compute?memberId=123}
 */
@Slf4j
@RestController
@RequestMapping("/mkt/preference")
@RequiredArgsConstructor
public class ItemPreferenceController {

    private final ItemPreferenceService itemPreferenceService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    /**
     * 会员 Top N 偏好（按 preferenceScore 降序）
     */
    @GetMapping("/top")
    public Result<List<CrmMemberItemPreference>> topPreferences(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(itemPreferenceService.getTopPreferences(memberId, tid(), limit));
    }

    /**
     * 按类型查偏好
     */
    @GetMapping("/by-type")
    public Result<List<CrmMemberItemPreference>> byType(
            @RequestParam Long memberId,
            @RequestParam String itemType) {
        return Result.success(itemPreferenceService.getPreferencesByType(memberId, tid(), itemType));
    }

    /**
     * 触发偏好计算（可在 POS 支付成功后调用）
     */
    @PostMapping("/compute")
    public Result<Map<String, Object>> compute(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "365") int windowDays) {
        int saved = itemPreferenceService.computeForMember(memberId, windowDays);
        return Result.success("已更新偏好", Map.of("computed", saved, "memberId", memberId));
    }

    /**
     * 推荐：基于偏好的服务 ID 列表（用于智能推荐/首页展示）
     */
    @GetMapping("/recommend")
    public Result<List<Long>> recommend(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "6") int topN) {
        List<Long> ids = itemPreferenceService.recommendServiceIds(memberId, tid(), topN);
        return Result.success(ids);
    }

    /**
     * 偏好摘要（会员画像 dashboard）
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam Long memberId) {
        return Result.success(itemPreferenceService.summary(memberId, tid()));
    }
}
