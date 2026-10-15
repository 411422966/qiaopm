package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.entity.SysTenantConfig;
import com.meiyetec.mapper.SysTenantConfigMapper;
import com.meiyetec.service.SysTenantConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysTenantConfigServiceImpl implements SysTenantConfigService {

    private final SysTenantConfigMapper configMapper;

    @Override
    public Map<String, String> getConfig(Long tenantId) {
        List<SysTenantConfig> rows = configMapper.listByTenantId(tenantId);
        Map<String, String> map = new HashMap<>();
        for (SysTenantConfig row : rows) {
            map.put(row.getCfgKey(), row.getCfgValue());
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(Long tenantId, Map<String, String> configs) {
        for (Map.Entry<String, String> e : configs.entrySet()) {
            QueryWrapper<SysTenantConfig> q = new QueryWrapper<>();
            q.eq("tenant_id", tenantId).eq("cfg_key", e.getKey());
            SysTenantConfig existing = configMapper.selectOne(q);
            if (existing != null) {
                existing.setCfgValue(e.getValue());
                configMapper.updateById(existing);
            } else {
                SysTenantConfig c = new SysTenantConfig();
                c.setTenantId(tenantId);
                c.setCfgKey(e.getKey());
                c.setCfgValue(e.getValue());
                configMapper.insert(c);
            }
        }
    }
}
