package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.dto.TenantStatsDTO;
import com.meiyetec.entity.SysTenant;

public interface SysTenantService extends IService<SysTenant> {

    /**
     * 检查租户是否可用（启用 + 未过期）。不可用时抛 RuntimeException。
     */
    void checkAvailable(Long tenantId);

    /**
     * 冻结过期租户：停用租户 + 禁用该租户下所有用户。
     * 返回冻结的用户数量。
     */
    int freezeExpiredTenant(Long tenantId);

    /**
     * 租户统计：包含用户总数/启用/禁用等指标。
     */
    TenantStatsDTO getStats(Long tenantId);
}