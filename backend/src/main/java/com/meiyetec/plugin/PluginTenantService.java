package com.meiyetec.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PluginInstall;
import com.meiyetec.mapper.PluginInstallMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EXT-23：插件租户级启停服务
 *
 * <p>职责：在 pf4j 的"平台级 enable"之上叠加"租户级 enable"开关。
 * <ul>
 *   <li>platform-level enabled（pf4j state == STARTED）：插件类加载完成，{@code onStart} 已调用</li>
 *   <li>tenant-level enabled（{@code plugin_install.enabled = 1}）：当前租户可调用该插件的扩展点</li>
 * </ul>
 *
 * <p>调用方（业务代码 / 其它插件）应通过 {@link #isEnabledForTenant(String, Long)} 判断后再调用插件扩展点。
 *
 * <p>平台默认（无 plugin_install 记录）：{@code 视为启用}（保持与历史兼容 — pf4j 已 enable 的插件对所有租户可见）。
 * 如果租户被显式记录 enabled=0，则**即使 pf4j STARTED 也拒绝**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginTenantService {

    private final PluginInstallMapper installMapper;

    /**
     * 判断某插件对某租户是否启用。
     * <p>返回 true 的前提：pf4j state == STARTED 且（plugin_install.enabled ≠ 0）。
     *
     * @param pluginCode 业务编码
     * @param tenantId   租户 ID（0 或 null = 平台级调用）
     * @param platformEnabled pf4j 平台级状态（来自 {@code PluginRuntimeInfo.getState()}）
     */
    public boolean isEnabledForTenant(String pluginCode, Long tenantId, String platformEnabled) {
        if (pluginCode == null) return false;
        if (!"STARTED".equals(platformEnabled)) return false;
        try {
            PluginInstall install = installMapper.selectOne(new LambdaQueryWrapper<PluginInstall>()
                    .eq(PluginInstall::getPluginCode, pluginCode)
                    .eq(PluginInstall::getTenantId, tenantId == null ? 0L : tenantId)
                    .last("LIMIT 1"));
            if (install == null) {
                // 兜底：平台级记录
                if (tenantId != null && tenantId != 0L) {
                    return isEnabledForTenant(pluginCode, 0L, platformEnabled);
                }
                // 完全没有 install 记录 → 默认启用
                return true;
            }
            return install.getEnabled() != null && install.getEnabled() == 1;
        } catch (Exception e) {
            log.warn("[PLUGIN-TENANT] isEnabledForTenant query failed: plugin={} tenant={} err={}",
                    pluginCode, tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * 设置某租户对某插件的启停状态。
     */
    public boolean setTenantEnabled(String pluginCode, Long tenantId, boolean enabled) {
        if (pluginCode == null || tenantId == null) return false;
        try {
            PluginInstall existing = installMapper.selectOne(new LambdaQueryWrapper<PluginInstall>()
                    .eq(PluginInstall::getPluginCode, pluginCode)
                    .eq(PluginInstall::getTenantId, tenantId)
                    .last("LIMIT 1"));
            if (existing == null) {
                PluginInstall ins = new PluginInstall();
                ins.setTenantId(tenantId);
                ins.setPluginCode(pluginCode);
                ins.setVersion("latest");
                ins.setEnabled(enabled ? 1 : 0);
                ins.setInstallStatus(enabled ? "ACTIVE" : "DISABLED");
                ins.setUseCount(0L);
                installMapper.insert(ins);
                log.info("[PLUGIN-TENANT] 新增插件租户记录：plugin={} tenant={} enabled={}",
                        pluginCode, tenantId, enabled);
                return true;
            }
            existing.setEnabled(enabled ? 1 : 0);
            existing.setInstallStatus(enabled ? "ACTIVE" : "DISABLED");
            installMapper.updateById(existing);
            log.info("[PLUGIN-TENANT] 更新插件租户启停：plugin={} tenant={} enabled={}",
                    pluginCode, tenantId, enabled);
            return true;
        } catch (Exception e) {
            log.warn("[PLUGIN-TENANT] setTenantEnabled failed: plugin={} tenant={} err={}",
                    pluginCode, tenantId, e.getMessage());
            return false;
        }
    }

    /** 列出某租户所有已记录的插件 install 状态 */
    public List<PluginInstall> listByTenant(Long tenantId) {
        return installMapper.selectList(new LambdaQueryWrapper<PluginInstall>()
                .eq(PluginInstall::getTenantId, tenantId == null ? 0L : tenantId)
                .orderByDesc(PluginInstall::getId));
    }
}
