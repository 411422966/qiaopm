package com.meiyetec.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PluginRuntimeState;
import com.meiyetec.mapper.PluginRuntimeStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EXT-23：插件运行时状态持久化服务
 *
 * <p>职责：把 pf4j 每次状态变更同步到 {@code plugin_runtime_state} 表。
 * <p>写入策略：upsert by (tenant_id, plugin_code)；不存在的 state 字段保留旧值（不覆盖）。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>不阻塞主流程：失败仅 warn，不抛异常（持久化失败不影响 pf4j 状态机）</li>
 *   <li>启动期调用 {@link #restoreStates()} 把 DB 里"上一次成功的状态"读出来，dashboard 显示历史</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginPersistenceService {

    private final PluginRuntimeStateMapper stateMapper;

    /**
     * 记录一次状态变更。
     *
     * @param tenantId   租户 ID（0 = 平台级）
     * @param pluginCode 业务编码
     * @param version    版本
     * @param pluginId   pf4j pluginId
     * @param state      状态（CREATED / STARTED / STOPPED / FAILED / DISABLED）
     * @param errorMsg   错误信息（可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordState(Long tenantId, String pluginCode, String version,
                            String pluginId, String state, String errorMsg) {
        if (pluginCode == null || pluginCode.isEmpty()) return;
        try {
            PluginRuntimeState existing = stateMapper.selectOne(new LambdaQueryWrapper<PluginRuntimeState>()
                    .eq(PluginRuntimeState::getTenantId, tenantId == null ? 0L : tenantId)
                    .eq(PluginRuntimeState::getPluginCode, pluginCode)
                    .last("LIMIT 1"));
            PluginRuntimeState row = existing == null ? new PluginRuntimeState() : existing;
            row.setTenantId(tenantId == null ? 0L : tenantId);
            row.setPluginCode(pluginCode);
            if (version != null) row.setVersion(version);
            if (pluginId != null) row.setPluginId(pluginId);
            if (state != null)   row.setState(state);
            // lastError 始终刷新（即使是 null 也要清空，让"成功"覆盖"失败"）
            row.setLastError(errorMsg);
            // loadedAt 只在 STARTED / CREATED 时刷新
            if ("STARTED".equals(state) || "CREATED".equals(state)) {
                row.setLoadedAt(LocalDateTime.now());
            }
            if (existing == null) {
                row.setId(null);
                stateMapper.insert(row);
            } else {
                stateMapper.updateById(row);
            }
        } catch (Exception e) {
            log.warn("[PLUGIN-PERSIST] recordState failed: tenant={} plugin={} state={} err={}",
                    tenantId, pluginCode, state, e.getMessage());
        }
    }

    /**
     * 取某租户某插件的 lastError（无记录 → null）。
     * <p>用于：disablePlugin 路径保留旧错误（I022）— 防止把 FAILED 错误被空值清掉。
     */
    public String getLastError(Long tenantId, String pluginCode) {
        if (pluginCode == null || pluginCode.isEmpty()) return null;
        try {
            PluginRuntimeState existing = stateMapper.selectOne(new LambdaQueryWrapper<PluginRuntimeState>()
                    .eq(PluginRuntimeState::getTenantId, tenantId == null ? 0L : tenantId)
                    .eq(PluginRuntimeState::getPluginCode, pluginCode)
                    .last("LIMIT 1"));
            return existing == null ? null : existing.getLastError();
        } catch (Exception e) {
            log.warn("[PLUGIN-PERSIST] getLastError failed: tenant={} plugin={} err={}",
                    tenantId, pluginCode, e.getMessage());
            return null;
        }
    }

    /** 启动期读取所有"历史状态"，供 dashboard / 监控使用 */
    public List<PluginRuntimeState> restoreStates() {
        try {
            return stateMapper.selectList(new LambdaQueryWrapper<PluginRuntimeState>()
                    .orderByDesc(PluginRuntimeState::getUpdatedAt));
        } catch (Exception e) {
            log.warn("[PLUGIN-PERSIST] restoreStates failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 取某租户所有持久化记录 */
    public List<PluginRuntimeState> listByTenant(Long tenantId) {
        try {
            return stateMapper.selectList(new LambdaQueryWrapper<PluginRuntimeState>()
                    .eq(PluginRuntimeState::getTenantId, tenantId == null ? 0L : tenantId)
                    .orderByDesc(PluginRuntimeState::getUpdatedAt));
        } catch (Exception e) {
            log.warn("[PLUGIN-PERSIST] listByTenant failed: tenant={} err={}", tenantId, e.getMessage());
            return List.of();
        }
    }
}
