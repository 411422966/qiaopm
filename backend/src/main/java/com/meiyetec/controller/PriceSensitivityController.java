package com.meiyetec.controller;

import com.meiyetec.service.PriceSensitivityService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M15-03 价格敏感度 REST API
 *
 * <p>端点：
 * <ul>
 *   <li>GET /intel/price-sensitivity/overview         总览：弹性 + 分群 + 洞察</li>
 *   <li>GET /intel/price-sensitivity/curve            单项目价格-转化曲线</li>
 *   <li>GET /intel/price-sensitivity/segments         会员敏感度分群</li>
 *   <li>GET /intel/price-sensitivity/matrix           项目 × 价格档位矩阵</li>
 * </ul>
 *
 * <p>依赖 {@link PriceSensitivityService}，复用 {@code pricing_experiment_result}
 * + {@code pos_order_item} 数据源，无新建表。
 */
@Slf4j
@RestController
@RequestMapping("/intel/price-sensitivity")
@RequiredArgsConstructor
public class PriceSensitivityController {

    private final PriceSensitivityService service;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return service.overview();
    }

    @GetMapping("/curve")
    public Result<Map<String, Object>> curve(@RequestParam Long projectId) {
        return service.curve(projectId);
    }

    @GetMapping("/segments")
    public Result<Map<String, Object>> segments() {
        return service.segments();
    }

    @GetMapping("/matrix")
    public Result<Map<String, Object>> matrix(@RequestParam(required = false) Integer topN) {
        return service.matrix(topN);
    }
}
